package com.lingua.app.data.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CtcDecoderTest {

  /** class 0 = blank, 1 = 'a', 2 = 'b', 3 = space — the shape PP-OCR's dictionary produces. */
  private val characters: List<String?> = listOf(null, "a", "b", " ")

  /** Builds [timeSteps] rows of one-hot probabilities over 4 classes. */
  private fun logits(vararg winners: Int): FloatArray {
    val classes = 4
    val data = FloatArray(winners.size * classes)
    winners.forEachIndexed { step, winner ->
      for (c in 0 until classes) data[step * classes + c] = if (c == winner) 0.9f else 0.033f
    }
    return data
  }

  @Test
  fun `collapses repeats and drops blanks`() {
    val data = logits(1, 1, 0, 2, 2, 0)

    val decoded = CtcDecoder.decode(data, batch = 1, timeSteps = 6, classes = 4, characters = characters)

    assertEquals("ab", decoded.single().text)
  }

  @Test
  fun `blank separates a repeated character`() {
    val data = logits(1, 0, 1)

    val decoded = CtcDecoder.decode(data, batch = 1, timeSteps = 3, classes = 4, characters = characters)

    assertEquals("aa", decoded.single().text)
  }

  @Test
  fun `maps the trailing space class`() {
    val data = logits(1, 3, 2)

    val decoded = CtcDecoder.decode(data, batch = 1, timeSteps = 3, classes = 4, characters = characters)

    assertEquals("a b", decoded.single().text)
  }

  @Test
  fun `reports the mean probability of emitted characters`() {
    val data = logits(1, 2)

    val decoded = CtcDecoder.decode(data, batch = 1, timeSteps = 2, classes = 4, characters = characters)

    assertTrue("confidence was ${decoded.single().confidence}", decoded.single().confidence > 0.85f)
  }

  @Test
  fun `handles raw logits by applying softmax`() {
    // Rows sum to 5, so they are logits rather than probabilities.
    val data = floatArrayOf(1f, 4f, 0f, 0f)

    val decoded = CtcDecoder.decode(data, batch = 1, timeSteps = 1, classes = 4, characters = characters)

    assertEquals("a", decoded.single().text)
    assertTrue(decoded.single().confidence > 0.9f)
  }

  @Test
  fun `decodes every item of a batch`() {
    val data = logits(1, 0) + logits(2, 0)

    val decoded = CtcDecoder.decode(data, batch = 2, timeSteps = 2, classes = 4, characters = characters)

    assertEquals(listOf("a", "b"), decoded.map { it.text })
  }
}
