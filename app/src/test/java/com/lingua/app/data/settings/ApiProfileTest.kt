package com.lingua.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiProfileTest {

  private fun profile(
    id: String = "p1",
    name: String = "DeepSeek",
    baseUrl: String = "https://api.deepseek.com/v1",
    model: String = "deepseek-chat",
    apiKey: String = "sk-secret",
  ) = ApiProfile(id = id, name = name, baseUrl = baseUrl, model = model, apiKey = apiKey)

  @Test
  fun `withoutSecret strips the api key and keeps everything else`() {
    val stripped = profile().withoutSecret()
    assertEquals("", stripped.apiKey)
    assertEquals("p1", stripped.id)
    assertEquals("https://api.deepseek.com/v1", stripped.baseUrl)
    assertEquals("deepseek-chat", stripped.model)
  }

  @Test
  fun `display name falls back through name model and base url`() {
    assertEquals("DeepSeek", profile().displayName)
    assertEquals("deepseek-chat", profile(name = "").displayName)
    assertEquals("https://api.deepseek.com/v1", profile(name = "", model = "").displayName)
  }

  @Test
  fun `defaults are conservative for translation`() {
    val defaults = ApiProfile(id = "x", name = "n", baseUrl = "u", model = "m")
    assertEquals(0.2, defaults.temperature, 0.0001)
    assertEquals(60, defaults.timeoutSeconds)
    assertTrue(defaults.jsonMode)
    assertTrue(defaults.extraHeaders.isEmpty())
    assertEquals("", defaults.apiKey)
  }

  @Test
  fun `active profile resolution handles missing and dangling ids`() {
    val a = profile(id = "a", name = "A")
    val b = profile(id = "b", name = "B")

    assertEquals("a", AppSettings(profiles = listOf(a, b), activeProfileId = "a").activeProfile?.id)
    assertEquals("a", AppSettings(profiles = listOf(a, b), activeProfileId = "gone").activeProfile?.id)
    assertEquals("a", AppSettings(profiles = listOf(a, b), activeProfileId = null).activeProfile?.id)
    assertNull(AppSettings().activeProfile)
  }

  @Test
  fun `settings defaults follow the system and keep history`() {
    val settings = AppSettings()
    assertEquals(ThemeMode.System, settings.themeMode)
    assertNull(settings.targetLanguageCode)
    assertTrue(settings.autoSaveHistory)
    assertTrue(settings.dynamicColor)
    assertFalse(settings.historySearchFavoritesOnly)
  }

  @Test
  fun `theme mode parsing is tolerant`() {
    assertEquals(ThemeMode.Dark, ThemeMode.fromStorage("dark"))
    assertEquals(ThemeMode.Light, ThemeMode.fromStorage("LIGHT"))
    assertEquals(ThemeMode.System, ThemeMode.fromStorage("nonsense"))
    assertEquals(ThemeMode.System, ThemeMode.fromStorage(null))
  }
}
