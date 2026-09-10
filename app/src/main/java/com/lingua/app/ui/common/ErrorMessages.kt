package com.lingua.app.ui.common

import androidx.annotation.StringRes
import com.lingua.app.R
import com.lingua.app.data.remote.LlmError
import com.lingua.app.domain.TranslationRepository

/** A localized error message: a string resource plus its format arguments. */
data class ErrorMessage(@param:StringRes val resId: Int, val args: List<Any> = emptyList())

/**
 * Maps failures onto user-facing copy. Kept free of Android framework types (only string resource
 * ids) so it can be unit tested on the JVM.
 */
object LlmErrorMapper {

  fun toMessage(error: Throwable): ErrorMessage =
    when (error) {
      TranslationRepository.NoActiveProfile -> ErrorMessage(R.string.error_no_profile)
      is LlmError.MissingApiKey -> ErrorMessage(R.string.error_missing_key)
      is LlmError.InvalidUrl -> ErrorMessage(R.string.error_invalid_url)
      is LlmError.Unauthorized -> ErrorMessage(R.string.error_unauthorized, listOf(error.code))
      is LlmError.ModelOrEndpointNotFound -> ErrorMessage(R.string.error_not_found, listOf(error.code))
      is LlmError.BadRequest -> ErrorMessage(R.string.error_bad_request, listOf(error.code))
      is LlmError.RateLimited -> ErrorMessage(R.string.error_rate_limited)
      is LlmError.ServerError -> ErrorMessage(R.string.error_server, listOf(error.code))
      is LlmError.Timeout -> ErrorMessage(R.string.error_timeout)
      is LlmError.Network -> ErrorMessage(R.string.error_network)
      is LlmError.InvalidResponse -> ErrorMessage(R.string.error_invalid_response)
      is LlmError.Cancelled -> ErrorMessage(R.string.error_cancelled)
      else -> ErrorMessage(R.string.error_unknown)
    }

  /** Raw payload (or transport reason) shown in the collapsible "details" section, if any. */
  fun detailOf(error: Throwable): String? =
    when (error) {
      is LlmError.Unauthorized -> error.body
      is LlmError.ModelOrEndpointNotFound -> error.body
      is LlmError.BadRequest -> error.body
      is LlmError.RateLimited -> error.body
      is LlmError.ServerError -> error.body
      is LlmError.Network -> error.reason
      is LlmError.InvalidResponse -> error.raw
      else -> null
    }?.takeIf { it.isNotBlank() }
}
