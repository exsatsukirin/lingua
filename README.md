# 灵译 Lingua

通过你自己的 LLM 接口翻译文本的 Android 应用。没有中间服务：文本只发送到你配置的
OpenAI 兼容端点（OpenAI、DeepSeek、Moonshot、硅基流动、Ollama、LM Studio、vLLM…）。

<p align="center">
  <em>Kotlin · Jetpack Compose · Material 3 · Room · DataStore · OkHttp</em>
</p>

## 功能

| 能力 | 说明 |
| --- | --- |
| 源语言自动识别 | 单次请求内由模型识别，结果与译文一起返回，界面显示"检测到：英语" |
| 目标语言可选 | 内置 65 种语言，默认跟随系统语言，选择结果持久化 |
| Material 3 界面 | 浅色 / 深色 / 跟随系统三态，Android 12+ 支持动态取色，宽屏自动切换 NavigationRail |
| 历史记录 | Room 本地数据库：自动保存、搜索、收藏、单条删除 + 撤销、清空、按日期分组 |
| API 配置可编辑 | 多套配置档案，预置服务商，Base URL / API Key / 模型 / 温度 / 超时 / JSON 模式 / 自定义请求头 |
| API 测试 | 连接测试（含 `/models` 探测）、真实翻译测试、模型列表拉取、原始响应查看 |
| API Key 加密 | AndroidKeyStore AES/GCM 加密后落盘，且从备份中排除 |
| 中英双语界面 | 默认英文，`values-zh-rCN` 提供完整简体中文 |

## 环境要求

| 组件 | 版本 |
| --- | --- |
| Android SDK | `platforms/android-36`、`build-tools;36.0.0`、`platform-tools` |
| 构建 JDK | 17+（本机使用 `/usr/lib/jvm/java-21-openjdk` 运行 Gradle） |
| Kotlin 工具链 | JDK 17，由 foojay resolver 自动下载 |
| Gradle | wrapper 9.1.0（AGP 9.0.1 要求 ≥ 9.1.0） |

SDK 位于用户可写目录（默认 `~/.android-sdk`），`local.properties` 中的 `sdk.dir` 指向它。
该文件与 `local.properties` 一样不入版本库。初始化：

```bash
android --sdk="$HOME/.android-sdk" sdk install platforms/android-36
# build-tools 与 platform-tools 会在首次构建时由 AGP 自动补齐（licenses 目录需存在）
```

## 构建与运行

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.lingua.app/.MainActivity
```

## 测试

### 单元测试

```bash
./gradlew :app:testDebugUnitTest
```

覆盖：模型响应解析容错（围栏 JSON、夹带散文、键名变体、纯文本回退）、端点 URL 推导
（含 Azure 风格完整 URL 与 query string）、错误映射、语言目录与 locale 匹配、提示词构造、
界面状态机、请求头解析与服务商预置。

### 本地 mock 端点

`tools/mock_llm_server.py` 是一个零依赖的 OpenAI 兼容假服务，用于端到端验证：

```bash
python3 tools/mock_llm_server.py --host 0.0.0.0 --port 8765
adb reverse tcp:8765 tcp:8765        # 让设备通过 127.0.0.1:8765 访问宿主机
```

在应用里新建配置：Base URL `http://127.0.0.1:8765/v1`、API Key `test-key`、模型 `mock-translate`。

故障注入通过**模型名**控制，无需改动服务端：

| 模型名 | 行为 |
| --- | --- |
| `mock-translate` | 正常返回 JSON 译文 |
| `mock-plain` | 返回纯文本，验证解析回退 |
| `mock-prose-json` | 返回夹带说明文字与围栏的 JSON |
| `error-400` / `error-401` / `error-404` / `error-429` / `error-500` | 返回对应 HTTP 状态码 |
| `error-malformed` | 返回非法 JSON，验证 `InvalidResponse` |
| `error-timeout` | 挂起 120 秒，验证超时处理 |

### 真机验收

`journeys/lingua_journey.xml` 是端到端验收脚本（翻译 → 历史 → 持久化 → API 测试 → 错误路径 → 主题）。
`tools/drive_ui.sh` 是配套的 adb 驱动脚本：

```bash
tools/drive_ui.sh dump             # 打印当前界面的可点击元素与坐标
tools/drive_ui.sh tap-text "保存"   # 按文本定位并点击
tools/drive_ui.sh screenshot /tmp/shot.png
```

## 配置说明

**Base URL 规则**：应用请求 `{base}/chat/completions`。`https://api.deepseek.com` 与
`https://api.openai.com/v1` 都可以；如果粘贴的是已经以 `/chat/completions` 结尾的完整
URL（Azure 部署地址常见），则原样使用。

**JSON 模式**：默认要求模型返回 `response_format: {"type":"json_object"}`。若端点不支持，
首次返回 400 且响应体提到 `response_format` 时，会自动去掉该字段重试一次。

**翻译协议**：系统提示词要求模型只返回

```json
{"source_language": "English", "source_language_code": "en", "translation": "…"}
```

解析器对真实模型的各种不守规矩行为做了容错：markdown 围栏、JSON 前后夹带说明、
键名变体（`translated_text` / `text` / `result`…）。完全无法解析时，整段回复会被当作译文，
源语言标记为未知。

## 架构

```
UI (Compose)  →  ViewModel (StateFlow)  →  TranslationRepository ─┬→ LlmClient (OkHttp) → LLM API
                                                                 └→ HistoryRepository (Room)
                     SettingsRepository (DataStore + AndroidKeyStore)
```

- 单模块、MVVM，`AppContainer`(AppContainer.kt) 手写依赖容器，不引入 DI 框架。
- `LlmClient` 只实现 `POST /chat/completions` 与 `GET /models`；所有网络与响应体读取都在
  `Dispatchers.IO` 上执行（响应体读取是阻塞式 socket 读，放在主线程会触发
  `NetworkOnMainThreadException`）。
- 单次请求超时通过 `withTimeout` 实现，取消协程会同时 `Call.cancel()`。
- API Key 与普通设置分开存放：`settings` 与 `secrets` 两个 DataStore 文件，
  后者被 `backup_rules.xml` / `data_extraction_rules.xml` 排除（Keystore 密钥不可跨设备恢复）。
- Room v1 未写迁移，因此**未**开启 `fallbackToDestructiveMigration`；升版需补 `Migration`。

## 明文流量

`network_security_config.xml` 允许明文 HTTP，因为自建端点（Ollama、LM Studio、局域网网关）
通常是 `http://`。公网 HTTPS 端点不受影响。若只使用 HTTPS，建议将
`cleartextTrafficPermitted` 改为 `false`。

## 已知限制

- 译文为一次性返回，暂不支持流式输出。
- 无 TTS / 语音输入 / 拍照 OCR / 术语表。
- `material-icons-extended` 使得 debug APK 偏大（约 21 MB）；release 构建开启 R8 后会自动裁剪。
- 单元测试覆盖纯逻辑（解析、URL、状态机、表单）；ViewModel 与 Repository 的协作目前由
  `journeys/lingua_journey.xml` 在真机上覆盖。
