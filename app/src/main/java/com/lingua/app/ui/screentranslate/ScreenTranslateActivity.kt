package com.lingua.app.ui.screentranslate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingua.app.LinguaApplication
import com.lingua.app.data.settings.ThemeMode
import com.lingua.app.theme.LinguaTheme
import com.lingua.app.ui.screenocr.ScreenOcrScreen
import com.lingua.app.ui.screenocr.ScreenOcrViewModel

/**
 * Screen translation started from the resident notification.
 *
 * The window is transparent and draws nothing until a frame has been captured, which is the whole
 * point: whatever app the user was looking at stays visible underneath and is therefore what the
 * capture contains. Once there is an image (or a failure to report) the ordinary screen-text UI
 * takes over.
 */
class ScreenTranslateActivity : ComponentActivity() {

  private val viewModel: ScreenOcrViewModel by viewModels {
    ScreenOcrViewModel.factory((application as LinguaApplication).container)
  }

  private val consent =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      viewModel.onCaptureResult(result.resultCode, result.data)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val container = (application as LinguaApplication).container

    setContent {
      val settings by
        container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

      LinguaTheme(
        themeMode = settings?.themeMode ?: ThemeMode.System,
        dynamicColor = settings?.dynamicColor ?: true,
      ) {
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
          viewModel.onEnter()
          consent.launch(viewModel.consentIntent())
        }

        // Invisible (the window is transparent) until there is something to show, so the captured
        // pixels are the app underneath rather than this one.
        if (state.image != null || state.error != null) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ScreenOcrContent(
              viewModel = viewModel,
              state = state,
              onClose = { finish() },
              onRequestCapture = ::requestCapture,
            )
          }
        }
      }
    }
  }

  /** Re-asks for the screen-capture grant after a denial or a failed frame. */
  private fun requestCapture() {
    viewModel.onEnter()
    consent.launch(viewModel.consentIntent())
  }

  override fun onDestroy() {
    // The ball was taken down so it would not appear in the capture; put it back for next time.
    ScreenTranslateService.restoreBall(this)
    super.onDestroy()
  }
}

@Composable
private fun ScreenOcrContent(
  viewModel: ScreenOcrViewModel,
  state: com.lingua.app.ui.screenocr.ScreenOcrUiState,
  onClose: () -> Unit,
  onRequestCapture: () -> Unit,
) {
  ScreenOcrScreen(
    state = state,
    onBack = onClose,
    onCaptureRequest = onRequestCapture,
    onPickImage = {},
    onRetake = viewModel::retake,
    onToggleParagraph = viewModel::toggleParagraph,
    onSelectAll = viewModel::selectAll,
    onClearSelection = viewModel::clearSelection,
    onEditSource = viewModel::editSource,
    onTargetLanguage = viewModel::setTargetLanguage,
    onSourceOverride = viewModel::setSourceOverride,
    onTranslate = viewModel::translate,
    onRetry = viewModel::retry,
    onToggleFavorite = viewModel::toggleFavorite,
    onDismissError = viewModel::dismissError,
    allowImagePick = false,
  )
}
