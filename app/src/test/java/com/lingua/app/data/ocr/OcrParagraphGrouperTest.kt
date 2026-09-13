package com.lingua.app.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrParagraphGrouperTest {

  private fun line(text: String, left: Float, top: Float, right: Float, bottom: Float): OcrLine =
    OcrLine(
      text = text,
      quad = Quad(floatArrayOf(left, right, right, left), floatArrayOf(top, top, bottom, bottom)),
      confidence = 0.9f,
    )

  @Test
  fun `merges the wrapped lines of one sentence`() {
    val lines =
      listOf(
        line("To improve battery life, the system limits", 40f, 400f, 900f, 440f),
        line("background activity for apps you rarely use.", 40f, 450f, 920f, 490f),
        line("You can change this in Settings at any time.", 40f, 500f, 900f, 540f),
      )

    val paragraphs = OcrParagraphGrouper.group(lines)

    assertEquals(1, paragraphs.size)
    assertTrue(paragraphs.single().text.contains("Settings at any time"))
    assertEquals(40f, paragraphs.single().left, 0.01f)
    assertEquals(540f, paragraphs.single().bottom, 0.01f)
  }

  @Test
  fun `keeps separate list rows apart`() {
    val lines =
      listOf(
        line("Battery usage since last full charge", 40f, 190f, 890f, 240f),
        line("Used 3 hours 12 minutes", 40f, 281f, 480f, 320f),
      )

    val paragraphs = OcrParagraphGrouper.group(lines)

    assertEquals(2, paragraphs.size)
  }

  @Test
  fun `keeps far apart lines in separate blocks`() {
    val lines =
      listOf(
        line("First block", 40f, 100f, 300f, 140f),
        line("Second block", 40f, 400f, 320f, 440f),
      )

    assertEquals(2, OcrParagraphGrouper.group(lines).size)
  }

  @Test
  fun `orders blocks by position regardless of input order`() {
    val lines =
      listOf(
        line("bottom", 40f, 500f, 200f, 540f),
        line("top", 40f, 100f, 200f, 140f),
      )

    val paragraphs = OcrParagraphGrouper.group(lines)

    assertEquals(listOf("top", "bottom"), paragraphs.map { it.text })
  }

  @Test
  fun `returns nothing for an empty page`() {
    assertEquals(emptyList<OcrParagraph>(), OcrParagraphGrouper.group(emptyList()))
  }
}
