package com.lingua.app.ui.processtext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lingua.app.AppContainer
import com.lingua.app.data.remote.LlmError
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

data class ProcessTextUiState(
  val sourceText: String = "",
  val targetLanguage: Language,
  val isLoading: Boolean = false,
  val translation: String? = null,
  val detectedLanguage: Language? = null,
  val error: ErrorMessage? = null,
  val errorDetail: String? = null,
  val savedRecordId: Long? = null,
  val favorite: Boolean = false,
  /** True when the failure is "nothing configured yet", which needs a route into Settings. */
  val needsSetup: Boolean = false,
) {
  val hasResult: Boolean get() = !translation.isNullOrBlank()
}

/**
 * One-shot translation for the text handed over by `ACTION_PROCESS_TEXT`.
 *
 * Uses the same [com.lingua.app.domain.TranslationRepository] as the main screen, so the configured
 * endpoint, target language, custom prompt and history rules all apply unchanged.
 */
class ProcessTextViewModel(private val container: AppContainer) : ViewModel() {

  private val _state =
    MutableStateFlow(ProcessTextUiState(targetLanguage = container.defaultTargetLanguage()))
  val state: StateFlow<ProcessTextUiState> = _state.asStateFlow()

  private var job: Job? = null
  private var started = false

  /** Called once from the screen; safe against recomposition and configuration changes. */
  fun start(sourceText: String) {
    if (started) return
    started = true
    _state.update { it.copy(sourceText = sourceText) }
    viewModelScope.launch {
      val settings = container.settingsRepository.current()
      _state.update {
        it.copy(
          targetLanguage =
            LanguageCatalog.targetOrNull(settings.targetLanguageCode) ?: container.defaultTargetLanguage(),
        )
      }
      translate()
    }
  }

  fun translate() {
    val snapshot = _state.value
    if (snapshot.sourceText.isBlank() || snapshot.isLoading) return

    job?.cancel()
    job =
      viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null, errorDetail = null, needsSetup = false) }

        container.translationRepository
          .translate(
            sourceText = snapshot.sourceText,
            target = snapshot.targetLanguage,
            sourceOverride = null,
          )
          .onSuccess { result ->
            _state.update {
              it.copy(
                isLoading = false,
                translation = result.outcome.translation,
                detectedLanguage = result.outcome.sourceLanguage,
                savedRecordId = result.savedRecordId,
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
              _state.update {
                it.copy(
                  isLoading = false,
                  error = LlmErrorMapper.toMessage(error),
                  errorDetail = LlmErrorMapper.detailOf(error),
                  needsSetup = error is TranslationRepository.NoActiveProfile || error is LlmError.MissingApiKey,
                )
              }
            }
          }
      }
  }

  fun toggleFavorite() {
    val current = _state.value
    val recordId = current.savedRecordId ?: return
    val next = !current.favorite
    viewModelScope.launch {
      container.historyRepository.setFavorite(recordId, next)
      _state.update { it.copy(favorite = next) }
    }
  }

  companion object {
    fun factory(container: AppContainer) = viewModelFactory {
      initializer { ProcessTextViewModel(container) }
    }
  }
}
