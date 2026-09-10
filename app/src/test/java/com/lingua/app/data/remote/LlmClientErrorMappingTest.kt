package com.lingua.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Error classification for HTTP responses, including the "key not filled in yet" case. */
class LlmClientErrorMappingTest {

  private val client = LlmClient()
  private val unauthorizedBody = """{"error":{"message":"Authentication Fails, Your api key is invalid","type":"authentication_error"}}"""

  @Test
  fun `401 with a blank key asks the user to fill the key in`() {
    val error = client.httpError(401, unauthorizedBody, keyIsBlank = true)
    assertEquals(LlmError.MissingApiKey, error)
  }

  @Test
  fun `401 with a key present is reported as a rejection carrying the api message`() {
    val error = client.httpError(401, unauthorizedBody, keyIsBlank = false)
    assertTrue(error is LlmError.Unauthorized)
    assertEquals("Authentication Fails, Your api key is invalid", (error as LlmError.Unauthorized).body)
  }

  @Test
  fun `403 with a blank key behaves like a missing key`() {
    assertEquals(LlmError.MissingApiKey, client.httpError(403, "", keyIsBlank = true))
  }

  @Test
  fun `keyless endpoints are unaffected by the blank-key rule`() {
    assertEquals(LlmError.ServerError::class, client.httpError(500, "boom", keyIsBlank = true)::class)
    assertTrue(client.httpError(404, "nope", keyIsBlank = true) is LlmError.ModelOrEndpointNotFound)
  }

  @Test
  fun `status codes map to their own error types`() {
    assertTrue(client.httpError(404, "{}") is LlmError.ModelOrEndpointNotFound)
    assertTrue(client.httpError(429, "{}") is LlmError.RateLimited)
    assertTrue(client.httpError(400, "{}") is LlmError.BadRequest)
    assertTrue(client.httpError(503, "{}") is LlmError.ServerError)
  }
}
