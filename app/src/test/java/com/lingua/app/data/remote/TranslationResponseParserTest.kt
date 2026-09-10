package com.lingua.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationResponseParserTest {

  @Test
  fun `parses a clean json answer`() {
    val parsed =
      TranslationResponseParser.parse(
        """{"source_language":"English","source_language_code":"en","translation":"你好，世界！"}"""
      )
    assertEquals("你好，世界！", parsed?.translation)
    assertEquals("English", parsed?.sourceLanguageName)
    assertEquals("en", parsed?.sourceLanguageCode)
  }

  @Test
  fun `parses json wrapped in markdown fences`() {
    val parsed =
      TranslationResponseParser.parse(
        "```json\n{\"source_language\":\"Japanese\",\"translation\":\"こんにちは\"}\n```"
      )
    assertEquals("こんにちは", parsed?.translation)
    assertEquals("Japanese", parsed?.sourceLanguageName)
  }

  @Test
  fun `parses json surrounded by prose`() {
    val parsed =
      TranslationResponseParser.parse(
        "Sure! Here you go:\n{\"source_language\":\"French\",\"translation\":\"Bonjour\"}\nHope that helps."
      )
    assertEquals("Bonjour", parsed?.translation)
  }

  @Test
  fun `accepts alternate key names`() {
    assertEquals("Hallo", TranslationResponseParser.parse("""{"translated_text":"Hallo"}""")?.translation)
    assertEquals("Hallo", TranslationResponseParser.parse("""{"translatedText":"Hallo"}""")?.translation)
    assertEquals("Hallo", TranslationResponseParser.parse("""{"result":"Hallo"}""")?.translation)
  }

  @Test
  fun `falls back to raw text when the model ignores the json instruction`() {
    val parsed = TranslationResponseParser.parse("Bonjour le monde")
    assertEquals("Bonjour le monde", parsed?.translation)
    assertNull(parsed?.sourceLanguageName)
  }

  @Test
  fun `falls back to raw text when the json has no translation key`() {
    val parsed = TranslationResponseParser.parse("""{"note":"I cannot help with that"}""")
    assertEquals("""{"note":"I cannot help with that"}""", parsed?.translation)
  }

  @Test
  fun `keeps multi line translations intact`() {
    val parsed = TranslationResponseParser.parse("""{"translation":"line one\nline two"}""")
    assertEquals("line one\nline two", parsed?.translation)
  }

  @Test
  fun `returns null for blank content`() {
    assertNull(TranslationResponseParser.parse(""))
    assertNull(TranslationResponseParser.parse("   \n  "))
    assertNull(TranslationResponseParser.parse(null))
  }

  @Test
  fun `ignores non string translation values`() {
    val parsed = TranslationResponseParser.parse("""{"translation":{"nested":"nope"}}""")
    assertEquals("""{"translation":{"nested":"nope"}}""", parsed?.translation)
  }
}
