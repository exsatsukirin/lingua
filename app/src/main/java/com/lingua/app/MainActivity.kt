package com.lingua.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.app.data.settings.ThemeMode
import com.lingua.app.theme.LinguaTheme
import com.lingua.app.ui.LinguaApp

class MainActivity : ComponentActivity() {

  /**
   * Screenshot handed over by another app, waiting to be opened. Kept as snapshot state so a
   * second share while the app is already open still navigates.
   */
  private val sharedImage = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val container = (application as LinguaApplication).container
    // Only on a cold start: a configuration change must not re-open the shared image.
    if (savedInstanceState == null) sharedImage.value = imageFromIntent(intent)

    setContent {
      val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

      LinguaTheme(
        themeMode = settings?.themeMode ?: ThemeMode.System,
        dynamicColor = settings?.dynamicColor ?: true,
      ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          LinguaApp(
            container = container,
            sharedImageUri = sharedImage.value,
            onSharedImageHandled = { sharedImage.value = null },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    sharedImage.value = imageFromIntent(intent)
  }

  private fun imageFromIntent(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_SEND) return null
    return extraStream(intent)?.toString()
  }

  private fun extraStream(intent: Intent): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
      @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
}
