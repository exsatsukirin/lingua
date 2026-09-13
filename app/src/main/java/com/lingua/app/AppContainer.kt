package com.lingua.app

import android.content.Context
import com.lingua.app.capture.ScreenCaptureController
import com.lingua.app.data.crypto.KeystoreSecretCipher
import com.lingua.app.data.crypto.SecretCipher
import com.lingua.app.data.history.HistoryDatabase
import com.lingua.app.data.history.HistoryRepository
import com.lingua.app.data.history.TranslationDao
import com.lingua.app.data.ocr.OcrRepository
import com.lingua.app.data.ocr.PaddleModelStore
import com.lingua.app.data.remote.LlmClient
import com.lingua.app.data.settings.SettingsRepository
import com.lingua.app.data.settings.settingsDataStore
import com.lingua.app.data.settings.secretsDataStore
import com.lingua.app.domain.LanguageCatalog
import com.lingua.app.domain.TranslationRepository

/**
 * Hand-rolled dependency container. The app is small enough that a DI framework would cost more
 * (build plugins, annotation processing) than the ~40 lines it saves.
 */
class AppContainer(context: Context) {

  /** Application context, exposed for the few collaborators that need a ContentResolver. */
  val appContext: Context = context.applicationContext

  val secretCipher: SecretCipher = KeystoreSecretCipher()

  val settingsRepository: SettingsRepository =
    SettingsRepository(
      settingsStore = appContext.settingsDataStore,
      secretsStore = appContext.secretsDataStore,
      cipher = secretCipher,
    )

  private val database: HistoryDatabase = HistoryDatabase.build(appContext)

  private val translationDao: TranslationDao = database.translationDao()

  val historyRepository: HistoryRepository = HistoryRepository(translationDao)

  val llmClient: LlmClient = LlmClient()

  val translationRepository: TranslationRepository =
    TranslationRepository(
      llmClient = llmClient,
      historyRepository = historyRepository,
      settingsRepository = settingsRepository,
    )

  /** On-device OCR for the screen-text feature. Models are installed on first use. */
  val ocrRepository: OcrRepository = OcrRepository(PaddleModelStore(appContext))

  /** One-shot screen capture; owns the foreground service that Android 14+ requires. */
  val screenCapture: ScreenCaptureController = ScreenCaptureController(appContext)

  /** Target language used before the user has picked one: the system language when supported. */
  fun defaultTargetLanguage(localeTag: String? = java.util.Locale.getDefault().toLanguageTag()) =
    LanguageCatalog.matchByLocale(localeTag) ?: LanguageCatalog.languages.first { it.code == "en" }
}
