package com.lingua.app.data.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lingua.app.R
import com.lingua.app.ui.screentranslate.ScreenTranslateActivity
import com.lingua.app.ui.screentranslate.ScreenTranslateDismissReceiver
import com.lingua.app.ui.screentranslate.ScreenTranslateService

/**
 * The resident screen-translate shortcut.
 *
 * It is a notification plus a draggable ball, both owned by [ScreenTranslateService]: the
 * notification is how the shortcut survives without an overlay grant, and the ball is the faster
 * way to reach it. Nothing is captured until the user actually taps one of them.
 */
object ScreenTranslateNotifier {

  const val CHANNEL_ID = "screen_translate"

  const val ACTION_DISMISS = "com.lingua.app.action.DISMISS_SCREEN_TRANSLATE"

  const val NOTIFICATION_ID = 0x12

  fun setEnabled(context: Context, enabled: Boolean) {
    val intent = Intent(context, ScreenTranslateService::class.java)
    if (!enabled) {
      context.stopService(intent)
      NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
      return
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    runCatching {
      ContextCompat.startForegroundService(context, intent.setAction(ScreenTranslateService.ACTION_START))
    }
  }

  /** True while the resident notification is on screen; used to keep the switch honest. */
  fun isShowing(context: Context): Boolean =
    NotificationManagerCompat.from(context)
      .activeNotifications
      .any { it.id == NOTIFICATION_ID }

  internal fun notification(context: Context): Notification {
    ensureChannel(context)
    val open =
      PendingIntent.getActivity(
        context,
        0,
        Intent(context, ScreenTranslateActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val dismiss =
      PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, ScreenTranslateDismissReceiver::class.java).setAction(ACTION_DISMISS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_screen_capture)
      .setContentTitle(context.getString(R.string.screen_translate_notification_title))
      .setContentText(context.getString(R.string.screen_translate_notification_text))
      .setContentIntent(open)
      .addAction(0, context.getString(R.string.screen_translate_action_dismiss), dismiss)
      .setOngoing(true)
      .setShowWhen(false)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .build()
  }

  private fun ensureChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      manager.createNotificationChannel(
        NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.screen_translate_channel),
          NotificationManager.IMPORTANCE_LOW,
        )
      )
    }
  }
}
