package com.lingua.app.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end recognition on device: bundled graphs, real processor, real timings. */
@RunWith(AndroidJUnit4::class)
class OnDeviceOcrTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  /** The fixture lives in the test APK, the models in the app under test. */
  private val testContext = InstrumentationRegistry.getInstrumentation().context

  private fun fixture(name: String): Bitmap =
    testContext.assets.open("ocr/$name").use { BitmapFactory.decodeStream(it) }
      ?: error("could not decode $name")

  @Test
  fun readsAMixedChineseAndEnglishScreenshot() {
    val repository = OcrRepository(PaddleModelStore(context))
    val bitmap = fixture("sample_screen.png")

    val started = System.currentTimeMillis()
    val result = runBlocking { repository.recognize(bitmap) }
    val elapsed = System.currentTimeMillis() - started
    println("on-device OCR: ${result.lines.size} lines in ${elapsed}ms")

    val text = result.lines.joinToString("\n") { line -> line.text }
    for (expected in listOf("电池与性能", "Battery usage since last full charge", "Battery level: 68%")) {
      assertTrue("missing '$expected' in: $text", text.replace(" ", "").contains(expected.replace(" ", "")))
    }
    assertTrue("expected at least 9 lines, got ${result.lines.size}", result.lines.size >= 9)
    assertTrue("ocr took ${elapsed}ms", elapsed < MAX_RECOGNITION_MS)
  }

  @Test
  fun readsVerticalDialogueAndAHorizontalCaption() {
    val repository = OcrRepository(PaddleModelStore(context))
    val bitmap = fixture("vertical_manga.png")

    val result = runBlocking { repository.recognize(bitmap) }
    println("manga on device: turned ${result.rotationDegrees}, ${result.lines.map { it.text }}")

    assertTrue("should have turned the pixels", result.rotationDegrees != 0)
    val texts = result.lines.map { it.text }
    assertTrue("missing a vertical column: $texts", texts.contains("吾輩は猫である。"))
    assertTrue("the horizontal caption was lost: $texts", texts.any { it.contains("夏目漱石") })
  }

  @Test
  fun findsNothingOnABlankImage() {
    val repository = OcrRepository(PaddleModelStore(context))
    val blank =
      Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).drawColor(Color.WHITE)
      }

    val result = runBlocking { repository.recognize(blank) }

    assertTrue("expected no text, got ${result.lines.map { it.text }}", result.lines.isEmpty())
  }

  /** Catches a regression that makes recognition unusably slow on a phone-sized screenshot. */
  private companion object {
    const val MAX_RECOGNITION_MS = 8_000L
  }
}
