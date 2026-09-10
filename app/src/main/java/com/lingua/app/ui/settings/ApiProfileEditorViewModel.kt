package com.lingua.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lingua.app.AppContainer
import com.lingua.app.data.remote.EndpointResolver
import com.lingua.app.data.remote.LlmError
import com.lingua.app.data.settings.ApiProfile
import com.lingua.app.data.settings.HeaderPair
import com.lingua.app.domain.Language
import com.lingua.app.domain.LanguageCatalog
import com.lingua.app.ui.common.ErrorMessage
import com.lingua.app.ui.common.LlmErrorMapper
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ValidationErrors(
  val name: Boolean = false,
  val baseUrl: Boolean = false,
  val model: Boolean = false,
) {
  val hasErrors: Boolean get() = name || baseUrl || model
}

sealed interface TestState {
  data object Idle : TestState

  data object Running : TestState

  data class Success(
    val latencyMs: Long,
    val reply: String,
    val raw: String,
    val model: String?,
    val modelsAvailable: Boolean?,
    val endpoint: String,
  ) : TestState

  data class Failure(val message: ErrorMessage, val detail: String?) : TestState
}

data class ApiEditorUiState(
  val profileId: String? = null,
  val name: String = "",
  val baseUrl: String = "",
  val apiKey: String = "",
  val model: String = "",
  val temperature: Double = ApiProfile.DEFAULT_TEMPERATURE,
  val timeoutSeconds: Int = ApiProfile.DEFAULT_TIMEOUT_SECONDS,
  val jsonMode: Boolean = true,
  val headersText: String = "",
  val keyDecryptFailed: Boolean = false,
  val validation: ValidationErrors = ValidationErrors(),
  val test: TestState = TestState.Idle,
  val models: List<String> = emptyList(),
  val isFetchingModels: Boolean = false,
  val modelsMessage: String? = null,
  val saved: Boolean = false,
) {
  val isEditing: Boolean get() = profileId != null

  val preset: ProviderPreset get() = ProviderPreset.forBaseUrl(baseUrl)

  val endpointPreview: String
    get() = if (EndpointResolver.isValidBaseUrl(baseUrl)) EndpointResolver.chatCompletions(baseUrl) else ""
}

/**
 * Drives the endpoint editor. The form can be tested before it is saved: every test builds a throwaway
 * [ApiProfile] from the current field values, so nothing has to be committed first.
 */
class ApiProfileEditorViewModel(private val container: AppContainer) : ViewModel() {

  private val _state = MutableStateFlow(ApiEditorUiState())
  val state: StateFlow<ApiEditorUiState> = _state.asStateFlow()

  private var boundProfileId: String? = null
  private var testJob: Job? = null

  /** Loads [profileId] once per editor session, resetting the form when the target changes. */
  fun bind(profileId: String?) {
    if (boundProfileId == profileId) return
    boundProfileId = profileId
    if (profileId == null) {
      _state.value = ApiEditorUiState()
      return
    }
    _state.value = ApiEditorUiState(profileId = profileId)
    viewModelScope.launch {
      val profile = container.settingsRepository.current().profiles.firstOrNull { it.id == profileId }
      if (profile == null) {
        _state.value = ApiEditorUiState()
        return@launch
      }
      _state.update {
        it.copy(
          name = profile.name,
          baseUrl = profile.baseUrl,
          apiKey = profile.apiKey,
          model = profile.model,
          temperature = profile.temperature,
          timeoutSeconds = profile.timeoutSeconds,
          jsonMode = profile.jsonMode,
          headersText = profile.extraHeaders.joinToString("\n") { header -> "${header.name}: ${header.value}" },
          keyDecryptFailed = profile.apiKey.isBlank(),
        )
      }
    }
  }

  fun onName(value: String) = _state.update { it.copy(name = value, validation = it.validation.copy(name = false)) }

