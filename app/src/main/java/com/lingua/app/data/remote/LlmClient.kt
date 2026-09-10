package com.lingua.app.data.remote

import com.lingua.app.data.settings.ApiProfile
import com.lingua.app.domain.Language
import com.lingua.app.domain.LanguageCatalog
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** A finished chat completion. */
data class Completion(
  val text: String,
  val model: String?,
  val latencyMs: Long,
  val rawResponse: String,
)

/** Result of a successful translation round trip. */
data class TranslationOutcome(
  val translation: String,
  val sourceLanguage: Language?,
  val sourceLanguageLabel: String?,
  val model: String?,
  val latencyMs: Long,
)

/** Result of the "test connection / test translation" buttons in the API editor. */
data class TestOutcome(
  val endpoint: String,
  val latencyMs: Long,
  val model: String?,
  val reply: String,
  val rawResponse: String,
)

/**
 * Minimal OpenAI-compatible chat client. Deliberately supports only `POST /chat/completions` and
 * `GET /models`, which every mainstream provider (OpenAI, DeepSeek, Moonshot, SiliconFlow, Ollama,
 * LM Studio, vLLM…) implements.
 */
class LlmClient(
  private val httpClient: OkHttpClient = defaultHttpClient(),
  private val json: Json = RemoteJson,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  suspend fun translate(
    profile: ApiProfile,
    text: String,
    target: Language,
    sourceOverride: Language? = null,
  ): Result<TranslationOutcome> =
    runRequest {
      val messages =
        listOf(
          ChatMessage(role = "system", content = PromptBuilder.systemPrompt(target, sourceOverride)),
          ChatMessage(role = "user", content = text),
        )
      val completion = chatCompletion(profile, messages)
      val parsed = TranslationResponseParser.parse(completion.text)
      if (parsed == null) throw LlmError.InvalidResponse(completion.rawResponse.truncateForDisplay())

      TranslationOutcome(
        translation = parsed.translation,
        sourceLanguage =
          LanguageCatalog.matchByLabel(parsed.sourceLanguageCode)
            ?: LanguageCatalog.matchByLabel(parsed.sourceLanguageName),
        sourceLanguageLabel = parsed.sourceLanguageName,
        model = completion.model,
        latencyMs = completion.latencyMs,
      )
    }

  suspend fun listModels(profile: ApiProfile): Result<List<String>> =
    runRequest {
      val request = baseRequest(profile, EndpointResolver.models(profile.baseUrl)).get().build()
      executeWithTimeout(profile, request).use { response ->
        val body = response.body.string()
        if (!response.isSuccessful) throw httpError(response.code, body)
        runCatching { json.decodeFromString<ModelsResponse>(body).data.map { it.id } }
          .getOrElse { throw LlmError.InvalidResponse(body.truncateForDisplay()) }
      }
    }

  suspend fun testChat(profile: ApiProfile, sample: String): Result<TestOutcome> =
    runRequest {
      val messages = listOf(ChatMessage(role = "user", content = sample))
      val completion = chatCompletion(profile, messages, jsonMode = false)
      TestOutcome(
        endpoint = EndpointResolver.chatCompletions(profile.baseUrl),
        latencyMs = completion.latencyMs,
        model = completion.model,
        reply = completion.text,
        rawResponse = completion.rawResponse,
      )
    }

  private suspend fun chatCompletion(
    profile: ApiProfile,
    messages: List<ChatMessage>,
    jsonMode: Boolean = profile.jsonMode,
  ): Completion {
    try {
      return postChatCompletion(profile, messages, jsonMode)
    } catch (error: LlmError.BadRequest) {
      // Providers without `response_format` support answer 400 and mention the field. Retry once.
      val mentionsResponseFormat = error.body?.contains("response_format") == true
      if (!jsonMode || !mentionsResponseFormat) throw error
      return postChatCompletion(profile, messages, jsonMode = false)
    }
  }

  private suspend fun postChatCompletion(
    profile: ApiProfile,
    messages: List<ChatMessage>,
    jsonMode: Boolean,
  ): Completion {
    val payload =
      ChatCompletionRequest(
        model = profile.model,
        messages = messages,
        temperature = profile.temperature,
        stream = false,
        responseFormat = if (jsonMode) ResponseFormat("json_object") else null,
      )

    val request =
      baseRequest(profile, EndpointResolver.chatCompletions(profile.baseUrl))
        .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
        .build()

    val startedAt = System.nanoTime()
    executeWithTimeout(profile, request).use { response ->
      val body = response.body.string()
      val latencyMs = (System.nanoTime() - startedAt) / 1_000_000
      if (!response.isSuccessful) throw httpError(response.code, body)

      val decoded =
        runCatching { json.decodeFromString<ChatCompletionResponse>(body) }
          .getOrElse { throw LlmError.InvalidResponse(body.truncateForDisplay()) }
      val content = decoded.choices.firstOrNull()?.message?.content
      if (content.isNullOrBlank()) throw LlmError.InvalidResponse(body.truncateForDisplay())

      return Completion(
        text = content,
        model = decoded.model ?: profile.model,
        latencyMs = latencyMs,
        rawResponse = body.truncateForDisplay().orEmpty(),
      )
    }
  }

  private fun baseRequest(profile: ApiProfile, url: String): Request.Builder {
    val builder = Request.Builder().url(url).header("Accept", "application/json")
    if (profile.apiKey.isNotBlank()) {
      builder.header("Authorization", "Bearer ${profile.apiKey}")
    }
    profile.extraHeaders.forEach { header ->
      if (header.name.isNotBlank()) builder.header(header.name.trim(), header.value)
    }
    return builder
  }

  private suspend fun executeWithTimeout(profile: ApiProfile, request: Request): Response {
    val call: Call = httpClient.newCall(request)
    val timeoutMillis = profile.timeoutSeconds.coerceIn(ApiProfile.MIN_TIMEOUT_SECONDS, ApiProfile.MAX_TIMEOUT_SECONDS) * 1000L
    return try {
      withTimeout(timeoutMillis) { call.executeAsync() }
    } catch (error: TimeoutCancellationException) {
      throw LlmError.Timeout
    }
  }

  private fun httpError(code: Int, body: String): LlmError {
    val detail = body.truncateForDisplay()
    val apiMessage = runCatching { json.decodeFromString<ApiErrorEnvelope>(body).error?.message }.getOrNull()
    val message = apiMessage?.truncateForDisplay(500) ?: detail
    return when {
      code == 401 || code == 403 -> LlmError.Unauthorized(code, message)
      code == 404 -> LlmError.ModelOrEndpointNotFound(code, message)
      code == 429 -> LlmError.RateLimited(code, message)
      code in 400..499 -> LlmError.BadRequest(code, message)
      code >= 500 -> LlmError.ServerError(code, message)
      else -> LlmError.ServerError(code, message)
    }
  }

  /**
   * Runs [block] on [ioDispatcher], converting transport failures into [LlmError] while letting
   * cancellation through.
   *
   * The dispatcher matters: `enqueue` hands the response back to whichever thread the calling
   * coroutine resumes on, and reading the body is a blocking socket read. Callers live in
   * `viewModelScope` (main), so without this the body read trips `NetworkOnMainThreadException`.
   */
  private suspend fun <T> runRequest(block: suspend () -> T): Result<T> =
    try {
      Result.success(withContext(ioDispatcher) { block() })
    } catch (cancellation: CancellationException) {
      if (cancellation is TimeoutCancellationException) Result.failure(LlmError.Timeout) else throw cancellation
    } catch (error: LlmError) {
      Result.failure(error)
    } catch (error: IllegalArgumentException) {
      Result.failure(LlmError.InvalidUrl)
    } catch (error: UnknownHostException) {
      Result.failure(LlmError.Network(error.message ?: "Unknown host"))
    } catch (error: SocketTimeoutException) {
      Result.failure(LlmError.Timeout)
    } catch (error: SSLException) {
      Result.failure(LlmError.Network(error.message ?: "TLS error"))
    } catch (error: IOException) {
      val reason = error.message.orEmpty()
      Result.failure(if (reason.contains("Canceled", ignoreCase = true)) LlmError.Cancelled else LlmError.Network(reason))
    } catch (error: Throwable) {
      Result.failure(LlmError.Network(error.message))
    }

  companion object {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    val RemoteJson: Json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = false
      explicitNulls = false
      isLenient = true
    }

    fun defaultHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Per-request read limits are enforced with withTimeout() so the shared client stays reusable.
        .readTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
  }
}
