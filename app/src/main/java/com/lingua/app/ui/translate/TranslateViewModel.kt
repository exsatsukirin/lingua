package com.lingua.app.ui.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lingua.app.AppContainer
import com.lingua.app.data.notify.ScreenTranslateNotifier
import com.lingua.app.data.remote.LlmError
import com.lingua.app.data.remote.TranslationOutcome
import com.lingua.app.domain.Language
import com.lingua.app.domain.LanguageCatalog
import com.lingua.app.domain.TranslationRepository
import com.lingua.app.ui.common.ErrorMessage
import com.lingua.app.ui.common.LlmErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_SOURCE_LENGTH = 5000

data class TranslateResultState(
  val text: String,
  val sourceLanguage: Language?,
  val sourceLanguageLabel: String?,
  val latencyMs: Long,
  val savedRecordId: Long?,
  val favorite: Boolean = false,
)

data class TranslateUiState(
  val sourceText: String = "",
  val targetLanguage: Language,
  val sourceOverride: Language? = null,
  val result: TranslateResultState? = null,
  val isLoading: Boolean = false,
  val error: ErrorMessage? = null,
  val errorDetail: String? = null,
  val hasProfile: Boolean = false,
  val activeProfileName: String? = null,
  val autoSaveHistory: Boolean = true,
  /**
   * Whether the resident notification is on. Session-scoped on purpose: it is never persisted, and
   * opening the app turns it off again.
   */
  val screenTranslateEnabled: Boolean = false,
) {
  val isTooLong: Boolean get() = sourceText.length > MAX_SOURCE_LENGTH

  val canTranslate: Boolean get() = sourceText.isNotBlank() && !isTooLong && !isLoading

  val detectedLanguage: Language? get() = result?.sourceLanguage

  /** True when the text would most likely come back unchanged. */
  val sameLanguageWarning: Boolean
    get() {
      val source = sourceOverride ?: result?.sourceLanguage ?: return false
      return source.code == targetLanguage.code
    }
}

class TranslateViewModel(private val container: AppContainer) : ViewModel() {

  private val _state =
    MutableStateFlow(
      TranslateUiState(targetLanguage = container.defaultTargetLanguage())
    )

  val state: StateFlow<TranslateUiState> = _state.asStateFlow()

  private var job: Job? = null

  init {
    viewModelScope.launch {
      container.settingsRepository.settings.collect { settings ->
        val configured = settings.targetLanguageCode?.let { LanguageCatalog.targetOrNull(it) }
        _state.update { current ->
          current.copy(
            targetLanguage = configured ?: container.defaultTargetLanguage(),
            hasProfile = settings.profiles.isNotEmpty(),
            activeProfileName = settings.activeProfile?.displayName,
            autoSaveHistory = settings.autoSaveHistory,
          )
        }
      }
    }
  }

  fun onSourceTextChange(text: String) {
    _state.update { it.copy(sourceText = text) }
  }

  fun clearSourceText() {
    job?.cancel()
    _state.update { it.copy(sourceText = "", result = null, error = null, errorDetail = null) }
  }

  fun setTargetLanguage(language: Language) {
    viewModelScope.launch { container.settingsRepository.setTargetLanguage(language.code) }
    // Refresh the visible result when the target changes, so the card never lies about what it shows.
    _state.update { it.copy(result = null, error = null, errorDetail = null) }
  }

  fun setSourceOverride(language: Language?) {
    _state.update { it.copy(sourceOverride = language, error = null, errorDetail = null, result = null) }
  }

  fun setExample(text: String) {
    _state.update { it.copy(sourceText = text, result = null, error = null, errorDetail = null) }
  }

  /** Swaps the detected source language into the target slot and reuses the translation as input. */
  fun swapLanguages() {
    val current = _state.value
    val detected = current.result?.sourceLanguage
    if (detected == null) {
      // Nothing detected yet: just clear the manual override and let detection run again.
      _state.update { it.copy(sourceOverride = null) }
      return
    }
    viewModelScope.launch { container.settingsRepository.setTargetLanguage(detected.code) }
    _state.update { state ->
      state.copy(
        sourceText = state.result?.text ?: state.sourceText,
        sourceOverride = state.targetLanguage,
        result = null,
        error = null,
        errorDetail = null,
      )
    }
  }

  fun translate() {
    val snapshot = _state.value
    val text = snapshot.sourceText.trim()
    if (text.isEmpty() || snapshot.isTooLong) return

    job?.cancel()
    job =
      viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null, errorDetail = null) }

        val result =
          container.translationRepository.translate(
            sourceText = text,
            target = snapshot.targetLanguage,
            sourceOverride = snapshot.sourceOverride,
          )

        result
          .onSuccess { translation ->
            _state.update { current ->
              current.copy(
                isLoading = false,
                result = translation.toResultState(),
                error = null,
                errorDetail = null,
              )
            }
          }
          .onFailure { error ->
            if (error is CancellationException) throw error
            if (error is LlmError.Cancelled) {
              _state.update { it.copy(isLoading = false) }
            } else {
              _state.update { current ->
                current.copy(
                  isLoading = false,
                  error = LlmErrorMapper.toMessage(error),
                  errorDetail = LlmErrorMapper.detailOf(error),
                )
              }
            }
          }
      }
  }

  fun retry() = translate()

  /** Turns the resident notification on or off. */
  fun setScreenTranslateEnabled(enabled: Boolean) {
    _state.update { it.copy(screenTranslateEnabled = enabled) }
    ScreenTranslateNotifier.setEnabled(container.appContext, enabled)
  }

  /**
   * Reconciles the switch with the notification that is actually posted, so dismissing it from the
   * shade does not leave a switch that claims to be on.
   */
  fun syncScreenTranslateState() {
    val showing = ScreenTranslateNotifier.isShowing(container.appContext)
    if (showing != _state.value.screenTranslateEnabled) {
      _state.update { it.copy(screenTranslateEnabled = showing) }
    }
  }

  /** Explicit save for users who turned automatic saving off. */
  fun saveToHistory() {
    val current = _state.value
    val result = current.result ?: return
    if (result.savedRecordId != null) return

    viewModelScope.launch {
      val outcome =
        TranslationOutcome(
          translation = result.text,
          sourceLanguage = result.sourceLanguage,
          sourceLanguageLabel = result.sourceLanguageLabel,
          model = null,
          latencyMs = result.latencyMs,
        )
      val id =
        container.translationRepository.save(
          outcome = outcome,
          sourceText = current.sourceText,
          target = current.targetLanguage,
          providerName = current.activeProfileName.orEmpty(),
        )
      _state.update { it.copy(result = it.result?.copy(savedRecordId = id)) }
    }
  }

  fun toggleFavorite() {
    val result = _state.value.result ?: return
    val recordId = result.savedRecordId ?: return
    val next = !result.favorite
    viewModelScope.launch {
      container.historyRepository.setFavorite(recordId, next)
      _state.update { it.copy(result = it.result?.copy(favorite = next)) }
    }
  }

  private fun com.lingua.app.domain.TranslationResult.toResultState() =
    TranslateResultState(
      text = outcome.translation,
      sourceLanguage = outcome.sourceLanguage,
      sourceLanguageLabel = outcome.sourceLanguageLabel,
      latencyMs = outcome.latencyMs,
      savedRecordId = savedRecordId,
    )

  companion object {
    fun factory(container: AppContainer) = viewModelFactory {
      initializer { TranslateViewModel(container) }
    }
  }
}
