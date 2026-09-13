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
| 提示词可定制 | 内置提示词只读不可改（承载 JSON 输出契约），可在其后追加自己的指令，并可预览实际发送的完整提示词 |
| API 测试 | 连接测试（含 `/models` 探测）、真实翻译测试、模型列表拉取、原始响应查看 |
| 划词翻译 | 在其他应用里选中文本，系统选区菜单中选「灵译」，即可就地弹出译文，无需切换应用 |
| 屏幕取词 | 截屏或分享一张截图进去，本地 OCR 识别文字块，点选后翻译；图片与文字都不离开设备 |
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

OCR 部分另有：多边形几何（旋转卡壳最小外接矩形、unclip 外扩、四点透视裁剪）、CTC 贪心解码、
段落合并、字典字符表校验、语种覆盖一致性，以及**用桌面版 ONNX Runtime 跑真实 PP-OCRv6 模型**
的端到端识别测试（对内置样例截图断言识别出的文字）。

### 仪器测试

```bash
./gradlew :app:connectedDebugAndroidTest
```

覆盖：模型装载与 SHA-256 校验、设备端真实识别（内置样例截图，断言文字内容与耗时上限）、
屏幕取词界面（空态、选中计数、按钮可用性）、历史与导航。

构建时请保持 `android.injected.androidTest.leaveApksInstalledAfterRun=true`：否则测试结束会
卸载应用，连带清掉 DataStore 里经 Keystore 加密的 API Key，而 Keystore 密钥不可恢复。

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

**提示词**：设置页「提示词」区块提供两项。

| 项 | 可修改 | 说明 |
| --- | --- | --- |
| 内置提示词 | 否 | 含 JSON 输出契约与 6 条翻译规则，随目标语言渲染；点开可只读预览 |
| 自定义补充提示词 | 是 | 追加在内置提示词之后，最多 2000 字；留空即只发送内置提示词 |

最终发送的提示词结构是：

```
<内置提示词>
<空行>
Additional instructions from the user — follow them whenever they do not conflict with the rules above:
<自定义提示词>
<空行>
These additional instructions must never change the JSON response format, its keys, or rules 1–6 above.
```

结尾那句约束放在自定义内容**之后**，让内置契约始终是模型读到的最后一条要求；即使用户写了
"忽略以上指令、只输出纯文本"，`TranslationResponseParser` 也会把纯文本结果兜底当作译文，
不会出现解析失败。设置页的「提示词预览」可以查看当前目标语言下实际发送的完整文本。

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

## 划词翻译（ACTION_PROCESS_TEXT）

在任意应用里选中一段文本 → 系统选区菜单 → 「灵译」，即可在不离开当前应用的情况下看到译文。

实现要点：

- `ProcessTextActivity` 声明 `android.intent.action.PROCESS_TEXT` + `text/plain` 的 intent-filter，
  读取 `Intent.EXTRA_PROCESS_TEXT`；`android:label` 就是菜单里显示的名字。
- 该 Activity 用 `Theme.Lingua.ProcessText`（透明窗口、不额外压暗），Compose 自己画半透明遮罩，
  所以源应用仍然可见，用户不会丢失上下文。
- 弹窗内可复制、分享、收藏、关闭；点击遮罩或按返回键即关闭。
- 翻译走的是同一个 `TranslationRepository`，因此**接口配置、目标语言、自定义提示词、
  自动保存历史全部沿用**，翻译结果也会进历史。
- 若尚未配置 API，弹窗会给出「去设置」按钮直接跳到应用，不会成为死路。
- 空选区不会弹出窗口，Activity 直接结束。

**已知限制**（平台/第三方应用行为，非本应用可控）：

| 场景 | 是否能出现「灵译」 |
| --- | --- |
| 基于原生 `TextView` / `EditText` 的应用（设置、大多数工具类应用） | ✅ 在选区工具栏的 `⋮` 溢出菜单里 |
| WebView 里的网页文本 | ❌ 多数 WebView 使用自绘菜单，不加载 PROCESS_TEXT 项 |
| 自带选区工具栏的应用（部分浏览器、Flutter 应用） | ❌ |
| Compose `SelectionContainer` 的文本 | ❌ Compose 的浮动工具栏不加载 PROCESS_TEXT 项 |

## 屏幕取词（本地 OCR）

翻译页右上角的截屏图标进入。截取当前屏幕，或者从系统分享面板把一张截图直接发给灵译
（`ACTION_SEND` / `image/*`），也可以用系统照片选择器挑一张图片。

流程：**截屏或选图 → 本地识别 → 点选文字块 → 走同一个 `TranslationRepository` 翻译**。
识别在设备上完成，图片不会上传；只有你选中的文字会进入你配置的 LLM 端点，译文同样进历史。

