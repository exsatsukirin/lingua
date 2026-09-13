package com.lingua.app.ui.screentranslate

import kotlin.math.hypot

/**
 * Placement and tap rules for the floating ball, kept free of Android types so they can be tested.
 */
internal object BallGeometry {

  /** Keeps the ball fully on screen. */
  fun clamp(position: Int, extent: Int, ballSize: Int): Int =
    position.coerceIn(0, (extent - ballSize).coerceAtLeast(0))

  /** Snaps the ball to whichever horizontal edge it is nearer, leaving [margin] of breathing room. */
  fun snapToEdge(x: Int, screenWidth: Int, ballSize: Int, margin: Int): Int {
    val right = (screenWidth - ballSize - margin).coerceAtLeast(margin)
    val centre = x + ballSize / 2
    return if (centre < screenWidth / 2) margin else right
  }

  /**
   * True when the pointer barely moved and was released quickly — the difference between a tap that
   * should start screen translation and a drag that only moves the ball.
   */
  fun isTap(dx: Float, dy: Float, durationMs: Long, slop: Float, timeoutMs: Long): Boolean =
    hypot(dx.toDouble(), dy.toDouble()) <= slop && durationMs <= timeoutMs

  /** True once the pointer has moved far enough that the gesture is a drag rather than a tap. */
  fun isDrag(dx: Float, dy: Float, slop: Float): Boolean =
    hypot(dx.toDouble(), dy.toDouble()) > slop
}
