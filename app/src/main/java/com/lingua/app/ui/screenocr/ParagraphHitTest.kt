package com.lingua.app.ui.screenocr

import com.lingua.app.data.ocr.OcrParagraph

/**
 * Which block a tap landed on.
 *
 * Boxes around small UI text are thin, so the hit area is grown by [margin] pixels; overlapping
 * candidates resolve to the tightest one.
 */
internal fun findParagraphAt(
  paragraphs: List<OcrParagraph>,
  x: Float,
  y: Float,
  margin: Float,
): Int? {
  var best: Int? = null
  var bestArea = Float.MAX_VALUE
  paragraphs.forEachIndexed { index, block ->
    if (x < block.left - margin || x > block.right + margin) return@forEachIndexed
    if (y < block.top - margin || y > block.bottom + margin) return@forEachIndexed
    val area = (block.right - block.left) * (block.bottom - block.top)
    if (area < bestArea) {
      bestArea = area
      best = index
    }
  }
  return best
}
