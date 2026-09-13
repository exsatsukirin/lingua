package com.lingua.app.data.ocr.paddle

import com.lingua.app.data.ocr.Quad
import com.lingua.app.data.ocr.RgbImage
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Recognition input: every detected box is un-warped to [REC_HEIGHT] pixels tall, keeping its
 * aspect ratio, then normalised with `(x / 127.5) - 1`.
 *
 * Boxes are batched because the per-crop work is dominated by ONNX Runtime call overhead; the batch
 * is padded to the widest crop, and the padding stays at zero in *normalised* space (which is what
 * PaddleOCR does — padding the bitmap with black first would feed the model `-1` instead).
 */
internal object RecProcessor {

  const val REC_HEIGHT = 48

  /** Widest crop a single line may be resized to. */
  const val MAX_WIDTH = 1200

  private const val MIN_WIDTH = 16

  /** PaddleOCR always pads a batch to at least the 320/48 ratio it was trained with. */
  private const val MIN_BATCH_WIDTH = 320

  private val LUT = FloatArray(256) { (it / 127.5f) - 1f }

  /** Width one box occupies once resized to [REC_HEIGHT], ignoring the batch padding. */
  fun plannedWidth(quad: Quad): Int =
    ceil(REC_HEIGHT * ratio(quad)).toInt().coerceIn(MIN_WIDTH, MAX_WIDTH)

  /** Width a batch of boxes is padded to: the widest member, never below the trained minimum. */
  fun batchWidth(widths: IntArray): Int =
    maxOf(MIN_BATCH_WIDTH, widths.maxOrNull() ?: 0).coerceAtMost(MAX_WIDTH)

  /** Widths the given boxes occupy once resized to [REC_HEIGHT], plus the batch width to pad to. */
  fun plan(quads: List<Quad>): Pair<IntArray, Int> {
    val widths = IntArray(quads.size) { plannedWidth(quads[it]) }
    val batch = batchWidth(widths)
    return widths to batch
  }

  private fun ratio(quad: Quad): Float {
    val height = max(quad.edgeHeight, 1f)
    return min(quad.edgeWidth / height, MAX_WIDTH.toFloat() / REC_HEIGHT)
  }

  /** Builds one NCHW batch tensor for [quads] (crop widths already decided by [plan]). */
  fun buildBatch(image: RgbImage, quads: List<Quad>, widths: IntArray, batchWidth: Int): FloatArray {
    val count = quads.size
    val plane = REC_HEIGHT * batchWidth
    val tensor = FloatArray(count * 3 * plane)

    for (n in 0 until count) {
      val quad = quads[n]
      val width = widths[n]
      val homography = OcrGeometry.homography(
        fromX = floatArrayOf(0f, width.toFloat(), width.toFloat(), 0f),
        fromY = floatArrayOf(0f, 0f, REC_HEIGHT.toFloat(), REC_HEIGHT.toFloat()),
        toX = quad.xs,
        toY = quad.ys,
      )
      val base = n * 3 * plane
      for (y in 0 until REC_HEIGHT) {
        for (x in 0 until width) {
          val u = x + 0.5f
          val v = y + 0.5f
          val denominator = homography[6] * u + homography[7] * v + homography[8]
          if (denominator == 0f) continue
          val sourceX = (homography[0] * u + homography[1] * v + homography[2]) / denominator
          val sourceY = (homography[3] * u + homography[4] * v + homography[5]) / denominator
          val pixel = OcrGeometry.sampleBilinear(image, sourceX, sourceY)
          val index = y * batchWidth + x
          tensor[base + index] = LUT[pixel shr 16 and 0xFF]
          tensor[base + plane + index] = LUT[pixel shr 8 and 0xFF]
          tensor[base + 2 * plane + index] = LUT[pixel and 0xFF]
        }
      }
    }
    return tensor
  }
}
