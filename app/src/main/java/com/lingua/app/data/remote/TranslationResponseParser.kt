package com.lingua.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** What the model told us about a translation. */
data class ParsedTranslation(
  val translation: String,
  val sourceLanguageName: String?,
  val sourceLanguageCode: String?,
)

/**
 * Extracts a translation from model output.
 *
 * Real endpoints are sloppy: they wrap JSON in markdown fences, prepend "Sure, here you go", use
 * their own key names, or ignore the JSON instruction entirely. Each of those is handled, and a
 * plausible plain-text answer is still returned as the translation rather than being thrown away.
 */
object TranslationResponseParser {

  private val TRANSLATION_KEYS =
    listOf("translation", "translated_text", "translatedText", "target_text", "targetText", "text", "result", "output")

  private val LANGUAGE_NAME_KEYS =
    listOf("source_language", "sourceLanguage", "detected_language", "detectedLanguage", "source_lang", "source")

  private val LANGUAGE_CODE_KEYS =
    listOf("source_language_code", "sourceLanguageCode", "detected_language_code", "source_code", "source_lang_code")

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  fun parse(content: String?): ParsedTranslation? {
    val trimmed = content?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    extractJsonObject(trimmed)?.let { obj ->
      val translation = firstString(obj, TRANSLATION_KEYS)
      if (!translation.isNullOrBlank()) {
        return ParsedTranslation(
          translation = translation.trim(),
          sourceLanguageName = firstString(obj, LANGUAGE_NAME_KEYS)?.trim()?.takeIf { it.isNotEmpty() },
          sourceLanguageCode = firstString(obj, LANGUAGE_CODE_KEYS)?.trim()?.takeIf { it.isNotEmpty() },
        )
      }
    }

    // No usable JSON: treat the whole answer as the translation, minus any markdown fence.
    val plain = stripFences(trimmed)
    return plain.takeIf { it.isNotBlank() }?.let { ParsedTranslation(it, null, null) }
  }

  private fun extractJsonObject(text: String): JsonObject? {
    decodeObject(text)?.let { return it }
    decodeObject(stripFences(text))?.let { return it }

    // Prose before/after the JSON object.
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start in 0 until end) {
      decodeObject(text.substring(start, end + 1))?.let { return it }
    }
    return null
  }

  private fun decodeObject(candidate: String): JsonObject? =
    runCatching { json.parseToJsonElement(candidate).jsonObject }.getOrNull()

  private fun stripFences(text: String): String {
    val withoutOpening = text
      .removePrefix("```json")
      .removePrefix("```JSON")
      .removePrefix("```")
    return withoutOpening.removeSuffix("```").trim()
  }

  private fun firstString(obj: JsonObject, keys: List<String>): String? {
    for (key in keys) {
      val element = obj[key] ?: continue
      val primitive = element as? JsonPrimitive ?: continue
      if (primitive.isString) return primitive.content
    }
    return null
  }
}
