package com.lingua.app.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lingua.app.R
import com.lingua.app.appContainer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Grabs exactly one frame of the display and hands it to [ScreenCaptureController].
 *
 * This is a foreground service because Android 14 refuses `getMediaProjection` unless a
 * `mediaProjection`-typed foreground service is already running. It stops itself as soon as the
 * frame is in hand, so its notification is visible for a fraction of a second.
 */
class ScreenCaptureService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val handlerThread = HandlerThread("lingua-capture")

  private val stopped = AtomicBoolean(false)

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action != ACTION_CAPTURE) {
      stopSelf()
      return START_NOT_STICKY
    }

    startCaptureForeground()

    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
    val resultData: Intent? = readResultData(intent)
    if (resultData == null) {
      application.appContainer.screenCapture.fail(CaptureState.Failure.Denied)
      stopSelf()
      return START_NOT_STICKY
    }

    scope.launch { captureOneFrame(resultCode, resultData) }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    scope.cancel()
    handlerThread.quitSafely()
    super.onDestroy()
  }

  @Suppress("DEPRECATION")
  private fun readResultData(intent: Intent): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
      intent.getParcelableExtra(EXTRA_RESULT_DATA)
    }

  private fun startCaptureForeground() {
    val manager = getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      manager.createNotificationChannel(
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.screen_capture_channel),
          NotificationManager.IMPORTANCE_LOW,
        )
      )
    }
    val notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_screen_capture)
        .setContentTitle(getString(R.string.screen_capture_notification_title))
        .setContentText(getString(R.string.screen_capture_notification_text))
        .setOngoing(true)
        .build()
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      notification,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
      } else {
        0
      },
    )
  }

  private suspend fun captureOneFrame(resultCode: Int, resultData: Intent) {
    val controller = application.appContainer.screenCapture
    if (!handlerThread.isAlive) handlerThread.start()
    val handler = Handler(handlerThread.looper)
    val metrics = displayMetrics()

    var projection: MediaProjection? = null
    var virtualDisplay: VirtualDisplay? = null
    var reader: ImageReader? = null
    val callback =
      object : MediaProjection.Callback() {
        override fun onStop() {
          stopped.set(true)
        }
      }

    try {
      val activeProjection =
        getSystemService(MediaProjectionManager::class.java)
          .getMediaProjection(resultCode, resultData)
          ?: throw IllegalStateException("media projection token was rejected")
      projection = activeProjection
      activeProjection.registerCallback(callback, handler)

      // The consent dialog is torn down right about now, and whatever it leaves on the display for a
      // frame or two would otherwise end up inside the captured image.
      delay(CAPTURE_SETTLE_DELAY_MS)

      reader =
        ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)

      // The listener has to be in place before the display starts feeding the surface, otherwise
      // the very first (and only) frame can arrive unnoticed.
      val frame = CompletableDeferred<Bitmap?>()
      reader.setOnImageAvailableListener({ source ->
        val image = source.acquireLatestImage()
        if (image == null) {
          frame.complete(null)
          return@setOnImageAvailableListener
        }
        try {
          frame.complete(image.toBitmap(metrics.widthPixels, metrics.heightPixels))
        } catch (error: Throwable) {
          frame.completeExceptionally(error)
        } finally {
          image.close()
        }
      }, handler)

      virtualDisplay =
        activeProjection.createVirtualDisplay(
          DISPLAY_NAME,
          metrics.widthPixels,
          metrics.heightPixels,
          metrics.densityDpi,
          DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
          reader.surface,
          null,
          handler,
        )

      val bitmap = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { frame.await() }
      when {
        stopped.get() -> controller.fail(CaptureState.Failure.Denied)
        bitmap == null -> controller.fail(CaptureState.Failure.Timeout)
        bitmap.isFlat() -> {
          bitmap.recycle()
          controller.fail(CaptureState.Failure.Empty)
        }
        else -> controller.publish(bitmap)
      }
    } catch (error: Throwable) {
      controller.fail(if (stopped.get()) CaptureState.Failure.Denied else CaptureState.Failure.Empty)
    } finally {
      reader?.setOnImageAvailableListener(null, null)
      virtualDisplay?.release()
      runCatching { projection?.unregisterCallback(callback) }
      runCatching { projection?.stop() }
      reader?.close()
      stopSelf()
    }
  }

  private fun displayMetrics(): DisplayMetrics {
    val metrics = DisplayMetrics()
    val windowManager = getSystemService(WindowManager::class.java)
    @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(metrics)
    return metrics
  }

  /** A `FLAG_SECURE` window is captured as one flat colour. */
  private fun Bitmap.isFlat(): Boolean {
    if (width == 0 || height == 0) return true
    val step = (minOf(width, height) / 16).coerceAtLeast(1)
    val first = getPixel(0, 0)
    var y = 0
    while (y < height) {
      var x = 0
      while (x < width) {
        if (getPixel(x, y) != first) return false
        x += step
      }
      y += step
    }
    return true
  }

  private fun Image.toBitmap(width: Int, height: Int): Bitmap {
    val plane = planes[0]
    val pixelStride = plane.pixelStride
    val rowPadding = plane.rowStride - pixelStride * width
    val padded =
      Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
    padded.copyPixelsFromBuffer(plane.buffer)
    if (rowPadding == 0) return padded
    val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
    if (cropped !== padded) padded.recycle()
    return cropped
  }

  companion object {
    const val ACTION_CAPTURE = "com.lingua.app.action.CAPTURE_SCREEN"
    const val EXTRA_RESULT_CODE = "resultCode"
    const val EXTRA_RESULT_DATA = "resultData"

    private const val CHANNEL_ID = "screen_capture"
    private const val NOTIFICATION_ID = 0x11
    private const val DISPLAY_NAME = "lingua-capture"
    private const val CAPTURE_TIMEOUT_MS = 4_000L
    private const val CAPTURE_SETTLE_DELAY_MS = 250L

    fun intent(context: Context): Intent = Intent(context, ScreenCaptureService::class.java)
  }
}
