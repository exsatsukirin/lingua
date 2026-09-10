package com.lingua.app.ui.processtext

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingua.app.LinguaApplication
import com.lingua.app.data.settings.ThemeMode
import com.lingua.app.theme.LinguaTheme

/**
 * Handles `ACTION_PROCESS_TEXT`: the "translate this" item offered by any app's text-selection
 * toolbar.
 *
 * Renders as a translucent dialog window (see `Theme.Lingua.ProcessText`) so the user keeps the
 * context of the app they were reading, instead of being thrown into the full Lingua UI.
 */
class ProcessTextActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val selectedText = intent?.processText().orEmpty()
    if (selectedText.isEmpty()) {
      // Nothing to work with (e.g. an empty selection): do not show a pointless popup.
      finish()
      return
    }

    val container = (application as LinguaApplication).container

    setContent {
      val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

      LinguaTheme(
        themeMode = settings?.themeMode ?: ThemeMode.System,
        dynamicColor = settings?.dynamicColor ?: true,
      ) {
        val viewModel: ProcessTextViewModel = viewModel(factory = ProcessTextViewModel.factory(container))
        val state by viewModel.state.collectAsStateWithLifecycle()

        ProcessTextScreen(
          state = state,
          onStart = { viewModel.start(selectedText) },
          onRetry = viewModel::translate,
          onToggleFavorite = viewModel::toggleFavorite,
          onDismiss = { finish() },
        )
      }
    }
  }

  /**
   * Reads the selection the system passed in.
   *
   * `EXTRA_PROCESS_TEXT_READONLY` tells us whether we are allowed to send a replacement back; this
   * app never rewrites the user's text, so the flag is informational only.
   */
  private fun Intent.processText(): String? =
    getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
