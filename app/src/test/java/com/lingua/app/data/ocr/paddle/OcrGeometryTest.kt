package com.lingua.app.data.ocr.paddle

import com.lingua.app.data.ocr.Quad
import com.lingua.app.data.ocr.RgbImage
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrGeometryTest {

  private fun quad(vararg points: Pair<Float, Float>): Quad =
    Quad(FloatArray(4) { points[it].first }, FloatArray(4) { points[it].second })

  @Test
  fun `minAreaRect recovers an axis aligned box`() {
    val xs = floatArrayOf(10f, 90f, 90f, 10f, 50f, 50f)
    val ys = floatArrayOf(20f, 20f, 60f, 60f, 20f, 60f)

    val rect = OcrGeometry.minAreaRect(xs, ys)

    assertNotNull(rect)
    assertEquals(80f, rect!!.width, 0.01f)
    assertEquals(40f, rect.height, 0.01f)
    assertEquals(3200f, OcrGeometry.area(rect), 0.5f)
  }

  @Test
  fun `minAreaRect recovers a rotated box`() {
    // A 100x20 rectangle rotated 30 degrees.
    val angle = Math.toRadians(30.0)
    val cos = kotlin.math.cos(angle).toFloat()
    val sin = kotlin.math.sin(angle).toFloat()
    fun rotate(x: Float, y: Float): Pair<Float, Float> =
      Pair(x * cos - y * sin + 200f, x * sin + y * cos + 200f)
    val corners = listOf(rotate(0f, 0f), rotate(100f, 0f), rotate(100f, 20f), rotate(0f, 20f))
    val xs = FloatArray(4) { corners[it].first }
    val ys = FloatArray(4) { corners[it].second }

    val rect = OcrGeometry.minAreaRect(xs, ys)!!

    assertEquals(100f, maxOf(rect.edgeWidth, rect.edgeHeight), 1.0f)
    assertEquals(20f, minOf(rect.edgeWidth, rect.edgeHeight), 1.0f)
    assertEquals(2000f, OcrGeometry.area(rect), 30f)
  }

  @Test
  fun `orderQuad normalises corners to top left first`() {
    // Cyclic order starting at the bottom-right, which is what minAreaRect produces.
    val rotated = quad(90f to 60f, 90f to 20f, 10f to 20f, 10f to 60f)

    val ordered = OcrGeometry.orderQuad(rotated)

    assertEquals(10f, ordered.x(0), 0.01f)
    assertEquals(20f, ordered.y(0), 0.01f)
    assertEquals(90f, ordered.x(1), 0.01f)
    assertEquals(20f, ordered.y(1), 0.01f)
    assertEquals(90f, ordered.x(2), 0.01f)
    assertEquals(60f, ordered.y(2), 0.01f)
    assertEquals(10f, ordered.x(3), 0.01f)
    assertEquals(60f, ordered.y(3), 0.01f)
  }

  @Test
  fun `orderQuad keeps a tall box tall`() {
    // The order minAreaRect produced for a vertical text column, where the top-right corner sits
    // 0.12px above the top-left one. The old "highest corner first" rule transposed this box, which
    // made the recognizer un-warp the crop rotated by 90 degrees.
    val tall = quad(88.95f to 50.0f, 90.19f to 582.79f, 38.44f to 582.91f, 37.20f to 50.12f)

    val ordered = OcrGeometry.orderQuad(tall)

    assertEquals(37.20f, ordered.x(0), 0.01f)
    assertEquals(50.12f, ordered.y(0), 0.01f)
    assertEquals(88.95f, ordered.x(1), 0.01f)
    assertEquals(50.0f, ordered.y(1), 0.01f)
    assertEquals(90.19f, ordered.x(2), 0.01f)
    assertEquals(582.79f, ordered.y(2), 0.01f)
    assertEquals(38.44f, ordered.x(3), 0.01f)
    assertEquals(582.91f, ordered.y(3), 0.01f)
    assertTrue("width ${ordered.edgeWidth} should be the short side", ordered.edgeWidth < 60f)
    assertTrue("height ${ordered.edgeHeight} should be the long side", ordered.edgeHeight > 500f)
  }

  @Test
  fun `orderQuad keeps a wide box wide`() {
    val wide = quad(10f to 80f, 200f to 80f, 200f to 100f, 10f to 100f)

    val ordered = OcrGeometry.orderQuad(wide)

    assertEquals(10f, ordered.x(0), 0.01f)
    assertEquals(80f, ordered.y(0), 0.01f)
    assertEquals(200f, ordered.x(1), 0.01f)
    assertEquals(190f, ordered.edgeWidth, 0.01f)
    assertEquals(20f, ordered.edgeHeight, 0.01f)
  }

  @Test
  fun `unclip grows a box outwards on every side`() {
    val box = quad(0f to 0f, 100f to 0f, 100f to 20f, 0f to 20f)

    val grown = OcrGeometry.unclip(box, 1.4f)

    // delta = area * ratio / perimeter = 2000 * 1.4 / 240
    val expected = 2000f * 1.4f / 240f
    assertTrue("grew left", grown.minX < -expected + 0.5f)
    assertTrue("grew right", grown.maxX > 100f + expected - 0.5f)
    assertTrue("grew up", grown.minY < -expected + 0.5f)
    assertTrue("grew down", grown.maxY > 20f + expected - 0.5f)
    assertTrue(OcrGeometry.area(grown) > OcrGeometry.area(box))
  }

  @Test
  fun `cropQuad copies an axis aligned region verbatim`() {
    val pixels = IntArray(4 * 4) { index ->
      val x = index % 4
      val y = index / 4
      0xFF000000.toInt() or (x * 40 shl 16) or (y * 40 shl 8)
    }
    val image = RgbImage(pixels, 4, 4)
    val region = quad(0f to 0f, 4f to 0f, 4f to 4f, 0f to 4f)

    val cropped = OcrGeometry.cropQuad(image, region, 4, 4)

    // Sampling happens at pixel centres, so the first output pixel is halfway between source
    // pixels 0 and 1 of the 40-step ramp; the last column clamps to the final source pixel.
    for (index in pixels.indices) {
      val x = index % 4
      val y = index / 4
      assertEquals("red $index", minOf(20 + 40 * x, 120), (cropped[index] shr 16) and 0xFF)
      assertEquals("green $index", minOf(20 + 40 * y, 120), (cropped[index] shr 8) and 0xFF)
    }
  }

  @Test
  fun `cropQuad maps a sub rectangle to the requested size`() {
    val pixels = IntArray(8 * 8) { index ->
      val x = index % 8
      0xFF000000.toInt() or (x * 20 shl 16)
    }
    val image = RgbImage(pixels, 8, 8)
    // Right half of the image, upscaled to 8x8.
    val region = quad(4f to 0f, 8f to 0f, 8f to 8f, 4f to 8f)

    val cropped = OcrGeometry.cropQuad(image, region, 8, 8)

    // Left column of the crop samples x=4 (80), right column samples x≈8 (clamped to 140).
    assertEquals(80.0, ((cropped[0] shr 16) and 0xFF).toDouble(), 12.0)
    assertEquals(140.0, ((cropped[7] shr 16) and 0xFF).toDouble(), 12.0)
  }

  @Test
  fun `homography maps the destination rect onto the source quad`() {
    val h = OcrGeometry.homography(
      fromX = floatArrayOf(0f, 10f, 10f, 0f),
      fromY = floatArrayOf(0f, 0f, 10f, 10f),
      toX = floatArrayOf(100f, 200f, 200f, 100f),
      toY = floatArrayOf(50f, 50f, 150f, 150f),
    )

    fun project(x: Float, y: Float): Pair<Float, Float> {
      val denominator = h[6] * x + h[7] * y + h[8]
      return Pair((h[0] * x + h[1] * y + h[2]) / denominator, (h[3] * x + h[4] * y + h[5]) / denominator)
    }

    val topLeft = project(0f, 0f)
    assertEquals(100f, topLeft.first, 0.01f)
    assertEquals(50f, topLeft.second, 0.01f)
    val bottomRight = project(10f, 10f)
    assertEquals(200f, bottomRight.first, 0.01f)
    assertEquals(150f, bottomRight.second, 0.01f)
  }

  @Test
  fun `boxScore averages the probability inside the box only`() {
    val prob = FloatArray(10 * 10) { 0.1f }
    for (x in 2 until 8) for (y in 2 until 8) prob[y * 10 + x] = 0.9f
    val box = quad(2f to 2f, 8f to 2f, 8f to 8f, 2f to 8f)

    val score = OcrGeometry.boxScore(prob, 10, 10, box)

    assertTrue("score was $score", abs(score - 0.9f) < 0.02f)
  }
}
