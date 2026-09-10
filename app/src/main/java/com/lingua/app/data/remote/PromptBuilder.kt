package com.lingua.app.data.remote

import com.lingua.app.domain.Language

/**
 * Builds the system prompt. The model is asked for a single JSON object so that source-language
 * detection and the translation arrive in one round trip (no separate detection request, no
 * per-provider detection API).
 */
object PromptBuilder {

  fun systemPrompt(target: Language, sourceOverride: Language? = null): String {
    val sourceLine =
      if (sourceOverride == null) {
        "Detect the source language yourself."
      } else {
        "The source language is ${sourceOverride.englishName}; do not second-guess it."
      }

    return """
      You are a precise professional translator.
      Translate the user's text into ${target.englishName} (${target.nativeName}).
      $sourceLine

      Reply with a single JSON object and nothing else — no markdown fences, no commentary:
      {
        "source_language": "<English name of the source language>",
        "source_language_code": "<ISO 639-1 code, or \"unknown\">",
        "translation": "<the translated text>"
      }

      Rules:
      1. The "translation" value must contain only the translation of the user's text.
      2. Preserve the original structure: line breaks, lists, numbering and placeholders.
      3. Keep code, URLs, e-mail addresses, variable names and proper nouns untranslated.
      4. Translate idiomatically rather than word for word.
      5. If the text is already in ${target.englishName}, repeat it unchanged.
      6. Never refuse and never explain; if the text is a fragment, translate the fragment.
    """.trimIndent()
  }
}
