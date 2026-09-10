package com.lingua.app.domain

import java.util.Locale

/**
 * A language the app can detect or translate into.
 *
 * [code] is a BCP-47-ish primary subtag (`en`, `zh`, `pt`). [nativeName] is shown to the user in the
 * picker; [englishName] is what gets injected into the prompt, which keeps model behaviour stable
 * regardless of the app's display language.
 */
data class Language(
  val code: String,
  val englishName: String,
  val nativeName: String,
) {
  /** Stable tag used as the persisted target-language value. */
  val tag: String get() = code

  companion object {
    /** Sentinel used by the source-language selector. */
    val Auto = Language(code = "auto", englishName = "Auto detect", nativeName = "自动检测")
  }
}

object LanguageCatalog {

  val languages: List<Language> =
    listOf(
      Language("zh", "Chinese (Simplified)", "简体中文"),
      Language("zh-TW", "Chinese (Traditional)", "繁體中文"),
      Language("en", "English", "English"),
      Language("ja", "Japanese", "日本語"),
      Language("ko", "Korean", "한국어"),
      Language("fr", "French", "Français"),
      Language("de", "German", "Deutsch"),
      Language("es", "Spanish", "Español"),
      Language("pt", "Portuguese", "Português"),
      Language("it", "Italian", "Italiano"),
      Language("ru", "Russian", "Русский"),
      Language("ar", "Arabic", "العربية"),
      Language("hi", "Hindi", "हिन्दी"),
      Language("th", "Thai", "ไทย"),
      Language("vi", "Vietnamese", "Tiếng Việt"),
      Language("id", "Indonesian", "Bahasa Indonesia"),
      Language("ms", "Malay", "Bahasa Melayu"),
      Language("tr", "Turkish", "Türkçe"),
      Language("nl", "Dutch", "Nederlands"),
      Language("pl", "Polish", "Polski"),
      Language("sv", "Swedish", "Svenska"),
      Language("da", "Danish", "Dansk"),
      Language("no", "Norwegian", "Norsk"),
      Language("fi", "Finnish", "Suomi"),
      Language("cs", "Czech", "Čeština"),
      Language("sk", "Slovak", "Slovenčina"),
      Language("hu", "Hungarian", "Magyar"),
      Language("ro", "Romanian", "Română"),
      Language("bg", "Bulgarian", "Български"),
      Language("el", "Greek", "Ελληνικά"),
      Language("uk", "Ukrainian", "Українська"),
      Language("he", "Hebrew", "עברית"),
      Language("fa", "Persian", "فارسی"),
      Language("ur", "Urdu", "اردو"),
      Language("bn", "Bengali", "বাংলা"),
      Language("ta", "Tamil", "தமிழ்"),
      Language("te", "Telugu", "తెలుగు"),
      Language("mr", "Marathi", "मराठी"),
      Language("sw", "Swahili", "Kiswahili"),
      Language("fil", "Filipino", "Filipino"),
      Language("km", "Khmer", "ភាសាខ្មែរ"),
      Language("lo", "Lao", "ລາວ"),
      Language("my", "Burmese", "မြန်မာ"),
      Language("si", "Sinhala", "සිංහල"),
      Language("ne", "Nepali", "नेपाली"),
      Language("mn", "Mongolian", "Монгол"),
      Language("ka", "Georgian", "ქართული"),
      Language("hy", "Armenian", "Հայերեն"),
      Language("az", "Azerbaijani", "Azərbaycan"),
      Language("kk", "Kazakh", "Қазақша"),
      Language("uz", "Uzbek", "Oʻzbek"),
      Language("sr", "Serbian", "Српски"),
      Language("hr", "Croatian", "Hrvatski"),
      Language("sl", "Slovenian", "Slovenščina"),
      Language("lt", "Lithuanian", "Lietuvių"),
      Language("lv", "Latvian", "Latviešu"),
      Language("et", "Estonian", "Eesti"),
      Language("is", "Icelandic", "Íslenska"),
      Language("ga", "Irish", "Gaeilge"),
      Language("ca", "Catalan", "Català"),
      Language("eu", "Basque", "Euskara"),
      Language("gl", "Galician", "Galego"),
      Language("af", "Afrikaans", "Afrikaans"),
      Language("am", "Amharic", "አማርኛ"),
      Language("es-419", "Spanish (Latin America)", "Español (Latinoamérica)"),
    )

  /** Every language except the auto-detect sentinel: valid translation targets. */
  val targets: List<Language> = languages

  private val byCode: Map<String, Language> = languages.associateBy { it.code.lowercase() }

  private val byName: Map<String, Language> =
    buildMap {
      languages.forEach { language ->
        put(language.englishName.lowercase(), language)
        put(language.nativeName.lowercase(), language)
        put(language.code.lowercase(), language)
      }
    }

  fun byCodeOrNull(code: String?): Language? = code?.let { byCode[it.lowercase()] }

  fun targetOrNull(code: String?): Language? = byCodeOrNull(code)

  /**
   * Best-effort mapping from a detected language label returned by the model (an English name, a
   * native name, or a language code) onto a catalog entry.
   */
  fun matchByLabel(label: String?): Language? {
    val cleaned = label?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    byName[cleaned]?.let { return it }
    val primary = cleaned.substringBefore('-').substringBefore('_')
    return byCode[cleaned] ?: byCode[primary]
  }

  /** Picks the default target language from a locale tag such as `zh-Hans-CN`. */
  fun matchByLocale(localeTag: String?): Language? {
    val tag = localeTag?.takeIf { it.isNotBlank() } ?: return null
    val locale = Locale.forLanguageTag(tag)
    val language = locale.language.lowercase().ifEmpty { return null }
    val script = locale.script
    val region = locale.country.uppercase()

    if (language == "zh") {
      val traditional = script.equals("Hant", ignoreCase = true) || region in setOf("TW", "HK", "MO")
      return if (traditional) byCode["zh-tw"] else byCode["zh"]
    }
    if (language == "pt" && region == "BR") return byCode["pt"]
    if (language == "es" && region in setOf("MX", "AR", "CO", "CL", "PE", "VE", "UY", "EC", "BO", "PY", "CR", "CU", "DO", "GT", "HN", "NI", "PA", "PR", "SV")) {
      return byCode["es-419"]
    }
    return byCode[language]
  }

  /** Case-insensitive search over code, English and native names. */
  fun search(query: String): List<Language> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return languages
    return languages.filter {
      it.code.lowercase().contains(needle) ||
        it.englishName.lowercase().contains(needle) ||
        it.nativeName.lowercase().contains(needle)
    }
  }
}