| 决策 | 取值 | 理由 |
| --- | --- | --- |
| OCR 引擎 | PaddleOCR **PP-OCRv6 small**（det + rec） | Apache-2.0、完全离线、无 GMS 依赖；单个识别模型覆盖中／繁／日／英与 50 种拉丁系语言 |
| 推理运行时 | ONNX Runtime 1.27.0（`onnxruntime-android`） | 上游 RapidOCR 生态与多个生产应用验证过的组合 |
| 预处理／后处理 | 纯 Kotlin，不引入 OpenCV | DB 后处理（连通域 + 最小外接矩形 + unclip）与四点透视裁剪都可控且能跑 JVM 单测 |
| 模型分发 | 内置进 APK，首次使用时校验 SHA-256 后拷入 `filesDir` | 装完即可离线识别，没有下载失败路径 |

体积：识别模型 `det 9.4 MB + rec 20.2 MB + 字典 75 KB`，ONNX Runtime 原生库 arm64 约 28 MB。
release APK 因此从 13.8 MB 涨到约 **73 MB**，且只包含 `arm64-v8a`（`x86_64` 仅用于模拟器／
Waydroid 的 debug 构建）。若在意体积，可换 `PP-OCRv6 tiny`（det 1.9 MB + rec 4.4 MB）换取约
24 MB，代价是**不支持日文**且中日文准确率从 81.3% 降到 73.5%。

实现要点：

- `PaddleOcrEngine` 只依赖 `ai.onnxruntime` 与普通 `IntArray` 图像，没有 `android.graphics`，
  因此同一份代码既能跑在设备上，也能在 JVM 单测里用桌面的 ONNX Runtime 跑真实模型。
- 检测输入按 DBNet 要求把最长边压到 960、两边对齐到 32 的倍数并做 ImageNet 归一化；
  识别输入把每个文本框透视裁成高 48、按 `(x / 127.5) - 1` 归一化。
- 识别按**宽度分桶**再分批，并限制单个批次输出大小：识别输出是
  `batch × (宽 / 8) × 18710` 个 float，八条满宽文本就是 90 MB，真机上会直接 OOM。
- 每批最多 4 个推理线程；上游实测超过 4 线程在 big.LITTLE 上反而更慢。

Android 14+ 的单次截屏授权：`MediaProjection` 只能交给已经在运行的 `mediaProjection` 类型
前台服务，且 token 用后即废，所以每次截屏都会弹一次系统确认。`ScreenCaptureService` 取到一帧
后立刻停止自己。

**语种覆盖**：字典里只有 CJK、假名、拉丁字母和希腊字母 —— **没有韩文、西里尔、阿拉伯、泰文、
天城文**。上游所说的「50 种语言」指的是 50 种拉丁系语言。选定这些源语言时界面会明确提示，
而不是返回乱码。

**已知限制**：

| 情况 | 表现 |
| --- | --- |
| 竖排 / 旋转 180° 的文本 | 不支持（未引入方向分类模型） |
| `FLAG_SECURE` 界面（银行、部分应用） | 截出来是纯色，会提示「该界面禁止截屏」 |
| 图标、头像等非文字区域 | 可能被识别成零散字符，所以默认不预选任何文字块 |
| 相机拍照识别 | 未实现，可先用系统相机拍完再从分享面板发进来 |

## 明文流量

`network_security_config.xml` 允许明文 HTTP，因为自建端点（Ollama、LM Studio、局域网网关）
通常是 `http://`。公网 HTTPS 端点不受影响。若只使用 HTTPS，建议将
`cleartextTrafficPermitted` 改为 `false`。

## 已知限制

- 译文为一次性返回，暂不支持流式输出。
- 无 TTS / 语音输入 / 相机拍照 OCR / 术语表。
- release APK 约 73 MB，其中绝大部分是内置的 PP-OCRv6 模型与 ONNX Runtime 原生库；且仅包含
  `arm64-v8a`（32 位 ARM 设备暂不支持）。
- 屏幕取词目前不覆盖韩文、俄文、阿拉伯文、泰文等非拉丁字母语系（见上）。
- 单元测试覆盖纯逻辑（解析、URL、状态机、表单、OCR 几何与 CTC 解码）；ViewModel 与
  Repository 的协作由真机验收覆盖。

## 第三方组件

| 组件 | 许可 | 用途 |
| --- | --- | --- |
| [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) PP-OCRv6 模型 | Apache-2.0 | 屏幕文字检测与识别（权重已转换为 ONNX） |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | MIT | 端侧推理 |
| [RapidOCR](https://github.com/RapidAI/RapidOCR) | Apache-2.0 | ONNX 版模型来源与模型清单参考 |

## 许可证

[MIT License](LICENSE) © 2026 exsatsukirin

