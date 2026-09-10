package com.lingua.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingua.app.ui.processtext.ProcessTextActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cover for the "translate selected text" entry point.
 *
 * The popup is driven by `ACTION_PROCESS_TEXT`, so these tests hand the activity the same intent the
 * system would and check what the user sees.
 */
@RunWith(AndroidJUnit4::class)
class ProcessTextIntentTest {

  @get:Rule val composeRule = createEmptyComposeRule()

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private fun processTextIntent(text: String?): Intent =
    Intent(context, ProcessTextActivity::class.java)
      .setAction(Intent.ACTION_PROCESS_TEXT)
      .setType("text/plain")
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      .apply { text?.let { putExtra(Intent.EXTRA_PROCESS_TEXT, it) } }

  @Test
  fun showsTheSelectedTextAndItsSourceLabel() {
    ActivityScenario.launch<ProcessTextActivity>(processTextIntent("Text handed over by another app")).use {
      composeRule.onNodeWithText(context.getString(R.string.process_text_source_label)).assertIsDisplayed()
      composeRule.onNodeWithText("Text handed over by another app").assertIsDisplayed()
    }
  }

  @Test
  fun blankSelectionDoesNotOpenAPopup() {
    ActivityScenario.launch<ProcessTextActivity>(processTextIntent("   ")).use {
      composeRule.onNodeWithText(context.getString(R.string.process_text_source_label)).assertDoesNotExist()
    }
  }

  @Test
  fun missingSelectionDoesNotOpenAPopup() {
    ActivityScenario.launch<ProcessTextActivity>(processTextIntent(null)).use {
      composeRule.onNodeWithText(context.getString(R.string.process_text_source_label)).assertDoesNotExist()
    }
  }
}
