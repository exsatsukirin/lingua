package com.lingua.app.data.ocr

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Files the OCR engine needs, resolved on disk. */
data class OcrModels(
  val detectionPath: String,
  val recognitionPath: String,
  val dictionaryLines: List<String>,
)

/**
 * Installs the bundled PP-OCRv6 graphs into app storage.
 *
 * ONNX Runtime wants a real file path, and assets inside an APK are not one, so the graphs are
 * copied once into `filesDir`. Every copy is verified against the hash recorded here so a truncated
 * write (or a bad build) is repaired instead of fed to the model.
 */
class PaddleModelStore(private val context: Context) {

  private val directory = File(context.filesDir, "ocr/$VERSION")

  private val detection = File(directory, DETECTION_ASSET)

  private val recognition = File(directory, RECOGNITION_ASSET)

  private val dictionary = File(directory, DICTIONARY_ASSET)

  @Volatile private var installed: OcrModels? = null

  /** True when every model file is present; used for the read-only row in settings. */
  val isInstalled: Boolean
    get() = installed != null ||
      (detection.length() == DETECTION_BYTES && recognition.length() == RECOGNITION_BYTES)

  suspend fun models(): OcrModels =
    installed ?: withContext(Dispatchers.IO) {
      installed ?: install().also { installed = it }
    }

  private fun install(): OcrModels {
    directory.mkdirs()
    copyVerified(DETECTION_ASSET, detection, DETECTION_SHA256, DETECTION_BYTES)
    copyVerified(RECOGNITION_ASSET, recognition, RECOGNITION_SHA256, RECOGNITION_BYTES)
    copyVerified(DICTIONARY_ASSET, dictionary, DICTIONARY_SHA256, DICTIONARY_BYTES)
    return OcrModels(
      detectionPath = detection.absolutePath,
      recognitionPath = recognition.absolutePath,
      dictionaryLines = dictionary.readLines(),
    )
  }

  private fun copyVerified(asset: String, target: File, sha256: String, bytes: Long) {
    if (target.length() == bytes && sha256Of(target) == sha256) return
    target.delete()
    context.assets.open("ocr/$asset").use { input ->
      target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE * 8) }
    }
    check(target.length() == bytes) { "$asset is ${target.length()} bytes, expected $bytes" }
    check(sha256Of(target) == sha256) { "$asset does not match its recorded hash" }
  }

  private fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
      while (true) {
        val read = stream.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  companion object {
    /** Bumping this re-installs the models after an asset change. */
    const val VERSION = "v6-small"

    const val DETECTION_ASSET = "PP-OCRv6_small_det.onnx"
    const val RECOGNITION_ASSET = "PP-OCRv6_small_rec.onnx"
    const val DICTIONARY_ASSET = "ppocrv6_dict.txt"

    const val DETECTION_BYTES = 9_880_512L
    const val RECOGNITION_BYTES = 21_159_378L
    const val DICTIONARY_BYTES = 74_947L

    const val DETECTION_SHA256 =
      "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"
    const val RECOGNITION_SHA256 =
      "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"
    const val DICTIONARY_SHA256 =
      "b5f2bfe2bdd9448429e3e82b51c789775d9b42f2403d082b00662eb77e401c5d"
  }
}
