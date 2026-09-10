package com.lingua.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lingua.app.AppContainer
import com.lingua.app.data.settings.AppSettings
import com.lingua.app.data.settings.ApiProfile
import com.lingua.app.data.settings.ThemeMode
import com.lingua.app.domain.Language
import com.lingua.app.domain.LanguageCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
  val settings: AppSettings = AppSettings(),
  val targetLanguage: Language,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

  val state: StateFlow<SettingsUiState> =
    container.settingsRepository.settings
      .map { settings ->
        val target =
          settings.targetLanguageCode?.let { LanguageCatalog.targetOrNull(it) }
            ?: container.defaultTargetLanguage()
        SettingsUiState(settings = settings, targetLanguage = target)
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(targetLanguage = container.defaultTargetLanguage()),
      )

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
  }

  fun setDynamicColor(enabled: Boolean) {
    viewModelScope.launch { container.settingsRepository.setDynamicColor(enabled) }
  }

  fun setAutoSaveHistory(enabled: Boolean) {
    viewModelScope.launch { container.settingsRepository.setAutoSaveHistory(enabled) }
  }

  fun setTargetLanguage(language: Language) {
    viewModelScope.launch { container.settingsRepository.setTargetLanguage(language.code) }
  }

  fun setActiveProfile(profile: ApiProfile) {
    viewModelScope.launch { container.settingsRepository.setActiveProfile(profile.id) }
  }

  fun deleteProfile(profile: ApiProfile) {
    viewModelScope.launch { container.settingsRepository.deleteProfile(profile.id) }
  }

  companion object {
    fun factory(container: AppContainer) = viewModelFactory {
      initializer { SettingsViewModel(container) }
    }
  }
}
