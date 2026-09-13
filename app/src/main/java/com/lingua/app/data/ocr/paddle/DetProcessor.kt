package com.lingua.app.data.ocr.paddle

import com.lingua.app.data.ocr.Quad
import com.lingua.app.data.ocr.RgbImage
import kotlin.math.roundToInt

/**
 * Detection input: DBNet wants the longest side capped and both sides a multiple of 32, normalised
 * with the ImageNet mean/std in RGB order.
 */
internal object DetPreProcessor {

  private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
  private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

  private val LUT = Array(3) { channel ->
    FloatArray(256) { value -> ((value / 255f) - MEAN[channel]) / STD[channel] }
  }

  class Prepared(
    val tensor: FloatArray,
    val width: Int,
    val height: Int,
    /** Multiply a probability-map coordinate by this to get the source-image coordinate. */
    val toSourceX: Float,
    val toSourceY: Float,
  )

  fun prepare(image: RgbImage, limitSide: Int): Prepared {
    val ratio = minOf(limitSide.toFloat() / maxOf(image.width, image.height), 1f)
    val width = ((image.width * ratio / 32f).roundToInt().coerceAtLeast(1)) * 32
    val height = ((image.height * ratio / 32f).roundToInt().coerceAtLeast(1)) * 32

    val plane = width * height
    val tensor = FloatArray(3 * plane)
    val scaleX = image.width.toFloat() / width
    val scaleY = image.height.toFloat() / height

    for (y in 0 until height) {
      val sourceY = (y + 0.5f) * scaleY - 0.5f
      val row = y * width
      for (x in 0 until width) {
        val sourceX = (x + 0.5f) * scaleX - 0.5f
        val pixel = OcrGeometry.sampleBilinear(image, sourceX, sourceY)
        val index = row + x
        tensor[index] = LUT[0][pixel shr 16 and 0xFF]
        tensor[plane + index] = LUT[1][pixel shr 8 and 0xFF]
        tensor[2 * plane + index] = LUT[2][pixel and 0xFF]
      }
    }
    return Prepared(tensor, width, height, scaleX, scaleY)
  }
}

/**
 * Detection output: turn the DBNet probability map into text boxes.
 *
 * Thresholds come from the PP-OCRv6 inference configuration; `thresh` decides which pixels are
 * text, `boxThreshold` filters whole regions by their mean score, and `unclipRatio` re-expands the
 * boxes that shrinking to a binary map costs.
 */
class DetPostProcessor(
  private val threshold: Float = 0.2f,
  private val boxThreshold: Float = 0.45f,
  private val unclipRatio: Float = 1.4f,
  private val minSize: Float = 3f,
  private val maxCandidates: Int = 1000,
) {

  private val neighbourX = intArrayOf(1, -1, 0, 0)
  private val neighbourY = intArrayOf(0, 0, 1, -1)

  fun extract(
    prob: FloatArray,
    width: Int,
    height: Int,
    toSourceX: Float,
    toSourceY: Float,
  ): List<Quad> {
    val visited = BooleanArray(width * height)
    val stack = IntArray(width * height)
    val found = ArrayList<Quad>()
    val border = ArrayList<Float>(256)

    for (start in 0 until width * height) {
      if (visited[start] || prob[start] <= threshold) continue
      if (found.size >= maxCandidates) break

      var top = 0
      stack[top++] = start
      visited[start] = true
      border.clear()
      var pixels = 0

      while (top > 0) {
        val index = stack[--top]
        val x = index % width
        val y = index / width
        pixels++
        var isBorder = false
        for (direction in 0 until 4) {
          val nx = x + neighbourX[direction]
          val ny = y + neighbourY[direction]
          if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
            isBorder = true
            continue
          }
          val neighbour = ny * width + nx
          if (prob[neighbour] <= threshold) {
            isBorder = true
          } else if (!visited[neighbour]) {
            visited[neighbour] = true
            stack[top++] = neighbour
          }
        }
        if (isBorder) {
          border.add(x.toFloat())
          border.add(y.toFloat())
        }
      }

      if (pixels < 4 || border.size < 6) continue

      val borderX = FloatArray(border.size / 2) { border[it * 2] }
      val borderY = FloatArray(border.size / 2) { border[it * 2 + 1] }
      val rect = OcrGeometry.minAreaRect(borderX, borderY) ?: continue
      if (OcrGeometry.shortSide(rect) < minSize) continue
      if (OcrGeometry.boxScore(prob, width, height, rect) < boxThreshold) continue

      val unclipped = OcrGeometry.unclip(rect, unclipRatio)
      val expanded = OcrGeometry.minAreaRect(unclipped.xs, unclipped.ys) ?: continue
      if (OcrGeometry.shortSide(expanded) < minSize + 2f) continue

      found.add(OcrGeometry.orderQuad(expanded.scaled(toSourceX, toSourceY)))
    }
    return found
  }
}
