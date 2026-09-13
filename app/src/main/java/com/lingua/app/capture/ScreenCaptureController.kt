package com.lingua.app.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a screen capture attempt is doing right now. */
sealed interface CaptureState {
  data object Idle : CaptureState

  data object Capturing : CaptureState

  data class Success(val bitmap: Bitmap) : CaptureState

  data class Failed(val reason: Failure) : CaptureState

  enum class Failure {
    /** The system dialog was dismissed or the projection was stopped mid-capture. */
    Denied,

    /** Nothing usable came back — usually a `FLAG_SECURE` window, which captures as black. */
    Empty,

    Timeout,
  }
}

/**
 * Drives the one-shot screen capture.
 *
 * Android 14 requires a foreground service with the `mediaProjection` type to be running *before*
 * the token is redeemed, and forbids reusing a token after the projection stops, so every capture
 * asks for consent again. The heavy lifting lives in [ScreenCaptureService]; this class is the
 * process-wide handle the UI observes.
 */
class ScreenCaptureController(private val context: Context) {

  private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)

  val state: StateFlow<CaptureState> = _state.asStateFlow()

  private val manager: MediaProjectionManager
    get() = context.getSystemService(MediaProjectionManager::class.java)

  /**
   * The intent that shows the system "start capturing?" dialog.
   *
   * Android 14 lets the user share a single app instead, which would hide most of what we are
   * looking for; asking for the default display up front keeps it one tap and whole-screen.
   */
  fun consentIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
    } else {
      manager.createScreenCaptureIntent()
    }

  /** Starts the capture after the user accepted the system dialog. */
  fun capture(resultCode: Int, data: Intent) {
    _state.value = CaptureState.Capturing
    val intent =
      Intent(context, ScreenCaptureService::class.java)
        .setAction(ScreenCaptureService.ACTION_CAPTURE)
        .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
        .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
    context.startForegroundService(intent)
  }

  fun fail(reason: CaptureState.Failure) {
    _state.value = CaptureState.Failed(reason)
  }

  fun reset() {
    if (_state.value !is CaptureState.Capturing) _state.value = CaptureState.Idle
  }

  internal fun publish(bitmap: Bitmap) {
    _state.value = CaptureState.Success(bitmap)
  }
}
