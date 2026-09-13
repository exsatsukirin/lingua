package com.lingua.app.data.ocr

/**
 * Which of the app's languages the bundled PP-OCRv6 dictionary can actually read.
 *
 * The dictionary holds 18708 characters — CJK, kana, Latin (with diacritics) and Greek — and no
 * Hangul, Cyrillic, Arabic, Thai or Devanagari at all, so "50 languages" upstream means 50
 * Latin-script languages plus Chinese and Japanese. Screen OCR has to say so instead of returning
 * garbage for, say, a Korean screenshot.
 */
object OcrScriptCoverage {

  /** Core languages whose scripts the dictionary covers. Codes are lower-case, as compared. */
  private val CORE = setOf("zh", "zh-tw", "ja", "el")

  /** Languages written in the Latin alphabet, which the dictionary covers fully. */
  private val LATIN =
    setOf(
      "en", "fr", "de", "es", "es-419", "pt", "it", "vi", "id", "ms", "tr", "nl", "pl", "sv", "da",
      "no", "fi", "cs", "sk", "hu", "ro", "sw", "fil", "az", "uz", "hr", "sl", "lt", "lv", "et",
      "is", "ga", "ca", "eu", "gl", "af",
    )

  val supportedLanguageCodes: Set<String> = CORE + LATIN

  fun supports(code: String?): Boolean {
    val normalized = code?.trim()?.lowercase() ?: return true
    if (normalized == "auto") return true
    return normalized in supportedLanguageCodes
  }
}
