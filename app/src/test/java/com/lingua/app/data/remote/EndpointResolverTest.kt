package com.lingua.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointResolverTest {

  @Test
  fun `appends chat completions to a bare host`() {
    assertEquals("https://api.deepseek.com/chat/completions", EndpointResolver.chatCompletions("https://api.deepseek.com"))
  }

  @Test
  fun `keeps the v1 segment`() {
    assertEquals("https://api.openai.com/v1/chat/completions", EndpointResolver.chatCompletions("https://api.openai.com/v1"))
  }

  @Test
  fun `trims trailing slashes and whitespace`() {
    assertEquals("http://192.168.1.9:11434/v1/chat/completions", EndpointResolver.chatCompletions("  http://192.168.1.9:11434/v1/  "))
  }

  @Test
  fun `uses a full endpoint url verbatim`() {
    val azure =
      "https://example.openai.azure.com/openai/deployments/gpt/chat/completions?api-version=2024-02-01"
    assertEquals(azure, EndpointResolver.chatCompletions(azure))
  }

  @Test
  fun `derives models endpoint from a base url`() {
    assertEquals("https://api.openai.com/v1/models", EndpointResolver.models("https://api.openai.com/v1"))
  }

  @Test
  fun `derives models endpoint from a full chat url`() {
    assertEquals(
      "https://example.com/v1/models",
      EndpointResolver.models("https://example.com/v1/chat/completions"),
    )
  }

  @Test
  fun `does not duplicate the models suffix`() {
    assertEquals("https://example.com/v1/models", EndpointResolver.models("https://example.com/v1/models"))
  }

  @Test
  fun `empty base url yields empty endpoints`() {
    assertEquals("", EndpointResolver.chatCompletions("   "))
    assertEquals("", EndpointResolver.models(""))
  }

  @Test
  fun `validates base urls`() {
    assertTrue(EndpointResolver.isValidBaseUrl("https://api.openai.com/v1"))
    assertTrue(EndpointResolver.isValidBaseUrl("http://192.168.240.1:8765/v1"))
    assertFalse(EndpointResolver.isValidBaseUrl("api.openai.com"))
    assertFalse(EndpointResolver.isValidBaseUrl("ftp://api.openai.com"))
    assertFalse(EndpointResolver.isValidBaseUrl(""))
    assertFalse(EndpointResolver.isValidBaseUrl("https://"))
    assertFalse(EndpointResolver.isValidBaseUrl("https://:8080/v1"))
  }
}
