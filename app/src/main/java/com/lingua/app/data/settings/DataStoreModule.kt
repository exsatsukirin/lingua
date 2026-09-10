package com.lingua.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Non-secret settings (profiles without API keys, target language, theme, toggles).
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Keystore-encrypted API keys. Kept in a separate file so that backup rules can exclude exactly one
 * file (`datastore/secrets.preferences_pb`) while still backing up ordinary settings.
 */
val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(name = "secrets")
