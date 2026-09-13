package com.lingua.app.ui.screentranslate

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.lingua.app.R

/**
 * The draggable ball that starts screen translation from any app.
 *
 * Lives in an application-overlay window, so it is drawn over whatever the user is looking at —
 * which is also why [hide] exists: the ball must be gone before a frame is captured, or it would
 * end up inside its own screenshot.
 */
internal class TranslateBall(private val context: Context, private val onTap: () -> Unit) {

  private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)

  private val size = context.resources.getDimensionPixelSize(R.dimen.screen_translate_ball)

  private val margin = context.resources.getDimensionPixelSize(R.dimen.screen_translate_ball_margin)

  private val iconSize = context.resources.getDimensionPixelSize(R.dimen.screen_translate_ball_icon)

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

  private val params =
    WindowManager.LayoutParams(
        size,
        size,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      )
      .apply {
        gravity = Gravity.TOP or Gravity.START
        val screen = screenSize()
        x = BallGeometry.clamp(screen.first - size - margin, screen.first, size)
        y = (screen.second * BALL_HEIGHT_FRACTION).toInt()
      }

  private var view: View? = null

  private var dragging = false

  private var downRawX = 0f

  private var downRawY = 0f

  private var downX = 0

  private var downY = 0

  private var downAt = 0L

  val isShowing: Boolean get() = view != null

  fun show() {
    if (view != null) return
    val ball = build()
    // A missing overlay grant is a normal state, not a crash: stay quietly hidden.
    runCatching { windowManager.addView(ball, params) }.onSuccess { view = ball }
  }

  fun hide() {
    val current = view ?: return
    view = null
    runCatching { windowManager.removeView(current) }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun build(): View {
    val ball =
      FrameLayout(context).apply {
        elevation = size / 4f
        background =
          GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.screen_translate_ball))
            setStroke(
              (size / 24).coerceAtLeast(1),
              ContextCompat.getColor(context, R.color.screen_translate_ball_ring),
            )
          }
        addView(
          ImageView(context).apply {
            setImageResource(R.drawable.ic_stat_screen_capture)
            imageTintList =
              ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.screen_translate_ball_icon)
              )
            layoutParams =
              FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
          }
        )
        setOnTouchListener { target, event -> handleTouch(target, event) }
      }
    return ball
  }

  private fun handleTouch(target: View, event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downRawX = event.rawX
        downRawY = event.rawY
        downX = params.x
        downY = params.y
        downAt = SystemClock.uptimeMillis()
        dragging = false
        target.alpha = 0.75f
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        val dx = event.rawX - downRawX
        val dy = event.rawY - downRawY
        if (!dragging && !BallGeometry.isDrag(dx, dy, touchSlop)) return true
        dragging = true
        val screen = screenSize()
        params.x = BallGeometry.clamp(downX + dx.toInt(), screen.first, size)
        params.y = BallGeometry.clamp(downY + dy.toInt(), screen.second, size)
        runCatching { windowManager.updateViewLayout(target, params) }
        return true
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        target.alpha = 1f
        val dx = event.rawX - downRawX
        val dy = event.rawY - downRawY
        val duration = SystemClock.uptimeMillis() - downAt
        if (BallGeometry.isTap(dx, dy, duration, touchSlop, TAP_TIMEOUT_MS)) {
          onTap()
        } else {
          snapToEdge(target)
        }
        dragging = false
        return true
      }
    }
    return false
  }

  private fun snapToEdge(target: View) {
    val screen = screenSize()
    val from = params.x
    val to = BallGeometry.snapToEdge(from, screen.first, size, margin)
    if (from == to) return
    ValueAnimator.ofInt(from, to).apply {
      duration = SNAP_DURATION_MS
      addUpdateListener { animator ->
        params.x = animator.animatedValue as Int
        runCatching { windowManager.updateViewLayout(target, params) }
      }
      start()
    }
  }

  private fun screenSize(): Pair<Int, Int> {
    val metrics = context.resources.displayMetrics
    return metrics.widthPixels to metrics.heightPixels
  }

  private companion object {
    const val TAP_TIMEOUT_MS = 400L
    const val SNAP_DURATION_MS = 180L
    const val BALL_HEIGHT_FRACTION = 0.42f
  }
}