  fun onBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, validation = it.validation.copy(baseUrl = false)) }

  fun onApiKey(value: String) = _state.update { it.copy(apiKey = value, keyDecryptFailed = false) }

  fun onModel(value: String) = _state.update { it.copy(model = value, validation = it.validation.copy(model = false)) }

  fun onTemperature(value: Double) = _state.update { it.copy(temperature = value) }

  fun onTimeout(value: Int) =
    _state.update {
      it.copy(timeoutSeconds = value.coerceIn(ApiProfile.MIN_TIMEOUT_SECONDS, ApiProfile.MAX_TIMEOUT_SECONDS))
    }

  fun onJsonMode(value: Boolean) = _state.update { it.copy(jsonMode = value) }

  fun onHeaders(value: String) = _state.update { it.copy(headersText = value) }

  fun applyPreset(preset: ProviderPreset) {
    if (preset == ProviderPreset.Custom) return
    _state.update { current ->
      current.copy(
        baseUrl = preset.baseUrl,
        model = preset.model,
        name = current.name.ifBlank { preset.baseUrl },
      )
    }
  }

  fun save(onSaved: () -> Unit) {
    val current = _state.value
    val validation =
      ValidationErrors(
        name = current.name.isBlank(),
        baseUrl = !EndpointResolver.isValidBaseUrl(current.baseUrl),
        model = current.model.isBlank(),
      )
    if (validation.hasErrors) {
      _state.update { it.copy(validation = validation) }
      return
    }

    val profile =
      ApiProfile(
        id = current.profileId ?: UUID.randomUUID().toString(),
        name = current.name.trim(),
        baseUrl = current.baseUrl.trim(),
        model = current.model.trim(),
        apiKey = current.apiKey.trim(),
        temperature = current.temperature,
        timeoutSeconds = current.timeoutSeconds,
        jsonMode = current.jsonMode,
        extraHeaders = parseHeaders(current.headersText),
      )

    viewModelScope.launch {
      container.settingsRepository.upsertProfile(profile)
      _state.update { it.copy(profileId = profile.id, saved = true) }
      onSaved()
    }
  }

  fun delete(onDeleted: () -> Unit) {
    val id = _state.value.profileId ?: return
    viewModelScope.launch {
      container.settingsRepository.deleteProfile(id)
      onDeleted()
    }
  }

  /** Fetches `/models` and merges the result into the model field suggestions. */
  fun fetchModels() {
    val profile = formAsProfile()
    _state.update { it.copy(isFetchingModels = true, modelsMessage = null, test = TestState.Idle) }
    viewModelScope.launch {
      container.llmClient
        .listModels(profile)
        .onSuccess { models ->
          _state.update {
            it.copy(
              isFetchingModels = false,
              models = models,
              modelsMessage = "OK (${models.size})",
              model = if (it.model.isBlank() && models.isNotEmpty()) models.first() else it.model,
            )
          }
        }
        .onFailure { error ->
          _state.update { it.copy(isFetchingModels = false, modelsMessage = "—", test = it.test.toFailure(error)) }
        }
    }
  }

  /** Verifies reachability: `GET /models` (best effort) plus one tiny chat completion. */
  fun testConnection() {
    val profile = formAsProfile()
    if (!EndpointResolver.isValidBaseUrl(profile.baseUrl)) {
      _state.update { it.copy(validation = it.validation.copy(baseUrl = true)) }
      return
    }

    testJob?.cancel()
    _state.update { it.copy(test = TestState.Running) }
    testJob =
      viewModelScope.launch {
        val startedAt = System.nanoTime()
        val modelsResult = container.llmClient.listModels(profile)
        val modelsAvailable = modelsResult.isSuccess
        modelsResult.getOrNull()?.let { models -> _state.update { it.copy(models = models) } }

        container.llmClient
          .testChat(profile, SAMPLE_TEXT)
          .onSuccess { outcome ->
            _state.update {
              it.copy(
                test =
                  TestState.Success(
                    latencyMs = (System.nanoTime() - startedAt) / 1_000_000,
                    reply = outcome.reply,
                    raw = outcome.rawResponse,
                    model = outcome.model,
                    modelsAvailable = modelsAvailable,
                    endpoint = outcome.endpoint,
                  )
              )
            }
          }
          .onFailure { error -> _state.update { it.copy(test = it.test.toFailure(error)) } }
      }
  }

  /** Runs the full translation path (prompt → parse) against the current form values. */
  fun testTranslation() {
    val profile = formAsProfile()
    if (!EndpointResolver.isValidBaseUrl(profile.baseUrl)) {
      _state.update { it.copy(validation = it.validation.copy(baseUrl = true)) }
      return
    }

    testJob?.cancel()
    _state.update { it.copy(test = TestState.Running) }
    testJob =
      viewModelScope.launch {
        // Use the same target language and custom instructions the Translate screen would.
        val settings = container.settingsRepository.current()
        val target =
          LanguageCatalog.targetOrNull(settings.targetLanguageCode) ?: container.defaultTargetLanguage()
        container.llmClient
          .translate(profile, SAMPLE_TEXT, target, null, settings.customPrompt)
          .onSuccess { outcome ->
            val reply =
              buildString {
                append(outcome.translation)
                outcome.sourceLanguage?.let { append("\n\n(").append(it.nativeName).append(")") }
              }
            _state.update {
              it.copy(
                test =
                  TestState.Success(
                    latencyMs = outcome.latencyMs,
                    reply = reply,
                    raw = "",
                    model = outcome.model,
                    modelsAvailable = null,
                    endpoint = EndpointResolver.chatCompletions(profile.baseUrl),
                  )
              )
            }
          }
          .onFailure { error ->
            if (error is CancellationException) throw error
            _state.update { it.copy(test = it.test.toFailure(error)) }
          }
      }
  }

  fun clearTest() = _state.update { it.copy(test = TestState.Idle) }

  private fun formAsProfile(): ApiProfile {
    val current = _state.value
    return ApiProfile(
      id = current.profileId ?: "unsaved",
      name = current.name.ifBlank { "unsaved" },
      baseUrl = current.baseUrl.trim(),
      model = current.model.trim().ifBlank { "unknown" },
      apiKey = current.apiKey.trim(),
      temperature = current.temperature,
      timeoutSeconds = current.timeoutSeconds,
      jsonMode = current.jsonMode,
      extraHeaders = parseHeaders(current.headersText),
    )
  }

  private fun TestState.toFailure(error: Throwable): TestState =
    if (error is LlmError.Cancelled) {
      TestState.Idle
    } else {
      TestState.Failure(message = LlmErrorMapper.toMessage(error), detail = LlmErrorMapper.detailOf(error))
    }

  companion object {
    const val SAMPLE_TEXT = "Hello, world!"

    fun factory(container: AppContainer) = viewModelFactory {
      initializer { ApiProfileEditorViewModel(container) }
    }

    /** Parses `Name: Value` lines, ignoring blanks and malformed rows. */
    fun parseHeaders(text: String): List<HeaderPair> =
      text.lines()
        .mapNotNull { line ->
          val separator = line.indexOf(':')
          if (separator <= 0) return@mapNotNull null
          val pair = HeaderPair(line.substring(0, separator).trim(), line.substring(separator + 1).trim())
          pair.takeIf { it.name.isNotEmpty() }
        }
  }
}
