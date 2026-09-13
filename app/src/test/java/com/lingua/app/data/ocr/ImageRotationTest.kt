package com.lingua.app.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageRotationTest {

  /** The mapping [RgbImage.rotated] applies, in continuous coordinates. */
  private fun forward(turn: QuarterTurn, x: Float, y: Float, width: Float, height: Float) =
    when (turn) {
      QuarterTurn.None -> x to y
      QuarterTurn.Clockwise90 -> (height - y) to x
      QuarterTurn.Half -> (width - x) to (height - y)
      QuarterTurn.Clockwise270 -> y to (width - x)
    }

  /** A 3x2 image whose red channel is `x * 10 + y`, so every pixel is identifiable. */
  private fun marked(): RgbImage =
    RgbImage(
      IntArray(6) { 0xFF000000.toInt() or (((it % 3) * 10 + (it / 3)) shl 16) },
      3,
      2,
    )

  @Test
  fun `a quarter turn clockwise moves the top left pixel to the top right`() {
    val turned = marked().rotated(QuarterTurn.Clockwise90)

    assertEquals(2, turned.width)
    assertEquals(3, turned.height)
    assertEquals(0, turned.redAt(1, 0))
    assertEquals(1, turned.redAt(0, 0))
    assertEquals(20, turned.redAt(1, 2))
    assertEquals(21, turned.redAt(0, 2))
  }

  @Test
  fun `half a turn swaps opposite corners`() {
    val turned = marked().rotated(QuarterTurn.Half)

    assertEquals(3, turned.width)
    assertEquals(2, turned.height)
    assertEquals(0, turned.redAt(2, 1))
    assertEquals(20, turned.redAt(0, 1))
    assertEquals(11, turned.redAt(1, 0))
  }

  @Test
  fun `three quarter turns move the top left pixel to the bottom left`() {
    val turned = marked().rotated(QuarterTurn.Clockwise270)

    assertEquals(2, turned.width)
    assertEquals(3, turned.height)
    assertEquals(0, turned.redAt(0, 2))
    assertEquals(20, turned.redAt(0, 0))
    assertEquals(21, turned.redAt(1, 0))
  }

  @Test
  fun `unrotating a box is the inverse of turning the image`() {
    val width = 1080
    val height = 1600
    val original = Quad(floatArrayOf(37f, 89f, 89f, 37f), floatArrayOf(50f, 50f, 582f, 582f))

    for (turn in QuarterTurn.entries) {
      val movedXs = FloatArray(4)
      val movedYs = FloatArray(4)
      for (i in 0 until 4) {
        val (x, y) = forward(turn, original.x(i), original.y(i), width.toFloat(), height.toFloat())
        movedXs[i] = x
        movedYs[i] = y
      }

      val restored = Quad(movedXs, movedYs).unrotated(turn, width, height)

      for (i in 0 until 4) {
        assertEquals("turn $turn corner $i x", original.x(i), restored.x(i), 0.01f)
        assertEquals("turn $turn corner $i y", original.y(i), restored.y(i), 0.01f)
      }
    }
  }

  @Test
  fun `a box found on a turned image lands inside the original frame`() {
    val width = 1080
    val height = 1600
    // Somewhere in the middle of the 1600x1080 turned image.
    val onTurned = Quad(floatArrayOf(100f, 900f, 900f, 100f), floatArrayOf(80f, 80f, 260f, 260f))

    for (turn in QuarterTurn.retries) {
      val original = onTurned.unrotated(turn, width, height)
      for (i in 0 until 4) {
        assertTrue("turn $turn x ${original.x(i)}", original.x(i) in 0f..width.toFloat())
        assertTrue("turn $turn y ${original.y(i)}", original.y(i) in 0f..height.toFloat())
      }
    }
  }
}
