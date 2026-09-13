package com.lingua.app.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lingua.app.R
import com.lingua.app.ui.screentranslate.ScreenTranslateActivity
import com.lingua.app.ui.screentranslate.ScreenTranslateDismissReceiver

/**
 * The resident notification that makes "translate whatever is on screen" a one-tap action.
 *
 * Deliberately not a foreground service: the notification only has to outlive the app so a tap can
 * start screen recognition, and a posted notification does that on its own. Capturing a frame still
 * goes through the `mediaProjection` foreground service, which runs only while it captures.
 */
object ScreenTranslateNotifier {

  const val CHANNEL_ID = "screen_translate"

  const val ACTION_DISMISS = "com.lingua.app.action.DISMISS_SCREEN_TRANSLATE"

  private const val NOTIFICATION_ID = 0x12

  fun setEnabled(context: Context, enabled: Boolean) {
    if (!enabled) {
      cancel(context)
      return
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    ensureChannel(context)
    runCatching {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context))
    }
  }

  fun cancel(context: Context) {
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
  }

  /** True while the resident notification is on screen; used to keep the switch honest. */
  fun isShowing(context: Context): Boolean =
    NotificationManagerCompat.from(context)
      .activeNotifications
      .any { it.id == NOTIFICATION_ID }

  private fun build(context: Context): android.app.Notification {
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
