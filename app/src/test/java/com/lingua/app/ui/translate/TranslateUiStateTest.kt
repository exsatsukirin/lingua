package com.lingua.app.ui.translate

import com.lingua.app.domain.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateUiStateTest {

  private val chinese = Language("zh", "Chinese (Simplified)", "简体中文")
  private val english = Language("en", "English", "English")

  private fun state(
    text: String = "",
    result: TranslateResultState? = null,
    override: Language? = null,
    loading: Boolean = false,
  ) = TranslateUiState(
    sourceText = text,
    targetLanguage = chinese,
    sourceOverride = override,
    result = result,
    isLoading = loading,
  )

  @Test
  fun `cannot translate an empty or blank field`() {
    assertFalse(state("").canTranslate)
    assertFalse(state("   ").canTranslate)
  }

  @Test
  fun `can translate once text is present`() {
    assertTrue(state("hello").canTranslate)
  }

  @Test
  fun `cannot translate while a request is in flight`() {
    assertFalse(state("hello", loading = true).canTranslate)
  }

  @Test
  fun `flags text beyond the character limit`() {
    val atLimit = state("a".repeat(MAX_SOURCE_LENGTH))
    val overLimit = state("a".repeat(MAX_SOURCE_LENGTH + 1))

    assertFalse(atLimit.isTooLong)
    assertTrue(atLimit.canTranslate)
    assertTrue(overLimit.isTooLong)
    assertFalse(overLimit.canTranslate)
  }

  @Test
  fun `warns when the detected source equals the target`() {
    val sameLanguage =
      TranslateResultState(text = "hello", sourceLanguage = chinese, sourceLanguageLabel = "Chinese", latencyMs = 1, savedRecordId = null)
    assertTrue(state("你好", result = sameLanguage).sameLanguageWarning)
  }

  @Test
  fun `does not warn for different languages`() {
    val different =
      TranslateResultState(text = "你好", sourceLanguage = english, sourceLanguageLabel = "English", latencyMs = 1, savedRecordId = null)
    assertFalse(state("hello", result = different).sameLanguageWarning)
  }

  @Test
  fun `does not warn before anything has been detected`() {
    assertFalse(state("hello").sameLanguageWarning)
  }

  @Test
  fun `manual source override drives the warning`() {
    assertTrue(state("hello", override = chinese).sameLanguageWarning)
    assertFalse(state("hello", override = english).sameLanguageWarning)
  }
}
