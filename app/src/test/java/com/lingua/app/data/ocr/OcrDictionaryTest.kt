package com.lingua.app.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDictionaryTest {

  private val dict = (1..18708).map { "c$it" }

  @Test
  fun `reserves class zero for the blank and appends the space class`() {
    val characters = OcrDictionary.characters(dict, classCount = 18710)

    assertEquals(18710, characters.size)
    assertEquals(null, characters[0])
    assertEquals("c1", characters[1])
    assertEquals("c18708", characters[18708])
    assertEquals(" ", characters[18709])
  }

  @Test
  fun `accepts a graph without the extra space class`() {
    val characters = OcrDictionary.characters(dict, classCount = 18709)

    assertEquals(18709, characters.size)
    assertEquals("c18708", characters.last())
  }

  @Test
  fun `rejects a class count that cannot be aligned with the dictionary`() {
    val error = runCatching { OcrDictionary.characters(dict, classCount = 100) }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }
}

class OcrScriptCoverageTest {

  private val dictFile =
    listOf(
      java.io.File("src/main/assets/ocr/ppocrv6_dict.txt"),
      java.io.File("app/src/main/assets/ocr/ppocrv6_dict.txt"),
    ).firstOrNull { it.exists() }

  @Test
  fun `claims match what the dictionary actually contains`() {
    val dict = requireNotNull(dictFile) { "ppocrv6_dict.txt not found" }.readLines()
    fun has(predicate: (Char) -> Boolean) = dict.any { line -> line.length == 1 && predicate(line[0]) }

    assertTrue("CJK", has { it.code in 0x4E00..0x9FFF })
    assertTrue("kana", has { it.code in 0x3040..0x30FF })
    assertTrue("greek", has { it.code in 0x0370..0x03FF })
    assertTrue("latin", has { it in 'a'..'z' })
    assertFalse("hangul", has { it.code in 0xAC00..0xD7A3 })
    assertFalse("cyrillic", has { it.code in 0x0400..0x04FF })
    assertFalse("arabic", has { it.code in 0x0600..0x06FF })
    assertFalse("thai", has { it.code in 0x0E00..0x0E7F })
  }

  @Test
  fun `flags the languages the dictionary cannot read`() {
    listOf("ko", "ru", "ar", "hi", "th", "he", "bn", "uk", "kk", "sr", "am").forEach { code ->
      assertFalse(code, OcrScriptCoverage.supports(code))
    }
  }

  @Test
  fun `accepts the languages it can read`() {
    listOf("auto", "zh", "zh-TW", "ja", "en", "fr", "vi", "tr", "el", "es-419", "fil").forEach { code ->
      assertTrue(code, OcrScriptCoverage.supports(code))
    }
    assertTrue("unknown codes must not block the user", OcrScriptCoverage.supports(null))
  }

  @Test
  fun `every catalog language is classified`() {
    val classified = OcrScriptCoverage.supportedLanguageCodes
    val unclassified = com.lingua.app.domain.LanguageCatalog.languages
      .map { it.code.lowercase() }
      .filter { code -> code !in classified && code !in knownUnsupported }

    assertEquals(emptyList<String>(), unclassified)
  }

  private val knownUnsupported =
    setOf(
      "ko", "ru", "ar", "hi", "th", "bg", "uk", "he", "fa", "ur", "bn", "ta", "te", "mr", "km", "lo",
      "my", "si", "ne", "mn", "ka", "hy", "kk", "sr", "am",
    )
}
