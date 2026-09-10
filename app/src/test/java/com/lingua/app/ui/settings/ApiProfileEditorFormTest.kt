package com.lingua.app.ui.settings

import com.lingua.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiProfileEditorFormTest {

  @Test
  fun `parses simple header lines`() {
    val headers = ApiProfileEditorViewModel.parseHeaders("X-Api-Version: 2024-02-01\nX-Trace: abc")
    assertEquals(2, headers.size)
    assertEquals("X-Api-Version", headers[0].name)
    assertEquals("2024-02-01", headers[0].value)
    assertEquals("abc", headers[1].value)
  }

  @Test
  fun `ignores blank and malformed lines`() {
    val headers = ApiProfileEditorViewModel.parseHeaders("\n\nno-colon-here\n: empty-name\nValid: yes\n")
    assertEquals(1, headers.size)
    assertEquals("Valid", headers[0].name)
    assertEquals("yes", headers[0].value)
  }

  @Test
  fun `keeps colons that belong to the value`() {
    val headers = ApiProfileEditorViewModel.parseHeaders("Authorization: Bearer a:b:c")
    assertEquals("Authorization", headers[0].name)
    assertEquals("Bearer a:b:c", headers[0].value)
  }

  @Test
  fun `trims surrounding whitespace`() {
    val headers = ApiProfileEditorViewModel.parseHeaders("  X-Test :  spaced value  ")
    assertEquals("X-Test", headers[0].name)
    assertEquals("spaced value", headers[0].value)
  }

  @Test
  fun `validation reports which fields are wrong`() {
    assertTrue(ValidationErrors(name = true).hasErrors)
    assertTrue(ValidationErrors(baseUrl = true).hasErrors)
    assertTrue(ValidationErrors(model = true).hasErrors)
    assertFalse(ValidationErrors().hasErrors)
  }

  @Test
  fun `presets are recognised from a base url`() {
    assertEquals(ProviderPreset.OpenAI, ProviderPreset.forBaseUrl("https://api.openai.com/v1"))
    assertEquals(ProviderPreset.DeepSeek, ProviderPreset.forBaseUrl("https://api.deepseek.com/v1"))
    assertEquals(ProviderPreset.Ollama, ProviderPreset.forBaseUrl("http://192.168.1.2:11434/v1"))
    assertEquals(ProviderPreset.Custom, ProviderPreset.forBaseUrl("https://my-own-gateway.example.com/v1"))
    assertEquals(ProviderPreset.Custom, ProviderPreset.forBaseUrl(""))
  }

  @Test
  fun `every preset except custom prefills a usable endpoint`() {
    ProviderPreset.entries.forEach { preset ->
      if (preset == ProviderPreset.Custom) return@forEach
      assertTrue("${preset.name} base url", preset.baseUrl.startsWith("http"))
      assertTrue("${preset.name} model", preset.model.isNotBlank())
      assertTrue("${preset.name} label", preset.labelRes != 0)
    }
  }

  @Test
  fun `custom preset has no opinion`() {
    assertEquals("", ProviderPreset.Custom.baseUrl)
    assertEquals("", ProviderPreset.Custom.model)
    assertEquals(R.string.preset_custom, ProviderPreset.Custom.labelRes)
  }
}
