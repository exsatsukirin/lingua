package com.lingua.app.ui.settings

import androidx.annotation.StringRes
import com.lingua.app.R

/**
 * Endpoint presets. Choosing one only prefills the form — every field stays editable, so a preset is
 * a starting point rather than a lock-in.
 */
enum class ProviderPreset(
  @param:StringRes val labelRes: Int,
  val baseUrl: String,
  val model: String,
) {
  OpenAI(R.string.preset_openai, "https://api.openai.com/v1", "gpt-4o-mini"),
  DeepSeek(R.string.preset_deepseek, "https://api.deepseek.com/v1", "deepseek-chat"),
  Moonshot(R.string.preset_moonshot, "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
  SiliconFlow(R.string.preset_siliconflow, "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct"),
  Ollama(R.string.preset_ollama, "http://192.168.1.2:11434/v1", "qwen2.5:7b"),
  Custom(R.string.preset_custom, "", ""),
  ;

  /** Best-effort detection so editing an existing profile highlights the matching preset. */
  companion object {
    fun forBaseUrl(baseUrl: String): ProviderPreset =
      entries.firstOrNull { it != Custom && it.baseUrl.isNotBlank() && baseUrl.startsWith(it.baseUrl) } ?: Custom
  }
}
