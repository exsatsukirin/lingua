package com.lingua.app.data.ocr

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Turns a screenshot into recognized text.
 *
 * The ONNX sessions are created on first use (loading 30MB of graphs at app start would be wasted
 * work for anyone who never opens screen recognition) and kept for the process lifetime, because
 * rebuilding them costs about a second. Calls are serialized: one inference at a time keeps peak
 * memory predictable on phones.
 */
class OcrRepository(private val modelStore: PaddleModelStore) {

  private val lock = Mutex()

  private var engine: PaddleOcrEngine? = null

  val isModelInstalled: Boolean get() = modelStore.isInstalled

  suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
    lock.withLock {
      val active = engine ?: createEngine()
      active.recognize(bitmap.toRgbImage())
    }
  }

  private suspend fun createEngine(): PaddleOcrEngine {
    val models = modelStore.models()
    return PaddleOcrEngine(
      detModelPath = models.detectionPath,
      recModelPath = models.recognitionPath,
      dictLines = models.dictionaryLines,
    ).also { engine = it }
  }
}

/** Copies the bitmap into a plain int array so the OCR core stays free of Android types. */
fun Bitmap.toRgbImage(): RgbImage {
  val pixels = IntArray(width * height)
  getPixels(pixels, 0, width, 0, 0, width, height)
  return RgbImage(pixels, width, height)
}
