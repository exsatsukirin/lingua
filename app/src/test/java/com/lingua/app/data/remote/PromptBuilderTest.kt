package com.lingua.app.data.remote

import com.lingua.app.domain.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

  private val chinese = Language("zh", "Chinese (Simplified)", "简体中文")
  private val english = Language("en", "English", "English")

  @Test
  fun `injects the target language and its native name`() {
    val prompt = PromptBuilder.systemPrompt(chinese)
    assertTrue(prompt.contains("Chinese (Simplified)"))
    assertTrue(prompt.contains("简体中文"))
  }

  @Test
  fun `asks the model to detect the language by default`() {
    val prompt = PromptBuilder.systemPrompt(chinese)
    assertTrue(prompt.contains("Detect the source language yourself."))
    assertFalse(prompt.contains("do not second-guess it"))
  }

  @Test
  fun `honours an explicit source language override`() {
    val prompt = PromptBuilder.systemPrompt(chinese, english)
    assertTrue(prompt.contains("The source language is English; do not second-guess it."))
    assertFalse(prompt.contains("Detect the source language yourself."))
  }

  @Test
  fun `always asks for the json contract`() {
    val prompt = PromptBuilder.systemPrompt(chinese)
    assertTrue(prompt.contains("source_language_code"))
    assertTrue(prompt.contains("translation"))
  }
}
