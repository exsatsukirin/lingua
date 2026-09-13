package com.lingua.app.ui.screenocr

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lingua.app.AppContainer
import com.lingua.app.R
import com.lingua.app.capture.CaptureState
import com.lingua.app.data.ocr.OcrParagraph
import com.lingua.app.data.ocr.OcrParagraphGrouper
import com.lingua.app.data.ocr.OcrScriptCoverage
import com.lingua.app.data.remote.LlmError
import com.lingua.app.domain.Language
import com.lingua.app.domain.LanguageCatalog
import com.lingua.app.ui.common.ErrorMessage
import com.lingua.app.ui.common.LlmErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The translation a screen selection produced. */
data class ScreenOcrResult(
  val text: String,
  val sourceLanguage: Language?,
  val savedRecordId: Long?,
  val favorite: Boolean = false,
)

enum class ScreenOcrPhase {
  /** No image yet: the screen offers capture and image import. */
  Idle,

  Capturing,

  Recognizing,

  /** Text is on screen and can be selected. */
  Ready,
}

data class ScreenOcrUiState(
  val phase: ScreenOcrPhase = ScreenOcrPhase.Idle,
  val image: Bitmap? = null,
  val paragraphs: List<OcrParagraph> = emptyList(),
  val selected: Set<Int> = emptySet(),
  val targetLanguage: Language,
  val sourceOverride: Language? = null,
  /** Set when the user corrected the recognized text; replaces the selection as the input. */
  val editedText: String? = null,
  val result: ScreenOcrResult? = null,
  val isTranslating: Boolean = false,
  /** Non-zero when the text only became readable after turning the pixels. */
  val rotationDegrees: Int = 0,
  val error: ErrorMessage? = null,
  val errorDetail: String? = null,
  val hasProfile: Boolean = false,
) {
  /** Text that would be sent to the model: the user's edit, or the selected blocks. */
  val sourceText: String
    get() =
      editedText
        ?: paragraphs
          .filterIndexed { index, _ -> index in selected }
          .joinToString("\n") { it.text }

  val canTranslate: Boolean get() = sourceText.isNotBlank() && !isTranslating

  /** True when the chosen source language is one the bundled dictionary cannot read. */
  val unsupportedSource: Language?
    get() = sourceOverride?.takeIf { !OcrScriptCoverage.supports(it.code) }
}

/**
 * Drives screen text recognition: capture or import an image, read the text on device, let the user
 * pick blocks, then translate the selection through the same LLM pipeline as the rest of the app.
 */
