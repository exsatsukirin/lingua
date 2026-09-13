package com.lingua.app.data.ocr

/**
 * An ARGB_8888 image as a plain int array.
 *
 * The whole OCR core is written against this instead of `android.graphics.Bitmap` so that the
 * detection/recognition pipeline can be exercised by ordinary JVM unit tests (and by the desktop
 * ONNX Runtime artifact) without an emulator.
 */
class RgbImage(val argb: IntArray, val width: Int, val height: Int) {
  init {
    require(width > 0 && height > 0) { "image must be non-empty" }
    require(argb.size == width * height) { "pixel count ${argb.size} != ${width}x$height" }
  }

  val size: Int get() = argb.size

  fun redAt(index: Int): Int = argb[index] shr 16 and 0xFF

  fun greenAt(index: Int): Int = argb[index] shr 8 and 0xFF

  fun blueAt(index: Int): Int = argb[index] and 0xFF

  fun redAt(x: Int, y: Int): Int = redAt(y * width + x)

  fun greenAt(x: Int, y: Int): Int = greenAt(y * width + x)

  fun blueAt(x: Int, y: Int): Int = blueAt(y * width + x)
}

/** A text box: four corners in source-image pixel coordinates, kept in cyclic order. */
class Quad(xs: FloatArray, ys: FloatArray) {
  val xs: FloatArray =
    if (xs.size == 4) FloatArray(4) { xs[it] } else throw IllegalArgumentException("quad needs 4 xs")
  val ys: FloatArray =
    if (ys.size == 4) FloatArray(4) { ys[it] } else throw IllegalArgumentException("quad needs 4 ys")

  fun x(index: Int): Float = xs[index]

  fun y(index: Int): Float = ys[index]

  val centerX: Float get() = (xs[0] + xs[1] + xs[2] + xs[3]) / 4f

  val centerY: Float get() = (ys[0] + ys[1] + ys[2] + ys[3]) / 4f

  val minX: Float get() = minOf(xs[0], xs[1], xs[2], xs[3])

  val maxX: Float get() = maxOf(xs[0], xs[1], xs[2], xs[3])

  val minY: Float get() = minOf(ys[0], ys[1], ys[2], ys[3])

  val maxY: Float get() = maxOf(ys[0], ys[1], ys[2], ys[3])

  val width: Float get() = maxX - minX

  val height: Float get() = maxY - minY

  /** Mean of the top and bottom edge lengths: the box's horizontal extent. */
  val edgeWidth: Float
    get() = (distance(0, 1) + distance(3, 2)) / 2f

  /** Mean of the left and right edge lengths: the box's vertical extent. */
  val edgeHeight: Float
    get() = (distance(0, 3) + distance(1, 2)) / 2f

  fun distance(from: Int, to: Int): Float {
    val dx = xs[to] - xs[from]
    val dy = ys[to] - ys[from]
    return kotlin.math.sqrt(dx * dx + dy * dy)
  }

  fun scaled(sx: Float, sy: Float): Quad =
    Quad(FloatArray(4) { xs[it] * sx }, FloatArray(4) { ys[it] * sy })

  override fun toString(): String =
    "Quad(${(0..3).joinToString { "(${xs[it].toInt()},${ys[it].toInt()})" }})"
}

/** One recognized text line. */
data class OcrLine(val text: String, val quad: Quad, val confidence: Float)

/** Everything one recognition pass produced, in reading order. */
data class OcrResult(val lines: List<OcrLine>, val imageWidth: Int, val imageHeight: Int) {
  val isEmpty: Boolean get() = lines.isEmpty()

  val fullText: String get() = lines.joinToString("\n") { it.text }
}

/** A group of lines the user can select and translate as one unit. */
data class OcrParagraph(
  val lines: List<OcrLine>,
  val text: String,
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  val centerX: Float get() = (left + right) / 2f

  val centerY: Float get() = (top + bottom) / 2f
}
