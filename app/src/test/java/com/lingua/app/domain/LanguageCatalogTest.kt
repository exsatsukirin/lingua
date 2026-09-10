package com.lingua.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageCatalogTest {

  @Test
  fun `codes are unique`() {
    val codes = LanguageCatalog.languages.map { it.code }
    assertEquals(codes.size, codes.toSet().size)
  }

  @Test
  fun `no catalog entry collides with the auto sentinel`() {
    assertTrue(LanguageCatalog.languages.none { it.code == Language.Auto.code })
  }

  @Test
  fun `resolves simplified chinese from a hans locale`() {
    assertEquals("zh", LanguageCatalog.matchByLocale("zh-Hans-CN")?.code)
    assertEquals("zh", LanguageCatalog.matchByLocale("zh-CN")?.code)
  }

  @Test
  fun `resolves traditional chinese from taiwan and hong kong`() {
    assertEquals("zh-TW", LanguageCatalog.matchByLocale("zh-Hant-TW")?.code)
    assertEquals("zh-TW", LanguageCatalog.matchByLocale("zh-HK")?.code)
  }

  @Test
  fun `resolves plain languages`() {
    assertEquals("en", LanguageCatalog.matchByLocale("en-US")?.code)
    assertEquals("ja", LanguageCatalog.matchByLocale("ja-JP")?.code)
    assertEquals("de", LanguageCatalog.matchByLocale("de")?.code)
  }

  @Test
  fun `resolves latin american spanish`() {
    assertEquals("es-419", LanguageCatalog.matchByLocale("es-MX")?.code)
    assertEquals("es", LanguageCatalog.matchByLocale("es-ES")?.code)
  }

  @Test
  fun `returns null for unknown locales`() {
    assertNull(LanguageCatalog.matchByLocale("xx-YY"))
    assertNull(LanguageCatalog.matchByLocale(""))
    assertNull(LanguageCatalog.matchByLocale(null))
  }

  @Test
  fun `matches model labels by english name native name or code`() {
    assertEquals("zh", LanguageCatalog.matchByLabel("Chinese (Simplified)")?.code)
    assertEquals("zh", LanguageCatalog.matchByLabel("简体中文")?.code)
    assertEquals("ja", LanguageCatalog.matchByLabel("ja")?.code)
    assertEquals("en", LanguageCatalog.matchByLabel("ENGLISH")?.code)
    assertNotNull(LanguageCatalog.matchByLabel("zh-Hans"))
    assertNull(LanguageCatalog.matchByLabel("Klingon"))
    assertNull(LanguageCatalog.matchByLabel(null))
  }

  @Test
  fun `search matches code english and native names`() {
    assertTrue(LanguageCatalog.search("deu").any { it.code == "de" })
    assertTrue(LanguageCatalog.search("日本").any { it.code == "ja" })
    assertTrue(LanguageCatalog.search("fr").any { it.code == "fr" })
    assertEquals(LanguageCatalog.languages.size, LanguageCatalog.search("").size)
    assertTrue(LanguageCatalog.search("zzzz").isEmpty())
  }

  @Test
  fun `byCode is case insensitive`() {
    assertEquals("en", LanguageCatalog.byCodeOrNull("EN")?.code)
    assertNull(LanguageCatalog.byCodeOrNull("nope"))
  }
}
