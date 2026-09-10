package com.lingua.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.app.data.settings.ThemeMode
import com.lingua.app.theme.LinguaTheme
import com.lingua.app.ui.LinguaApp

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val container = (application as LinguaApplication).container

    setContent {
      val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

      LinguaTheme(
        themeMode = settings?.themeMode ?: ThemeMode.System,
        dynamicColor = settings?.dynamicColor ?: true,
      ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          LinguaApp(container = container)
        }
      }
    }
  }
}
