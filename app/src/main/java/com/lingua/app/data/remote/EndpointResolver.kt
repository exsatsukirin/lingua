package com.lingua.app.data.remote

/**
 * Turns a user-entered base URL into a concrete endpoint.
 *
 * The rule is intentionally simple and documented in the settings UI:
 *  - trailing slashes are trimmed;
 *  - a URL that already points at `/chat/completions` is used verbatim (lets users paste an
 *    Azure-style deployment URL);
 *  - otherwise `/chat/completions` is appended, so `https://api.openai.com/v1` and
 *    `https://api.deepseek.com` both work.
 */
object EndpointResolver {

  private const val CHAT_SUFFIX = "/chat/completions"
  private const val MODELS_SUFFIX = "/models"

  fun chatCompletions(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    val (path, suffix) = splitQuery(trimmed)
    return if (path.endsWith(CHAT_SUFFIX)) path + suffix else path + CHAT_SUFFIX + suffix
  }

  fun models(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    val (path, suffix) = splitQuery(trimmed)
    return when {
      path.endsWith(CHAT_SUFFIX) -> path.removeSuffix(CHAT_SUFFIX) + MODELS_SUFFIX + suffix
      path.endsWith(MODELS_SUFFIX) -> path + suffix
      else -> path + MODELS_SUFFIX + suffix
    }
  }

  /** Splits `…/chat/completions?api-version=1` into its path and `?…` remainder. */
  private fun splitQuery(url: String): Pair<String, String> {
    val queryIndex = url.indexOf('?')
    return if (queryIndex < 0) url to "" else url.substring(0, queryIndex) to url.substring(queryIndex)
  }

  /** True when [baseUrl] looks like something OkHttp can actually dial. */
  fun isValidBaseUrl(baseUrl: String): Boolean {
    val trimmed = baseUrl.trim()
    if (trimmed.isEmpty()) return false
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
    val withoutScheme = trimmed.substringAfter("://")
    val authority = withoutScheme.substringBefore('/')
    return authority.isNotBlank() && !authority.startsWith(":") && " " !in authority
  }
}
