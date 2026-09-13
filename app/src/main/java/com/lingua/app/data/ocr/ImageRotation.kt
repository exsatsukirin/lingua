package com.lingua.app.data.ocr

/**
 * A quarter turn applied to a screenshot before recognition.
 *
 * Detection finds vertical text perfectly well, but the recognizer only reads horizontal lines, so
 * a screen holding vertical (or upside-down) text is recognized by turning the pixels and mapping
 * the resulting boxes back.
 */
enum class QuarterTurn(val degrees: Int) {
  None(0),
  Clockwise90(90),
  Half(180),
  Clockwise270(270);

  companion object {
    /** Tried in this order when the upright pass reads nothing convincing. */
    val retries: List<QuarterTurn> = listOf(Clockwise90, Clockwise270, Half)
  }
}

/** The same pixels rotated clockwise by [turn]; [QuarterTurn.None] returns the receiver. */
fun RgbImage.rotated(turn: QuarterTurn): RgbImage {
  if (turn == QuarterTurn.None) return this
  val swapped = turn != QuarterTurn.Half
  val outWidth = if (swapped) height else width
  val outHeight = if (swapped) width else height
  val out = IntArray(outWidth * outHeight)
  for (y in 0 until height) {
    for (x in 0 until width) {
      val index =
        when (turn) {
          QuarterTurn.Clockwise90 -> x * outWidth + (height - 1 - y)
          QuarterTurn.Half -> (height - 1 - y) * outWidth + (width - 1 - x)
          QuarterTurn.Clockwise270 -> (width - 1 - x) * outWidth + y
          QuarterTurn.None -> 0
        }
      out[index] = argb[y * width + x]
    }
  }
  return RgbImage(out, outWidth, outHeight)
}

/**
 * Maps a box measured on `image.rotated(turn)` back onto the original image.
 *
 * [originalWidth] and [originalHeight] describe the image *before* it was turned. Coordinates are
 * continuous, so the mapping is the exact inverse of [rotated] without the pixel-index offset.
 */
fun Quad.unrotated(turn: QuarterTurn, originalWidth: Int, originalHeight: Int): Quad {
  if (turn == QuarterTurn.None) return this
  val width = originalWidth.toFloat()
  val height = originalHeight.toFloat()
  val xs = FloatArray(4)
  val ys = FloatArray(4)
  for (i in 0 until 4) {
    val nx = x(i)
    val ny = y(i)
    when (turn) {
      QuarterTurn.Clockwise90 -> {
        // Forward: (x, y) -> (height - y, x)
        xs[i] = ny
        ys[i] = height - nx
      }
      QuarterTurn.Half -> {
        // Forward: (x, y) -> (width - x, height - y)
        xs[i] = width - nx
        ys[i] = height - ny
      }
      QuarterTurn.Clockwise270 -> {
        // Forward: (x, y) -> (y, width - x)
        xs[i] = width - ny
        ys[i] = nx
      }
      QuarterTurn.None -> Unit
    }
  }
  return Quad(xs, ys)
}
