package com.lingua.app.data.ocr

/**
 * Groups recognized lines into the blocks a user can tap.
 *
 * Screen text arrives as one box per line; translating line by line loses the sentence across wraps,
 * so neighbouring lines of the same column are merged until the vertical or horizontal rhythm breaks.
 */
object OcrParagraphGrouper {

  fun group(lines: List<OcrLine>): List<OcrParagraph> {
    if (lines.isEmpty()) return emptyList()

    val ordered = lines.sortedWith(
      compareBy({ (it.quad.centerY / 8f).toInt() }, { it.quad.centerX }),
    )
    val paragraphs = ArrayList<MutableList<OcrLine>>()
    for (line in ordered) {
      val current = paragraphs.lastOrNull()
      if (current != null && continues(current.last(), line)) {
        current.add(line)
      } else {
        paragraphs.add(mutableListOf(line))
      }
    }

    return paragraphs.map { group ->
      val left = group.minOf { it.quad.minX }
      val top = group.minOf { it.quad.minY }
      val right = group.maxOf { it.quad.maxX }
      val bottom = group.maxOf { it.quad.maxY }
      OcrParagraph(
        lines = group,
        text = group.joinToString("\n") { it.text },
        left = left,
        top = top,
        right = right,
        bottom = bottom,
      )
    }
  }

  /** True when [next] reads as a continuation of [previous] rather than a new block. */
  private fun continues(previous: OcrLine, next: OcrLine): Boolean {
    val a = previous.quad
    val b = next.quad
    if (b.centerY <= a.centerY) return false

    val lineHeight = maxOf(a.edgeHeight, b.edgeHeight, 1f)
    // A wrapped line follows closely; separate rows of a settings list do not.
    val verticalGap = b.minY - a.maxY
    if (verticalGap > lineHeight * 0.6f) return false

    val overlap = minOf(a.maxX, b.maxX) - maxOf(a.minX, b.minX)
    val narrower = minOf(a.width, b.width).coerceAtLeast(1f)
    if (overlap / narrower >= 0.5f) return true

    // Centered or slightly indented continuation lines still left-align roughly.
    return kotlin.math.abs(b.minX - a.minX) < lineHeight * 0.8f
  }
}
