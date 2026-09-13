package com.lingua.app.data.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.lingua.app.data.ocr.paddle.CtcDecoder
import com.lingua.app.data.ocr.paddle.DetPostProcessor
import com.lingua.app.data.ocr.paddle.DetPreProcessor
import com.lingua.app.data.ocr.paddle.OcrGeometry
import com.lingua.app.data.ocr.paddle.RecProcessor
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * On-device OCR: PP-OCRv6 text detection (DBNet) followed by PP-OCRv6 text recognition.
 *
 * Deliberately free of Android types so the same class runs on a device and in JVM unit tests
 * against the desktop ONNX Runtime artifact.
 */
class PaddleOcrEngine(
  private val detModelPath: String,
  private val recModelPath: String,
  private val dictLines: List<String>,
  threadCount: Int = Runtime.getRuntime().availableProcessors(),
  private val detLimitSide: Int = DEFAULT_DET_LIMIT_SIDE,
  private val recBatchSize: Int = DEFAULT_REC_BATCH_SIZE,
  private val detPostProcessor: DetPostProcessor = DetPostProcessor(),
) : Closeable {

  /** Little cores drag a batch out; upstream measurements put the knee at four threads. */
  private val threads = threadCount.coerceIn(1, 4)

  private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()

  private val options: OrtSession.SessionOptions =
    OrtSession.SessionOptions().apply {
      setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
      setIntraOpNumThreads(threads)
      setInterOpNumThreads(1)
    }

  private val detectionLazy = lazy { environment.createSession(detModelPath, options) }

  private val recognitionLazy = lazy { environment.createSession(recModelPath, options) }

  private val detection: OrtSession get() = detectionLazy.value

  private val recognition: OrtSession get() = recognitionLazy.value

  private val characters: List<String?> by lazy {
    val info = recognition.outputInfo.values.first().info as TensorInfo
    OcrDictionary.characters(dictLines, info.shape.last().toInt())
  }

  /**
   * Detects and recognizes every text line in [image], in reading order.
   *
   * The recognizer only reads horizontal lines, so when the upright pass is not convincing the
   * screen is tried again quarter-turned and the best-scoring orientation wins. Boxes are mapped
   * back to the caller's coordinates either way, and the fast path costs nothing extra: an upright
   * screen that reads cleanly is never re-run.
   */
  fun recognize(image: RgbImage): OcrResult {
    val upright = runTurn(image, QuarterTurn.None)
    if (upright.score >= CONFIDENT_MEAN_CONFIDENCE) return upright.result

    var best = upright
    for (turn in QuarterTurn.retries) {
      val candidate = runTurn(image, turn)
      if (candidate.score > best.score + ROTATION_SWITCH_MARGIN) best = candidate
    }
    if (best === upright) return upright.result
    return merge(upright.result, best.result)
  }

  /**
   * Keeps the better reading of each box when the screen holds both orientations at once.
   *
   * Manga dialogue is vertical while its captions and sound effects are horizontal; turning the
   * whole image rescues one at the cost of the other, so boxes that land on top of each other are
   * settled by confidence.
   */
  private fun merge(upright: OcrResult, turned: OcrResult): OcrResult {
    val candidates =
      (turned.lines + upright.lines).filter { it.text.isNotBlank() }.sortedByDescending { it.confidence }
    val kept = ArrayList<OcrLine>(candidates.size)
    for (line in candidates) {
      if (kept.any { overlaps(it.quad, line.quad) }) continue
      kept.add(line)
    }
    return OcrResult(
      lines = sortLinesReadingOrder(kept),
      imageWidth = upright.imageWidth,
      imageHeight = upright.imageHeight,
      rotationDegrees = turned.rotationDegrees,
    )
  }

  /** True when the boxes cover mostly the same pixels. */
  private fun overlaps(a: Quad, b: Quad): Boolean {
    val overlapX = minOf(a.maxX, b.maxX) - maxOf(a.minX, b.minX)
    val overlapY = minOf(a.maxY, b.maxY) - maxOf(a.minY, b.minY)
    if (overlapX <= 0f || overlapY <= 0f) return false
    val smaller = minOf(a.width * a.height, b.width * b.height)
    return smaller > 0f && (overlapX * overlapY) / smaller > OVERLAP_RATIO
  }

  private class Scored(val result: OcrResult, val score: Float)

  /** One detection + recognition pass over `image.rotated(turn)`, reported in [image]'s frame. */
  private fun runTurn(image: RgbImage, turn: QuarterTurn): Scored {
    val source = image.rotated(turn)
    val quads = detect(source)
    if (quads.isEmpty()) {
      return Scored(OcrResult(emptyList(), image.width, image.height, turn.degrees), 0f)
    }

    val lines =
      recognizeQuads(source, quads).map { line ->
        val mapped =
          if (turn == QuarterTurn.None) line.quad
          else OcrGeometry.orderQuad(line.quad.unrotated(turn, image.width, image.height))
        line.copy(quad = mapped)
      }
    val ordered = sortLinesReadingOrder(lines)
    return Scored(
      OcrResult(ordered, image.width, image.height, turn.degrees),
      meanConfidence(ordered),
    )
  }

  /** How readable the pass was; junk from a wrong orientation scores well under 0.6. */
  private fun meanConfidence(lines: List<OcrLine>): Float {
    val meaningful = lines.filter { it.text.isNotBlank() }
    if (meaningful.isEmpty()) return 0f
    return meaningful.map { it.confidence }.average().toFloat()
  }

  private fun detect(image: RgbImage): List<Quad> {
    val prepared = DetPreProcessor.prepare(image, detLimitSide)
    val probability = runDetection(prepared)
    return detPostProcessor
      .extract(
        prob = probability.data,
        width = probability.width,
        height = probability.height,
        toSourceX = prepared.toSourceX,
        toSourceY = prepared.toSourceY,
      )
      .let(::sortReadingOrder)
  }

  private fun recognizeQuads(image: RgbImage, quads: List<Quad>): List<OcrLine> {
    if (quads.isEmpty()) return emptyList()

    val lines = arrayOfNulls<OcrLine>(quads.size)
    for (chunk in planChunks(quads)) {
      val chunkQuads = chunk.map { quads[it] }
      val decoded = recognizeChunk(image, chunkQuads)
      chunk.forEachIndexed { position, quadIndex -> lines[quadIndex] = decoded[position] }
    }
    return lines.filterNotNull()
  }

  /**
   * Splits the detected boxes into recognition runs.
   *
   * Boxes are grouped by similar width first, because a batch is padded to its widest member and a
   * single long line would otherwise inflate every crop beside it. Each run is then capped so the
   * output tensor stays near [RECOGNITION_BUDGET_FLOATS] floats: recognition output is
   * `batch x (width / 8) x 18710` floats, which for eight full-width lines is 90MB.
   */
  private fun planChunks(quads: List<Quad>): List<List<Int>> {
    val classes = characters.size
    val widths = IntArray(quads.size) { RecProcessor.plannedWidth(quads[it]) }
    val runs = ArrayList<MutableList<Int>>()
    var run = mutableListOf<Int>()
    var runWidth = 0
    for (index in quads.indices.sortedBy { widths[it] }) {
      val width = widths[index]
      val full = run.size == recBatchSize
      val bucketBoundary = run.isNotEmpty() && width > runWidth * WIDTH_BUCKET_FACTOR
      if (full || bucketBoundary) {
        runs.add(run)
        run = mutableListOf()
        runWidth = 0
      }
      run.add(index)
      runWidth = maxOf(runWidth, width)
    }
    if (run.isNotEmpty()) runs.add(run)

    return runs.flatMap { bucket ->
      val batchWidth = RecProcessor.batchWidth(IntArray(bucket.size) { widths[bucket[it]] })
      val timeSteps = (batchWidth + TIME_STEP_STRIDE - 1) / TIME_STEP_STRIDE
      val perItem = timeSteps * classes
      val limit = (RECOGNITION_BUDGET_FLOATS / perItem).coerceIn(1, recBatchSize)
      bucket.chunked(limit)
    }
  }

  override fun close() {
    if (detectionLazy.isInitialized()) runCatching { detectionLazy.value.close() }
    if (recognitionLazy.isInitialized()) runCatching { recognitionLazy.value.close() }
    runCatching { options.close() }
  }

  private class Probability(val data: FloatArray, val width: Int, val height: Int)

  private fun runDetection(prepared: DetPreProcessor.Prepared): Probability {
    val shape = longArrayOf(1, 3, prepared.height.toLong(), prepared.width.toLong())
    OnnxTensor.createTensor(environment, directBuffer(prepared.tensor), shape).use { input ->
      detection.run(mapOf(detection.inputNames.first() to input)).use { result ->
        val tensor = result.iterator().next().value as OnnxTensor
        val outputShape = tensor.info.shape
        val width = outputShape[outputShape.size - 1].toInt()
        val height = outputShape[outputShape.size - 2].toInt()
        val buffer = tensor.floatBuffer
        val data = FloatArray(buffer.remaining())
        buffer.get(data)
        applySigmoidIfNeeded(data)
        return Probability(data, width, height)
      }
    }
  }

  private fun recognizeChunk(image: RgbImage, quads: List<Quad>): List<OcrLine> {
    val (widths, batchWidth) = RecProcessor.plan(quads)
    val tensor = RecProcessor.buildBatch(image, quads, widths, batchWidth)
    val shape =
      longArrayOf(quads.size.toLong(), 3, RecProcessor.REC_HEIGHT.toLong(), batchWidth.toLong())
    OnnxTensor.createTensor(environment, directBuffer(tensor), shape).use { input ->
      recognition.run(mapOf(recognition.inputNames.first() to input)).use { result ->
        val output = result.iterator().next().value as OnnxTensor
        val outputShape = output.info.shape
        val batch = outputShape[0].toInt()
        val timeSteps = outputShape[1].toInt()
        val classes = outputShape[2].toInt()
        val decoded =
          CtcDecoder.decode(output.floatBuffer, batch, timeSteps, classes, characters)
        return quads.indices.map { index ->
          OcrLine(
            text = decoded[index].text,
            quad = quads[index],
            confidence = decoded[index].confidence,
          )
        }
      }
    }
  }

  private fun sortReadingOrder(quads: List<Quad>): List<Quad> =
    if (quads.size < 2) quads else quads.sortedWith(readingOrder(quads.map { it.edgeHeight }))

  private fun sortLinesReadingOrder(lines: List<OcrLine>): List<OcrLine> {
    if (lines.size < 2) return lines
    val order = readingOrder(lines.map { it.quad.edgeHeight })
    return lines.sortedWith(Comparator { a, b -> order.compare(a.quad, b.quad) })
  }

  /**
   * Groups boxes into rows so left-to-right ordering does not jump between columns of different
   * heights; the band is a fraction of the median line height.
   */
  private fun readingOrder(heights: List<Float>): Comparator<Quad> {
    if (heights.isEmpty()) return compareBy({ it.centerY }, { it.centerX })
    val sorted = heights.sorted()
    val band = (sorted[sorted.size / 2].coerceAtLeast(1f) * 0.6f).coerceAtLeast(1f)
    return compareBy({ (it.centerY / band).toInt() }, { it.centerX })
  }

  /** DBNet exports usually include the sigmoid, but a raw-logit export must still work. */
  private fun applySigmoidIfNeeded(data: FloatArray) {
    var min = Float.MAX_VALUE
    var max = -Float.MAX_VALUE
    for (value in data) {
      if (value < min) min = value
      if (value > max) max = value
    }
    if (min >= -0.001f && max <= 1.001f) return
    for (index in data.indices) {
      data[index] = 1f / (1f + kotlin.math.exp(-data[index]))
    }
  }

  private fun directBuffer(values: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .put(values)
      .apply { rewind() }

  companion object {
    /** Longest side fed to detection; phone screenshots are usually 1080-1440 wide. */
    const val DEFAULT_DET_LIMIT_SIDE = 960

    const val DEFAULT_REC_BATCH_SIZE = 8

    /**
     * An upright pass at or above this mean confidence is trusted as-is. Clean screenshots score
     * ~0.99 while a wrong orientation scores 0.15-0.6, so the gap is wide.
     */
    private const val CONFIDENT_MEAN_CONFIDENCE = 0.9f

    /** A turned pass has to be clearly better before its boxes are reported. */
    private const val ROTATION_SWITCH_MARGIN = 0.05f

    /** Fraction of the smaller box two readings must share to be treated as the same text. */
    private const val OVERLAP_RATIO = 0.5f

    /**
     * Recognition output is `batch x (width / 8) x 18710` floats. Six million floats is ~24MB, which
     * keeps a full screen of text comfortably inside a phone heap.
     */
    private const val RECOGNITION_BUDGET_FLOATS = 6_000_000

    /** Crop width to time-step ratio of the SVTR recognition head. */
    private const val TIME_STEP_STRIDE = 8

    /** Widths within this factor share a batch. */
    private const val WIDTH_BUCKET_FACTOR = 1.4f
  }
}