class ScreenOcrViewModel(private val container: AppContainer, private val sharedImageUri: String?) :
  ViewModel() {

  private val _state =
    MutableStateFlow(ScreenOcrUiState(targetLanguage = container.defaultTargetLanguage()))

  val state: StateFlow<ScreenOcrUiState> = _state.asStateFlow()

  private var translationJob: Job? = null

  init {
    viewModelScope.launch {
      container.settingsRepository.settings.collect { settings ->
        val configured = settings.targetLanguageCode?.let { LanguageCatalog.targetOrNull(it) }
        _state.update { current ->
          current.copy(
            targetLanguage = configured ?: container.defaultTargetLanguage(),
            hasProfile = settings.profiles.isNotEmpty(),
          )
        }
      }
    }

    viewModelScope.launch { container.screenCapture.state.collect(::onCaptureState) }

    sharedImageUri?.let { uri -> importImage(Uri.parse(uri)) }
  }

  fun consentIntent(): Intent = container.screenCapture.consentIntent()

  /**
   * Called every time the screen is entered.
   *
   * The ViewModel is scoped to the activity rather than to the navigation entry, so a previous
   * capture would otherwise still be on screen when the user comes back.
   */
  fun onEnter() {
    _state.update {
      it.copy(result = null, isTranslating = false, error = null, errorDetail = null)
    }
  }

  /** Called with the result of the system screen-capture dialog. */
  fun onCaptureResult(resultCode: Int, data: Intent?) {
    if (data == null || resultCode != android.app.Activity.RESULT_OK) {
      _state.update { it.copy(error = ErrorMessage(R.string.screen_ocr_failed_denied)) }
      return
    }
    _state.update {
      it.copy(
        phase = ScreenOcrPhase.Capturing,
        error = null,
        errorDetail = null,
        result = null,
        editedText = null,
      )
    }
    container.screenCapture.capture(resultCode, data)
  }

  fun importImage(uri: Uri) {
    viewModelScope.launch {
      val decoded = withContext(Dispatchers.IO) { runCatching { decodeScaled(uri) } }
      val bitmap = decoded.getOrNull()
      if (bitmap == null) {
        _state.update {
          it.copy(
            error = ErrorMessage(R.string.screen_ocr_import_failed),
            errorDetail = decoded.exceptionOrNull()?.message ?: uri.toString(),
          )
        }
        return@launch
      }
      recognize(bitmap)
    }
  }

  fun retake() {
    translationJob?.cancel()
    container.screenCapture.reset()
    _state.update {
      it.copy(
        phase = ScreenOcrPhase.Idle,
        image = null,
        paragraphs = emptyList(),
        selected = emptySet(),
        editedText = null,
        result = null,
        isTranslating = false,
        error = null,
        errorDetail = null,
      )
    }
  }

  fun toggleParagraph(index: Int) {
    _state.update { current ->
      val selected = current.selected.toMutableSet()
      if (!selected.add(index)) selected.remove(index)
      current.copy(selected = selected, editedText = null, result = null)
    }
  }

  fun selectAll() {
    _state.update {
      it.copy(selected = it.paragraphs.indices.toSet(), editedText = null, result = null)
    }
  }

  fun clearSelection() {
    _state.update { it.copy(selected = emptySet(), editedText = null, result = null) }
  }

  fun editSource(text: String) {
    _state.update { it.copy(editedText = text, result = null) }
  }

  fun setTargetLanguage(language: Language) {
    _state.update { it.copy(targetLanguage = language, result = null) }
  }

  fun setSourceOverride(language: Language?) {
    _state.update { it.copy(sourceOverride = language, result = null) }
  }

  fun dismissError() {
    _state.update { it.copy(error = null, errorDetail = null) }
  }

  fun translate() {
    val snapshot = _state.value
    val text = snapshot.sourceText.trim()
    if (text.isEmpty()) return

    translationJob?.cancel()
    translationJob =
      viewModelScope.launch {
        _state.update { it.copy(isTranslating = true, error = null, errorDetail = null) }
        container.translationRepository
          .translate(
            sourceText = text,
            target = snapshot.targetLanguage,
            sourceOverride = snapshot.sourceOverride,
          )
          .onSuccess { translated ->
            _state.update { current ->
              current.copy(
                isTranslating = false,
                result =
                  ScreenOcrResult(
                    text = translated.outcome.translation,
                    sourceLanguage = translated.outcome.sourceLanguage,
                    savedRecordId = translated.savedRecordId,
                  ),
              )
            }
          }
          .onFailure { error ->
            if (error is CancellationException) throw error
            if (error is LlmError.Cancelled) {
              _state.update { it.copy(isTranslating = false) }
            } else {
              _state.update { current ->
                current.copy(
                  isTranslating = false,
                  error = LlmErrorMapper.toMessage(error),
                  errorDetail = LlmErrorMapper.detailOf(error),
                )
              }
            }
          }
      }
  }

  fun retry() = translate()

  fun toggleFavorite() {
    val recordId = _state.value.result?.savedRecordId ?: return
    val next = !(_state.value.result?.favorite ?: false)
    viewModelScope.launch {
      container.historyRepository.setFavorite(recordId, next)
      _state.update { it.copy(result = it.result?.copy(favorite = next)) }
    }
  }

  private fun onCaptureState(capture: CaptureState) {
    when (capture) {
      CaptureState.Idle -> Unit
      CaptureState.Capturing ->
        _state.update { it.copy(phase = ScreenOcrPhase.Capturing, error = null) }
      is CaptureState.Success -> recognize(capture.bitmap)
      is CaptureState.Failed ->
        _state.update {
          it.copy(phase = ScreenOcrPhase.Idle, error = capture.reason.toMessage())
        }
    }
  }

  private fun recognize(bitmap: Bitmap) {
    viewModelScope.launch {
      _state.update {
        it.copy(
          phase = ScreenOcrPhase.Recognizing,
          image = bitmap,
          paragraphs = emptyList(),
          selected = emptySet(),
          editedText = null,
          result = null,
          error = null,
          errorDetail = null,
        )
      }
      runCatching { container.ocrRepository.recognize(bitmap) }
        .onSuccess { recognized ->
          val paragraphs = OcrParagraphGrouper.group(recognized.lines)
          _state.update { current ->
            current.copy(
              phase = ScreenOcrPhase.Ready,
              paragraphs = paragraphs,
              rotationDegrees = recognized.rotationDegrees,
              // Nothing is pre-selected: a screen also holds icons the detector reads as garbage,
              // and sending that to a paid endpoint would be worse than one extra tap.
              selected = emptySet(),
            )
          }
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          _state.update { current ->
            current.copy(
              phase = ScreenOcrPhase.Idle,
              error = ErrorMessage(R.string.screen_ocr_failed_model),
              errorDetail = error.message,
            )
          }
        }
    }
  }

  private fun CaptureState.Failure.toMessage(): ErrorMessage =
    when (this) {
      CaptureState.Failure.Denied -> ErrorMessage(R.string.screen_ocr_failed_denied)
      CaptureState.Failure.Empty -> ErrorMessage(R.string.screen_ocr_failed_empty)
      CaptureState.Failure.Timeout -> ErrorMessage(R.string.screen_ocr_failed_timeout)
    }

  /** Decodes a shared image, downsampled so a 4K screenshot does not blow up memory. */
  private fun decodeScaled(uri: Uri): Bitmap? {
    val resolver = container.appContext.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
    val options =
      BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
  }

  companion object {
    private const val MAX_IMAGE_SIDE = 2048

    fun factory(container: AppContainer, sharedImageUri: String? = null) = viewModelFactory {
      initializer { ScreenOcrViewModel(container, sharedImageUri) }
    }
  }
}
