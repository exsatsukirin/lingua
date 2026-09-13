package com.lingua.app.ui.screenocr

import com.lingua.app.data.ocr.OcrParagraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParagraphHitTestTest {

  private fun block(left: Float, top: Float, right: Float, bottom: Float) =
    OcrParagraph(lines = emptyList(), text = "t", left = left, top = top, right = right, bottom = bottom)

  private val blocks =
    listOf(
      block(100f, 100f, 200f, 130f),
      block(100f, 200f, 200f, 230f),
      block(300f, 100f, 500f, 200f),
    )

  @Test
  fun `a point inside a block selects it`() {
    assertEquals(1, findParagraphAt(blocks, 150f, 215f, margin = 0f))
  }

  @Test
  fun `a point outside every block selects nothing`() {
    assertNull(findParagraphAt(blocks, 150f, 170f, margin = 0f))
  }

  @Test
  fun `a small margin makes thin lines easier to hit`() {
    // 10px above the first block: a miss without the margin, a hit with it.
    assertNull(findParagraphAt(blocks, 150f, 95f, margin = 0f))
    assertEquals(0, findParagraphAt(blocks, 150f, 95f, margin = 12f))
  }

  @Test
  fun `overlapping candidates resolve to the tightest block`() {
    val overlapping = listOf(block(0f, 0f, 400f, 400f), block(100f, 100f, 200f, 150f))

    assertEquals(1, findParagraphAt(overlapping, 150f, 120f, margin = 0f))
  }

  @Test
  fun `the margin never reaches a block that is further away`() {
    assertNull(findParagraphAt(blocks, 150f, 170f, margin = 12f))
  }
}
