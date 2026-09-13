package com.lingua.app.ui.screentranslate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lingua.app.data.notify.ScreenTranslateNotifier

/** The notification's "turn off" action: drop the shortcut without opening the app. */
class ScreenTranslateDismissReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == ScreenTranslateNotifier.ACTION_DISMISS) {
      ScreenTranslateNotifier.setEnabled(context, enabled = false)
    }
  }
}
