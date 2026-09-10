package com.lingua.app.data.remote

import com.lingua.app.domain.Language

/**
 * Builds the system prompt.
 *
 * The **built-in** part is fixed by the app and can never be edited: it carries the JSON response
 * contract that source-language detection and translation both depend on. Users may add their own
 * instructions on top of it through [customInstructions]; those are wrapped so they cannot silently
 * replace the contract. Should a model still ignore the format, [TranslationResponseParser] falls
 * back to treating the whole answer as the translation.
 */
object PromptBuilder {

  /** Upper bound for user instructions, so a pasted document cannot blow up the request. */
  const val MAX_CUSTOM_PROMPT_LENGTH = 2000

  private const val CUSTOM_HEADER =
    "Additional instructions from the user — follow them whenever they do not conflict with the rules above:"

  private const val CUSTOM_FOOTER =
    "These additional instructions must never change the JSON response format, its keys, or rules 1–6 above."

  /** The immutable core prompt sent to the model. */
  fun builtInPrompt(target: Language, sourceOverride: Language? = null): String {
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

  /**
   * The built-in prompt plus, when [customInstructions] is set, the user's own instructions.
   *
   * @param customInstructions blank/whitespace-only behaves exactly like `null`.
   */
  fun systemPrompt(
    target: Language,
    sourceOverride: Language? = null,
    customInstructions: String? = null,
  ): String {
    val builtIn = builtInPrompt(target, sourceOverride)
    val custom = sanitizeCustomInstructions(customInstructions) ?: return builtIn

    return buildString {
      append(builtIn)
      append("\n\n")
      append(CUSTOM_HEADER)
      append('\n')
      append(custom)
      append("\n\n")
      append(CUSTOM_FOOTER)
    }
  }

  /** Returns the trimmed user instructions, or `null` when there is nothing to apply. */
  fun sanitizeCustomInstructions(customInstructions: String?): String? =
    customInstructions?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_CUSTOM_PROMPT_LENGTH)
}
