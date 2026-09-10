package com.lingua.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lingua.app.data.crypto.SecretCipher
import com.lingua.app.data.remote.PromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Single source of truth for [AppSettings].
 *
 * Profiles are serialized to the `settings` store with their API key blanked out; the key itself
 * lives in the `secrets` store encrypted by [cipher]. The combine step re-attaches decrypted keys so
 * callers always see a complete [ApiProfile].
 */
class SettingsRepository(
  private val settingsStore: DataStore<Preferences>,
  private val secretsStore: DataStore<Preferences>,
  private val cipher: SecretCipher,
  private val json: Json = DefaultJson,
) {

  val settings: Flow<AppSettings> =
    combine(settingsStore.data, secretsStore.data) { preferences, secrets ->
      val stored = decode(preferences[KEY_SETTINGS])
      stored.copy(
        profiles = stored.profiles.map { profile ->
          val blob = secrets[apiKeyKey(profile.id)]
          profile.copy(apiKey = blob?.let { cipher.decrypt(it) }.orEmpty())
        }
      )
    }

  suspend fun current(): AppSettings = settings.first()

  /** Applies [transform] to the persisted, secret-free snapshot. */
  private suspend fun mutate(transform: (AppSettings) -> AppSettings) {
    settingsStore.edit { preferences ->
      val updated = transform(decode(preferences[KEY_SETTINGS]))
      preferences[KEY_SETTINGS] = json.encodeToString(updated.copy(profiles = updated.profiles.map { it.withoutSecret() }))
    }
  }

  suspend fun setThemeMode(mode: ThemeMode) = mutate { it.copy(themeMode = mode) }

  suspend fun setDynamicColor(enabled: Boolean) = mutate { it.copy(dynamicColor = enabled) }

  suspend fun setTargetLanguage(code: String?) = mutate { it.copy(targetLanguageCode = code) }

  suspend fun setAutoSaveHistory(enabled: Boolean) = mutate { it.copy(autoSaveHistory = enabled) }

  suspend fun setHistoryFavoritesOnly(enabled: Boolean) = mutate { it.copy(historySearchFavoritesOnly = enabled) }

  /** Stores the user's extra prompt instructions; blank clears them so only the built-in applies. */
  suspend fun setCustomPrompt(prompt: String) =
    mutate { it.copy(customPrompt = prompt.trim().take(PromptBuilder.MAX_CUSTOM_PROMPT_LENGTH)) }

  suspend fun setActiveProfile(id: String?) = mutate { it.copy(activeProfileId = id) }

  /** Inserts or replaces [profile] and stores its API key encrypted. */
  suspend fun upsertProfile(profile: ApiProfile) {
    withContext(Dispatchers.IO) {
      secretsStore.edit { preferences ->
        val key = apiKeyKey(profile.id)
        if (profile.apiKey.isBlank()) {
          preferences.remove(key)
        } else {
          cipher.encrypt(profile.apiKey)?.let { preferences[key] = it }
        }
      }
    }
    mutate { current ->
      val withoutSecret = profile.withoutSecret()
      val existingIndex = current.profiles.indexOfFirst { it.id == profile.id }
      val profiles =
        if (existingIndex >= 0) current.profiles.toMutableList().apply { set(existingIndex, withoutSecret) }
        else current.profiles + withoutSecret
      current.copy(
        profiles = profiles,
        activeProfileId = current.activeProfileId ?: profile.id,
      )
    }
  }

  suspend fun deleteProfile(id: String) {
    withContext(Dispatchers.IO) {
      secretsStore.edit { it.remove(apiKeyKey(id)) }
    }
    mutate { current ->
      val remaining = current.profiles.filterNot { it.id == id }
      current.copy(
        profiles = remaining,
        activeProfileId = if (current.activeProfileId == id) remaining.firstOrNull()?.id else current.activeProfileId,
      )
    }
  }

  private fun decode(raw: String?): AppSettings =
    if (raw.isNullOrBlank()) AppSettings()
    else runCatching { json.decodeFromString<AppSettings>(raw) }.getOrElse { AppSettings() }

  companion object {
    private val KEY_SETTINGS = stringPreferencesKey("settings_json")

    private fun apiKeyKey(profileId: String) = stringPreferencesKey("api_key_$profileId")

    val DefaultJson: Json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }
  }
}
