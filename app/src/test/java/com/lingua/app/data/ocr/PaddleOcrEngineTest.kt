package com.lingua.app.data.ocr

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real PP-OCRv6 graphs over a bundled screenshot fixture.
 *
 * This is the only test that exercises detection thresholds, the homography crop and CTC decoding
 * together, and it needs no device: the desktop ONNX Runtime artifact serves the same graphs.
 */
class PaddleOcrEngineTest {

  private val assetsDir =
    listOf(File("src/main/assets/ocr"), File("app/src/main/assets/ocr")).firstOrNull { it.isDirectory }
      ?: error("assets/ocr not found (cwd=${File(".").absolutePath})")

  private val fixture =
    listOf(File("src/test/resources/ocr/sample_screen.png"), File("app/src/test/resources/ocr/sample_screen.png"))
      .firstOrNull { it.exists() }
      ?: error("sample_screen.png not found (cwd=${File(".").absolutePath})")

  private fun loadImage(): RgbImage {
    val buffered = ImageIO.read(fixture)
    val pixels = IntArray(buffered.width * buffered.height)
    buffered.getRGB(0, 0, buffered.width, buffered.height, pixels, 0, buffered.width)
    return RgbImage(pixels, buffered.width, buffered.height)
  }

  private fun recognize(): OcrResult {
    val engine =
      PaddleOcrEngine(
        detModelPath = File(assetsDir, "PP-OCRv6_small_det.onnx").absolutePath,
        recModelPath = File(assetsDir, "PP-OCRv6_small_rec.onnx").absolutePath,
        dictLines = File(assetsDir, "ppocrv6_dict.txt").readLines(),
      )
    return engine.use { it.recognize(loadImage()) }
  }

  @Test
  fun `reads mixed chinese and english screen text`() {
    val result = recognize()
    val lines = result.lines
    println("recognized ${lines.size} lines: ${lines.joinToString(" | ") { it.text }}")

    assertTrue("expected at least 9 lines, got ${lines.size}", lines.size >= 9)
    assertEquals("an upright screenshot must not be re-run turned", 0, result.rotationDegrees)
    val expected =
      listOf(
        "电池与性能",
        "Battery usage since last full charge",
        "已使用3小时12分钟",
        "屏幕开启时间1小时48分钟",
        "To improve battery life, the system limits",
        "background activity for apps you rarely use.",
        "You can change this in Settings at any time.",
        "Wi-Fi已连接·5 GHz",
        "省电模式已关闭",
        "Battery level: 68%",
      )
    val texts = lines.map { it.text }
    // The model's use of spaces around CJK/Latin boundaries shifts with batch padding, so compare
    // ignoring whitespace.
    val compact = texts.map { it.replace(" ", "") }
    for (needle in expected) {
      assertTrue("missing '$needle' in $texts", compact.any { it.contains(needle.replace(" ", "")) })
    }
    assertTrue("confidences too low: ${lines.map { it.confidence }}", lines.all { it.confidence > 0.8f })
  }

  @Test
  fun `groups wrapped lines without merging separate rows`() {
    val result = recognize()

    val paragraphs = OcrParagraphGrouper.group(result.lines)
    println("paragraphs: ${paragraphs.map { it.text.replace("\n", " / ") }}")

    val wrapped = paragraphs.firstOrNull { it.text.contains("To improve battery life") }
    assertTrue("wrapped paragraph not found", wrapped != null)
    assertTrue(
      "wrapped paragraph lost its last line: ${wrapped!!.text}",
      wrapped.text.contains("Settings at any time"),
    )

    val title = paragraphs.first { it.text.contains("电池与性能") }
    assertTrue("title absorbed the next row: ${title.text}", !title.text.contains("Battery usage"))
  }

  @Test
  fun `recovers a screenshot that is lying on its side`() {
    val sideways = loadImage().rotated(QuarterTurn.Clockwise90)

    val result =
      PaddleOcrEngine(
        detModelPath = File(assetsDir, "PP-OCRv6_small_det.onnx").absolutePath,
        recModelPath = File(assetsDir, "PP-OCRv6_small_rec.onnx").absolutePath,
        dictLines = File(assetsDir, "ppocrv6_dict.txt").readLines(),
      ).use { it.recognize(sideways) }

    println("sideways: rotated by ${result.rotationDegrees}, lines=${result.lines.map { it.text }}")

    assertTrue("should have turned the pixels, got ${result.rotationDegrees}", result.rotationDegrees != 0)
    val compact = result.lines.map { it.text.replace(" ", "") }
    for (needle in listOf("电池与性能", "Batteryusagesincelastfullcharge", "Batterylevel:68%")) {
      assertTrue("missing '$needle' in $compact", compact.any { it.contains(needle) })
    }
    // Boxes come back in the caller's frame, so they must fit the image that was handed in.
    for (line in result.lines) {
      assertTrue(
        "box ${line.quad} outside ${sideways.width}x${sideways.height}",
        line.quad.minX >= -1f &&
          line.quad.minY >= -1f &&
          line.quad.maxX <= sideways.width + 1f &&
          line.quad.maxY <= sideways.height + 1f,
      )
    }
  }

  @Test
  fun `reads vertical dialogue and a horizontal caption in the same image`() {
    // Manga-style panel: four columns read top-to-bottom, plus one ordinary horizontal caption.
    val buffered = ImageIO.read(File("src/test/resources/ocr/vertical_manga.png"))
    val pixels = IntArray(buffered.width * buffered.height)
    buffered.getRGB(0, 0, buffered.width, buffered.height, pixels, 0, buffered.width)

    val result =
      PaddleOcrEngine(
        detModelPath = File(assetsDir, "PP-OCRv6_small_det.onnx").absolutePath,
        recModelPath = File(assetsDir, "PP-OCRv6_small_rec.onnx").absolutePath,
        dictLines = File(assetsDir, "ppocrv6_dict.txt").readLines(),
      ).use { it.recognize(RgbImage(pixels, buffered.width, buffered.height)) }

    println("manga: turned ${result.rotationDegrees}, ${result.lines.map { it.text }}")

    assertTrue("should have turned the pixels", result.rotationDegrees != 0)
    val texts = result.lines.map { it.text }
    for (column in listOf("吾輩は猫である。", "名前はまだ無い。", "どこで生れたか", "とんと見当がつかぬ。")) {
      assertTrue("missing vertical column '$column' in $texts", texts.contains(column))
    }
    // Turning the image is what makes the columns readable, and it is exactly what breaks the
    // caption unless the upright pass is merged back in.
    assertTrue("the horizontal caption was lost: $texts", texts.any { it.contains("夏目漱石") })
  }

  @Test
  fun `returns no lines for a blank image`() {
    val blank = RgbImage(IntArray(320 * 320) { 0xFFFFFFFF.toInt() }, 320, 320)

    val result =
      PaddleOcrEngine(
        detModelPath = File(assetsDir, "PP-OCRv6_small_det.onnx").absolutePath,
        recModelPath = File(assetsDir, "PP-OCRv6_small_rec.onnx").absolutePath,
        dictLines = File(assetsDir, "ppocrv6_dict.txt").readLines(),
      ).use { it.recognize(blank) }

    assertTrue("blank page produced ${result.lines.map { it.text }}", result.lines.isEmpty())
  }
}
