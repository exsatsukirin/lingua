package com.lingua.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingua.app.data.history.TranslationRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression cover for the history detail sheet.
 *
 * A long translation used to be clipped with no way to scroll, so the tail of the text and the
 * actions below it were unreachable on small screens. Reaching the copy action by scrolling proves
 * the sheet is scrollable.
 */
@RunWith(AndroidJUnit4::class)
class HistoryDetailScrollTest {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  private val sourceText = "SCROLL-REGRESSION-SOURCE"

  /** Far taller than any phone screen at this text size. */
  private val longTranslation =
    buildString {
        repeat(150) { append("这是一段用于验证详情弹窗可以滚动的较长译文，需要能够滚动查看完整内容。") }
        append("TAIL-MARKER")
      }
      .also { require(it.length > 5000) { "test fixture must exceed the viewport" } }

  private val repository
    get() = ApplicationProvider.getApplicationContext<LinguaApplication>().container.historyRepository

  @Before
  fun seedOneLongRecord() {
    runBlocking {
      removeTestRecords()
      repository.insert(
        TranslationRecord(
          sourceText = sourceText,
          translatedText = longTranslation,
          sourceLangCode = "en",
          sourceLangName = "English",
          targetLangCode = "zh",
          targetLangName = "简体中文",
          providerName = "Test",
          model = "test-model",
          createdAt = System.currentTimeMillis(),
        )
      )
    }
    composeRule.waitForIdle()
  }

  @After
  fun removeSeededRecord() = runBlocking { removeTestRecords() }

  /**
   * Only ever touches rows created by this test. Instrumented tests run against the real app
   * database, so wiping the table here would destroy the user's own translation history.
   */
  private suspend fun removeTestRecords() {
    repository
      .observe(query = "", favoritesOnly = false, limit = 500)
      .first()
      .filter { it.sourceText == sourceText }
      .forEach { repository.delete(it.id) }
  }

  @Test
  fun longTranslationCanBeScrolledFullyIntoView() {
    composeRule
      .onAllNodesWithText(composeRule.activity.getString(R.string.tab_history))
      .onFirst()
      .performClick()

    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithText(sourceText).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithText(sourceText).performClick()
    composeRule.waitForIdle()

    // The copy action sits below the whole translation: it is only reachable if the sheet scrolls.
    composeRule
      .onNodeWithText(composeRule.activity.getString(R.string.history_copy_translation))
      .performScrollTo()
      .assertIsDisplayed()
  }
}
