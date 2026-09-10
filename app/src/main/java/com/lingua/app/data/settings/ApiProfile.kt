package com.lingua.app.data.settings

import kotlinx.serialization.Serializable

/** How the app picks between light and dark. */
enum class ThemeMode {
  System,
  Light,
  Dark;

  companion object {
    fun fromStorage(raw: String?): ThemeMode =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: System
  }
}

/** A single `Name: Value` HTTP header the user wants attached to every request. */
@Serializable
data class HeaderPair(val name: String, val value: String)

/**
 * One user-editable LLM endpoint configuration.
 *
 * [apiKey] is held in plaintext in memory only; [SettingsRepository] strips it before the profile is
 * serialized to disk and stores it separately through [com.lingua.app.data.crypto.SecretCipher].
 */
@Serializable
data class ApiProfile(
  val id: String,
  val name: String,
  val baseUrl: String,
  val model: String,
  val apiKey: String = "",
  val temperature: Double = DEFAULT_TEMPERATURE,
  val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
  val jsonMode: Boolean = true,
  val extraHeaders: List<HeaderPair> = emptyList(),
) {
  /** Copy safe to persist: the API key is removed. */
  fun withoutSecret(): ApiProfile = copy(apiKey = "")

  val displayName: String
    get() = name.ifBlank { model.ifBlank { baseUrl } }

  companion object {
    const val DEFAULT_TEMPERATURE = 0.2
    const val DEFAULT_TIMEOUT_SECONDS = 60
    const val MIN_TIMEOUT_SECONDS = 10
    const val MAX_TIMEOUT_SECONDS = 300
  }
}

/** Everything the app persists apart from the encrypted API keys. */
@Serializable
data class AppSettings(
  val profiles: List<ApiProfile> = emptyList(),
  val activeProfileId: String? = null,
  /** `null` means "follow the system language". */
  val targetLanguageCode: String? = null,
  val themeMode: ThemeMode = ThemeMode.System,
  val dynamicColor: Boolean = true,
  val autoSaveHistory: Boolean = true,
  val historySearchFavoritesOnly: Boolean = false,
  /**
   * User instructions appended to the immutable built-in prompt. Empty means "built-in only".
   * Added after v1 shipped: the default keeps older stored settings readable.
   */
  val customPrompt: String = "",
) {
  val activeProfile: ApiProfile?
    get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
}
