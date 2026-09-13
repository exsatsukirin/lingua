package com.lingua.app.ui.screentranslate

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.ServiceCompat
import com.lingua.app.data.notify.ScreenTranslateNotifier

/**
 * Keeps the screen-translate shortcut alive: the resident notification and, when the user granted
 * "display over other apps", the draggable ball.
 *
 * This has to be a foreground service for the ball to survive the app being backgrounded — an
 * overlay belongs to its process, so a plain notification would leave a dead ball behind.
 */
class ScreenTranslateService : Service() {

  private lateinit var ball: TranslateBall

  override fun onCreate() {
    super.onCreate()
    ball = TranslateBall(this, onTap = ::startScreenTranslation)
    running = true
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_SHOW_BALL -> {
        updateBall()
        return START_STICKY
      }
      else -> {
        startForeground()
        updateBall()
      }
    }
    return START_STICKY
  }

  override fun onDestroy() {
    ball.hide()
    running = false
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startForeground() {
    ServiceCompat.startForeground(
      this,
      ScreenTranslateNotifier.NOTIFICATION_ID,
      ScreenTranslateNotifier.notification(this),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      } else {
        0
      },
    )
  }

  private fun updateBall() {
    if (Settings.canDrawOverlays(this)) ball.show() else ball.hide()
  }

  /**
   * The ball is taken down first: screen capture sees overlay windows, and the shortcut should not
   * appear in the screenshot it is taking.
   */
  private fun startScreenTranslation() {
    ball.hide()
    startActivity(
      Intent(this, ScreenTranslateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }

  companion object {
    const val ACTION_START = "com.lingua.app.action.START_SCREEN_TRANSLATE"
    const val ACTION_STOP = "com.lingua.app.action.STOP_SCREEN_TRANSLATE"
    const val ACTION_SHOW_BALL = "com.lingua.app.action.SHOW_TRANSLATE_BALL"

    @Volatile var running: Boolean = false
      private set

    /** Brings the ball back once the capture UI is done with the screen. */
    fun restoreBall(context: Context) {
      if (!running) return
      runCatching {
        context.startService(
          Intent(context, ScreenTranslateService::class.java).setAction(ACTION_SHOW_BALL)
        )
      }
    }
  }
}
