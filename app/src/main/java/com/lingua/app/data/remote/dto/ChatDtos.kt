package com.lingua.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ChatMessage(val role: String, val content: String)

@Serializable data class ResponseFormat(val type: String)

@Serializable
data class ChatCompletionRequest(
  val model: String,
  val messages: List<ChatMessage>,
  val temperature: Double? = null,
  val stream: Boolean = false,
  @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)

@Serializable
data class ChatChoice(
  val index: Int = 0,
  val message: ChatMessage? = null,
  @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Usage(
  @SerialName("prompt_tokens") val promptTokens: Int? = null,
  @SerialName("completion_tokens") val completionTokens: Int? = null,
  @SerialName("total_tokens") val totalTokens: Int? = null,
)

@Serializable
data class ChatCompletionResponse(
  val id: String? = null,
  val model: String? = null,
  val choices: List<ChatChoice> = emptyList(),
  val usage: Usage? = null,
)

@Serializable data class ModelInfo(val id: String)

@Serializable data class ModelsResponse(val data: List<ModelInfo> = emptyList())

@Serializable data class ApiErrorBody(val message: String? = null, val type: String? = null, val code: String? = null)

@Serializable data class ApiErrorEnvelope(val error: ApiErrorBody? = null)
