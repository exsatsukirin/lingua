package com.lingua.app.domain

import com.lingua.app.data.history.HistoryRepository
import com.lingua.app.data.history.TranslationRecord
import com.lingua.app.data.remote.LlmClient
import com.lingua.app.data.remote.TranslationOutcome
import com.lingua.app.data.settings.SettingsRepository

/** A translation plus the history row it produced, if any. */
data class TranslationResult(
  val outcome: TranslationOutcome,
  val savedRecordId: Long?,
)

/**
 * Orchestrates one translation: pick the active profile, call the model, and (when the user has not
 * switched it off) store the result in history.
 */
class TranslationRepository(
  private val llmClient: LlmClient,
  private val historyRepository: HistoryRepository,
  private val settingsRepository: SettingsRepository,
) {

  suspend fun translate(
    sourceText: String,
    target: Language,
    sourceOverride: Language? = null,
  ): Result<TranslationResult> {
    val settings = settingsRepository.current()
    val profile = settings.activeProfile ?: return Result.failure(NoActiveProfile)
    val trimmed = sourceText.trim()

    return llmClient
      .translate(
        profile = profile,
        text = trimmed,
        target = target,
        sourceOverride = sourceOverride,
        customInstructions = settings.customPrompt,
      )
      .map { outcome ->
        val recordId =
          if (settings.autoSaveHistory) {
            save(outcome, trimmed, target, profile.displayName)
          } else {
            null
          }
        TranslationResult(outcome = outcome, savedRecordId = recordId)
      }
  }

  /** Saves a result the user asked to keep explicitly. Returns the new row id. */
  suspend fun save(
    outcome: TranslationOutcome,
    sourceText: String,
    target: Language,
    providerName: String,
  ): Long =
    historyRepository.insert(
      outcome.toRecord(sourceText = sourceText.trim(), target = target, providerName = providerName)
    )

  /** Returned when translating is attempted before any endpoint has been configured. */
  data object NoActiveProfile : Exception("no active API profile")
}

private fun TranslationOutcome.toRecord(
  sourceText: String,
  target: Language,
  providerName: String,
): TranslationRecord =
  TranslationRecord(
    sourceText = sourceText,
    translatedText = translation,
    sourceLangCode = sourceLanguage?.code ?: UNKNOWN_LANGUAGE_CODE,
    sourceLangName = sourceLanguage?.nativeName ?: sourceLanguageLabel ?: "",
    targetLangCode = target.code,
    targetLangName = target.nativeName,
    providerName = providerName,
    model = model.orEmpty(),
    createdAt = System.currentTimeMillis(),
  )

const val UNKNOWN_LANGUAGE_CODE = "unknown"
