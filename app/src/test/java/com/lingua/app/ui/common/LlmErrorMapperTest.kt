package com.lingua.app.ui.common

import com.lingua.app.R
import com.lingua.app.data.remote.LlmError
import com.lingua.app.domain.TranslationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmErrorMapperTest {

  @Test
  fun `maps status errors to dedicated copy`() {
    assertEquals(R.string.error_unauthorized, LlmErrorMapper.toMessage(LlmError.Unauthorized(401, null)).resId)
    assertEquals(R.string.error_not_found, LlmErrorMapper.toMessage(LlmError.ModelOrEndpointNotFound(404, null)).resId)
    assertEquals(R.string.error_bad_request, LlmErrorMapper.toMessage(LlmError.BadRequest(400, null)).resId)
    assertEquals(R.string.error_rate_limited, LlmErrorMapper.toMessage(LlmError.RateLimited(429, null)).resId)
    assertEquals(R.string.error_server, LlmErrorMapper.toMessage(LlmError.ServerError(503, null)).resId)
  }

  @Test
  fun `passes the http status through as a format argument`() {
    assertEquals(listOf(401), LlmErrorMapper.toMessage(LlmError.Unauthorized(401, null)).args)
    assertEquals(listOf(500), LlmErrorMapper.toMessage(LlmError.ServerError(500, null)).args)
    assertEquals(emptyList<Any>(), LlmErrorMapper.toMessage(LlmError.RateLimited(429, null)).args)
  }

  @Test
  fun `maps transport and configuration errors`() {
    assertEquals(R.string.error_timeout, LlmErrorMapper.toMessage(LlmError.Timeout).resId)
    assertEquals(R.string.error_network, LlmErrorMapper.toMessage(LlmError.Network("dns")).resId)
    assertEquals(R.string.error_missing_key, LlmErrorMapper.toMessage(LlmError.MissingApiKey).resId)
    assertEquals(R.string.error_invalid_url, LlmErrorMapper.toMessage(LlmError.InvalidUrl).resId)
    assertEquals(R.string.error_invalid_response, LlmErrorMapper.toMessage(LlmError.InvalidResponse("x")).resId)
    assertEquals(R.string.error_cancelled, LlmErrorMapper.toMessage(LlmError.Cancelled).resId)
  }

  @Test
  fun `maps an unconfigured app to its own hint`() {
    assertEquals(R.string.error_no_profile, LlmErrorMapper.toMessage(TranslationRepository.NoActiveProfile).resId)
  }

  @Test
  fun `unknown throwables fall back to a generic message`() {
    assertEquals(R.string.error_unknown, LlmErrorMapper.toMessage(IllegalStateException("boom")).resId)
  }

  @Test
  fun `exposes raw detail only when there is something to show`() {
    assertEquals("bad key", LlmErrorMapper.detailOf(LlmError.Unauthorized(401, "bad key")))
    assertEquals("dns failure", LlmErrorMapper.detailOf(LlmError.Network("dns failure")))
    assertNull(LlmErrorMapper.detailOf(LlmError.Unauthorized(401, "   ")))
    assertNull(LlmErrorMapper.detailOf(LlmError.Timeout))
  }
}
