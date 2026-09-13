package com.lingua.app.ui.screentranslate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BallGeometryTest {

  private val size = 120

  @Test
  fun `clamping keeps the ball on screen`() {
    assertEquals(0, BallGeometry.clamp(-40, extent = 1080, ballSize = size))
    assertEquals(960, BallGeometry.clamp(5000, extent = 1080, ballSize = size))
    assertEquals(300, BallGeometry.clamp(300, extent = 1080, ballSize = size))
  }

  @Test
  fun `clamping copes with a screen smaller than the ball`() {
    assertEquals(0, BallGeometry.clamp(50, extent = 80, ballSize = size))
  }

  @Test
  fun `the ball snaps to the nearer edge`() {
    // Centre of the screen or further left goes left; past it goes right.
    assertEquals(10, BallGeometry.snapToEdge(x = 20, screenWidth = 1080, ballSize = size, margin = 10))
    assertEquals(950, BallGeometry.snapToEdge(x = 900, screenWidth = 1080, ballSize = size, margin = 10))
    assertEquals(10, BallGeometry.snapToEdge(x = 400, screenWidth = 1080, ballSize = size, margin = 10))
    assertEquals(950, BallGeometry.snapToEdge(x = 600, screenWidth = 1080, ballSize = size, margin = 10))
  }

  @Test
  fun `a short still press is a tap`() {
    assertTrue(BallGeometry.isTap(dx = 3f, dy = 4f, durationMs = 120, slop = 20f, timeoutMs = 400))
  }

  @Test
  fun `a drag or a long press is not a tap`() {
    assertFalse(BallGeometry.isTap(dx = 60f, dy = 0f, durationMs = 120, slop = 20f, timeoutMs = 400))
    assertFalse(BallGeometry.isTap(dx = 2f, dy = 2f, durationMs = 900, slop = 20f, timeoutMs = 400))
  }

  @Test
  fun `dragging starts once the pointer leaves the slop`() {
    assertFalse(BallGeometry.isDrag(dx = 5f, dy = 5f, slop = 20f))
    assertTrue(BallGeometry.isDrag(dx = 0f, dy = 30f, slop = 20f))
  }
}
