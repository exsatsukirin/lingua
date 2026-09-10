package com.lingua.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks the three top-level destinations to catch navigation or composition crashes. */
@RunWith(AndroidJUnit4::class)
class MainNavigationSmokeTest {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun allThreeTabsRender() {
    // The tab labels also appear in app bars, so target the placeholder/section copy instead.
    composeRule.onNodeWithText("输入要翻译的文本").assertExists()

    composeRule.onAllNodesWithText("历史").onFirst().performClick()
    composeRule.onNodeWithText("搜索历史记录").assertExists()

    composeRule.onAllNodesWithText("设置").onFirst().performClick()
    composeRule.onNodeWithText("外观").assertExists()
  }

  @Test
  fun languagePickerOpens() {
    composeRule.onNodeWithText("简体中文 · Chinese (Simplified)").performClick()
    composeRule.onNodeWithText("目标语言").assertExists()
    composeRule.onNodeWithText("日本語").assertExists()
  }
}
