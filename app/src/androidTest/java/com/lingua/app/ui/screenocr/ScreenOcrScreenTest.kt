package com.lingua.app.ui.screenocr

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingua.app.data.ocr.OcrParagraph
import com.lingua.app.domain.Language
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenOcrScreenTest {

  @get:Rule val compose = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private fun string(id: Int) = context.getString(id)

  private val target = Language("zh", "Chinese (Simplified)", "简体中文")

  private fun paragraph(text: String) =
    OcrParagraph(lines = emptyList(), text = text, left = 0f, top = 0f, right = 100f, bottom = 40f)

  private fun show(
    state: ScreenOcrUiState,
    onToggleParagraph: (Int) -> Unit = {},
    onTranslate: () -> Unit = {},
  ) {
    compose.setContent {
      MaterialTheme {
        ScreenOcrScreen(
          state = state,
          onBack = {},
          onCaptureRequest = {},
          onPickImage = {},
          onRetake = {},
          onToggleParagraph = onToggleParagraph,
          onSelectAll = {},
          onClearSelection = {},
          onEditSource = {},
          onTargetLanguage = {},
          onSourceOverride = {},
          onTranslate = onTranslate,
          onRetry = {},
          onToggleFavorite = {},
          onDismissError = {},
        )
      }
    }
  }

  @Test
  fun idleStateOffersCaptureAndImport() {
    show(ScreenOcrUiState(targetLanguage = target))

    compose.onNodeWithText(string(com.lingua.app.R.string.screen_ocr_capture)).assertExists()
    compose.onNodeWithText(string(com.lingua.app.R.string.screen_ocr_idle_title)).assertExists()
  }

  @Test
  fun nothingIsSelectedUntilTheUserTapsABlock() {
    val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
    show(
      state =
        ScreenOcrUiState(
          phase = ScreenOcrPhase.Ready,
          image = bitmap,
          paragraphs = listOf(paragraph("Hello world")),
          selected = emptySet(),
          targetLanguage = target,
        )
    )

    compose.onNodeWithText(string(com.lingua.app.R.string.screen_ocr_translate)).assertIsNotEnabled()
    compose.onNodeWithText(string(com.lingua.app.R.string.screen_ocr_hint_tap)).assertExists()
  }

  @Test
  fun aSelectionIsSummarisedInTheBar() {
    val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
    show(
      state =
        ScreenOcrUiState(
          phase = ScreenOcrPhase.Ready,
          image = bitmap,
          paragraphs = listOf(paragraph("Hello world"), paragraph("Second block")),
          selected = setOf(0),
          targetLanguage = target,
        )
    )

    compose
      .onNodeWithText(context.getString(com.lingua.app.R.string.screen_ocr_selected_count, 1))
      .assertExists()
    compose.onNodeWithText("Hello world").assertExists()
    compose.onNodeWithText(string(com.lingua.app.R.string.screen_ocr_translate)).assertIsEnabled()
  }

  @Test
  fun selectAllSelectsEveryBlock() {
    val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
    var selectionCount = -1
    compose.setContent {
      MaterialTheme {
        ScreenOcrScreen(
          state =
            ScreenOcrUiState(
              phase = ScreenOcrPhase.Ready,
              image = bitmap,
              paragraphs = listOf(paragraph("One"), paragraph("Two")),
              selected = emptySet(),
              targetLanguage = target,
            ),
          onBack = {},
          onCaptureRequest = {},
          onPickImage = {},
          onRetake = {},
          onToggleParagraph = {},
          onSelectAll = { selectionCount = 2 },
          onClearSelection = {},
          onEditSource = {},
          onTargetLanguage = {},
          onSourceOverride = {},
          onTranslate = {},
          onRetry = {},
          onToggleFavorite = {},
          onDismissError = {},
        )
      }
    }

    compose.onNodeWithText(string(com.lingua.app.R.string.screen_ocr_select_all)).performClick()

    assertEquals(2, selectionCount)
  }
}
