package com.lingua.app.data.remote

/** Everything that can go wrong while talking to a user-configured endpoint. */
sealed class LlmError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
  /** No API key configured for the active profile. */
  data object MissingApiKey : LlmError()

  /** The configured base URL could not be turned into a valid HTTP request. */
  data object InvalidUrl : LlmError()

  /** HTTP 401 / 403 — bad or missing credentials. */
  data class Unauthorized(val code: Int, val body: String?) : LlmError("HTTP $code")

  /** HTTP 404 — wrong base URL, wrong path, or unknown model. */
  data class ModelOrEndpointNotFound(val code: Int, val body: String?) : LlmError("HTTP $code")

  /** HTTP 400 / 422 — the endpoint rejected the payload. */
  data class BadRequest(val code: Int, val body: String?) : LlmError("HTTP $code")

  /** HTTP 429. */
  data class RateLimited(val code: Int, val body: String?) : LlmError("HTTP $code")

  /** HTTP 5xx. */
  data class ServerError(val code: Int, val body: String?) : LlmError("HTTP $code")

  /** Connect/read/call timeout. */
  data object Timeout : LlmError()

  /** DNS failure, TLS failure, connection refused, no network. */
  data class Network(val reason: String?) : LlmError(reason)

  /** The endpoint answered, but the payload could not be understood. */
  data class InvalidResponse(val raw: String?) : LlmError(raw)

  /** The request was cancelled (new request superseded it, or the screen went away). */
  data object Cancelled : LlmError()
}

/** Upper bound on the raw payload kept for the "details" expander. */
internal fun String?.truncateForDisplay(max: Int = 2000): String? {
  if (this == null) return null
  return if (length <= max) this else take(max) + "…"
}
