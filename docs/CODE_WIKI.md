# GenBrush Code Wiki

> 面向 v1.2 代码状态的结构化代码地图：模块职责、关键类型、数据流、构建与约定。
>
> **事实源**：`CLAUDE.md` 描述项目结构与开发约定，`README.md` 面向使用者，本文是详细版。三者冲突时以 `CLAUDE.md` 为准。
>
> **历史说明**：v1.1 及更早版本的 SD WebUI 后端、LoRA 逻辑、`data/remote/` 与「双后端分发」已在 v1.2 全部移除，本文中不再保留相关章节。需要了解演进背景请查看 git 历史（`b99a467`、`84c6328`）。

## 目录

1. [项目概览](#1-项目概览)
2. [分层架构](#2-分层架构)
3. [目录结构](#3-目录结构)
4. [模块职责](#4-模块职责)
5. [关键类型](#5-关键类型)
6. [两个提供商对照](#6-两个提供商对照)
7. [核心流程](#7-核心流程)
8. [构建、签名与 CI](#8-构建签名与-ci)
9. [约定与已知限制](#9-约定与已知限制)
10. [版本历史](#10-版本历史)

---

## 1. 项目概览

| 属性 | 值 |
|---|---|
| 应用 / 包名 | GenBrush / `com.example.genbrush` |
| Gradle 模块 | 单模块 `:app` |
| 当前版本 | 1.2（versionCode 3，尚未打 tag 发布） |
| 语言 / 字节码 | Kotlin 2.2.10 / Java 11 |
| UI | Jetpack Compose + Material 3（BOM 2026.02.01） |
| 架构 | MVVM + Repository + Provider 抽象，手写依赖装配 |
| 源码规模 | 约 7,000 行 Kotlin（52 个 `.kt`，含测试） |
| SDK | min 24 / target 36 / compile 36 |
| 构建 | Gradle 9.4.1（Kotlin DSL）+ AGP 9.2.1 + KSP 2.3.6，JDK 17+ |

核心功能：文生图、图像编辑、多提供商切换、模型管理、相册（搜索/筛选/排序/收藏/批量操作）、全屏查看、ZIP 数据迁移、诊断日志导出、API Key 加密存储、中英双语、Material 3 动态取色。

## 2. 分层架构

```
┌───────────────────────────────────────────────────────────────┐
│ UI 层 (ui/)  Compose Screens + ViewModel(StateFlow)            │
│  TextToImage      ImageEdit      Gallery      Settings         │
│  └─ FullImageViewerScreen（详情路由，直接使用 repository）      │
├───────────────────────────────────────────────────────────────┤
│ GenBrushApp（Application）：OkHttp / Gson / Coil / 依赖装配     │
│ AppNavigation：NavHost + 底部导航 + savedStateHandle 回传      │
├───────────────────────────────┬───────────────────────────────┤
│ GenerationRepository          │ PreferencesManager            │
│ 解析 provider、凭据/日志/取消  │ EncryptedSharedPreferences    │
│ 结果落盘入库                  │ configVersion 联动信号        │
├───────────────────────────────┼───────────────────────────────┤
│ ProviderRegistry              │ 本地存储 (data/local/)        │
│  ├─ DashScopeProvider         │  ImageStore → Room(images)    │
│  │   └─ DashScopeApi          │  图片文件 generated_images/   │
│  └─ AgnesProvider             │  DataMigrationManager (ZIP)   │
│      └─ AgnesApi              │  ImageSaver (MediaStore)      │
├───────────────────────────────┴───────────────────────────────┤
│ data/network/：executeCancellable、NetworkLoggingInterceptor   │
│ data/local/AppLogStore：512KB 滚动日志（脱敏 + 导出）           │
└───────────────────────────────────────────────────────────────┘
```

分层原则：Provider 只负责调用远端拿图（URL 或字节），Repository 负责编排与持久化，UI 不感知具体提供商；`ImageEntry` DTO 隔离 Room 实体。

## 3. 目录结构

```
app/src/main/java/com/example/genbrush/
├── GenBrushApp.kt                   # Application：客户端、注册表、依赖装配、语言流
├── MainActivity.kt                  # 入口 Activity
├── data/
│   ├── local/
│   │   ├── db/                      # AppDatabase / ImageDao / ImageEntity
│   │   ├── AppLogStore.kt           # 诊断日志
│   │   ├── DataMigrationManager.kt  # ZIP 导入导出
│   │   ├── ImageSaver.kt            # 保存到系统相册
│   │   ├── ImageStore.kt            # 图片文件 + Room 封装 + 旧 JSON 迁移
│   │   └── PreferencesManager.kt    # 加密偏好
│   ├── network/
│   │   ├── OkHttpExtensions.kt      # executeCancellable
│   │   └── NetworkLoggingInterceptor.kt
│   ├── provider/
│   │   ├── ImageProvider.kt         # 接口 + 通用请求/结果/异常
│   │   ├── ProviderDefinition.kt    # 元数据与 SizeOption
│   │   ├── ProviderRegistry.kt      # 注册表
│   │   ├── dashscope/               # Provider / Definition / Api / ApiModels
│   │   └── agnes/                   # Provider / Definition / Api / ApiModels
│   └── repository/
│       └── GenerationRepository.kt
└── ui/
    ├── common/ErrorMapper.kt
    ├── components/                  # ModelSelector、SizeSelector、ImageResultCard、
    │                                # ErrorResultCard、LoadingOverlay
    ├── gallery/                     # GalleryScreen / GalleryViewModel / FullImageViewerScreen
    ├── imageedit/                   # ImageEditScreen / ImageEditViewModel
    ├── texttoimage/                 # TextToImageScreen / TextToImageViewModel
    ├── settings/                    # SettingsScreen / SettingsViewModel
    ├── localization/                # AppStrings / LocalStrings
    ├── navigation/                  # Screen / AppNavigation
    └── theme/                       # Color / Theme / Type
```

资源：`res/xml/` 下含 `network_security_config.xml`、`file_provider_paths.xml`、`backup_rules.xml`、`data_extraction_rules.xml`；测试位于 `app/src/test/`（5 个 JVM 测试类）与 `app/src/androidTest/`（模板）。

## 4. 模块职责

### 4.1 入口与依赖装配

| 文件 | 职责 |
|---|---|
| `GenBrushApp.kt` | 初始化 `AppLogStore`、`PreferencesManager`；构建单一 `OkHttpClient`（60s 连接 / 300s 读写 / 360s 调用超时，挂载 `NetworkLoggingInterceptor`，DEBUG 另加 BODY 日志并脱敏 `Authorization`、`X-API-Key`）；构造 `ProviderRegistry(DashScopeProvider, AgnesProvider)` 与 `GenerationRepository`；配置 Coil `SingletonImageLoader`（内存 25% / 磁盘 2%）；以 `language: StateFlow<String>` 驱动全局语言切换 |
| `MainActivity.kt` | `ComponentActivity` + edge-to-edge，从 `application` 取依赖，在 `GenBrushTheme` 中挂载 `AppNavigation` |
| `AppNavigation.kt` / `Screen.kt` | 四个底部 Tab 路由 + `gallery/image/{imageId}` 详情；通过 `viewModel(factory = ...)` 创建各 ViewModel；用 `savedStateHandle` 回传「重新生成」（prompt/model/size/negativePrompt）与「编辑此图」（图片路径） |

### 4.2 Provider 层

| 文件 | 职责 |
|---|---|
| `ImageProvider.kt` | `generateTextToImage` / `generateImageEdit` / `supportedSizes` / 可选 `fetchModels`；定义 `ProviderImage`、`ProviderCredentials`、`ProviderException`（含 `isContentSafety`） |
| `ProviderDefinition.kt` | id、中英显示名、预设生成/编辑模型、默认模型、`supportsImageEdit`、API Key 提示、`SizeOption(value = "宽*高", label)` |
| `ProviderRegistry.kt` | 保存注册列表，`resolve(id)` 在未知 id 时回退到第一个注册项 |
| `dashscope/` | `DashScopeProvider` 判断 `isWanModel` 走异步任务，否则走同步多模态接口；`DashScopeApi` 负责三个端点与轮询（5s × 120 次）；`DashScopeDefinition` 维护模型族谓词与各系列尺寸表；`ApiModels` 为 Gson DTO |
| `agnes/` | `AgnesProvider` 用同一 `GenerationRequest` 处理文生图与图编辑；`AgnesApi` 调用 `POST https://apihub.agnes-ai.com/v1/images/generations`；`AgnesDefinition.apiSizeParams` 把内部 `W*H` 反查为 2.5/2.1 的档位值 + ratio，2.0 及未知模型用精确 `WxH` |

### 4.3 Repository 层

`GenerationRepository` 是唯一生成入口：

- `generateTextToImage(prompt, negativePrompt, model, size)`：解析当前 provider → 读取该 provider 的 API Key → 校验非空 → 调用 provider → `saveImage`。
- `generateImageEdit(imageUri, prompt, model, size, context)`：额外校验 `supportsImageEdit`，把输入图片降采样至 2048px、JPEG 85% 压缩后编码为 `data:image/jpeg;base64,...`，编辑路径的 `negativePrompt` 固定为空。
- `saveImage`：按 `ProviderImage.Remote`（下载 URL）或 `Raw`（写字节）落盘，再构造 `ImageEntry` 入库。
- 透传 `CancellationException`，其余异常包装为 `Result.failure`；全过程写 `AppLogStore` 的 start / success / failed 日志。
- 另提供相册查询、本地路径、删除、收藏等薄封装。

### 4.4 本地存储与诊断

| 文件 | 职责 |
|---|---|
| `ImageStore.kt` | 以 `ImageEntry` DTO 对外；文件存于 `filesDir/generated_images/`；`saveImage` / `saveImageFromUrl` / `saveImageFromBytes`；首次访问时用 `Mutex` 把旧 `metadata.json` 导入 Room 并重命名为 `.bak`；`importEntries` 跳过已存在 id 且要求文件已就位 |
| `db/AppDatabase.kt` | Room `genbrush.db`，version 1，`exportSchema = false`，单例懒加载 |
| `db/ImageEntity.kt` | `images` 表：`id / fileName / prompt / model / timestamp / type / size / negativePrompt / isFavorite / seed`，`timestamp` 与 `isFavorite` 有索引 |
| `db/ImageDao.kt` | 仅基础 CRUD 与全量 Flow；收藏/搜索/筛选的组合逻辑放在 ViewModel 层 |
| `PreferencesManager.kt` | `EncryptedSharedPreferences`（AES256_GCM 主密钥，AES256_SIV 键 / AES256_GCM 值加密）；所有配置按 provider id 命名空间化，兼容旧全局键与 `backend_type`；`configVersion` 在配置写入时自增；`exportAll()` / `applyImported()` 支撑迁移 |
| `ImageSaver.kt` | 保存到系统相册：`inJustDecodeBounds` + 采样防 OOM，Android Q+ 用 MediaStore，低版本用 `insertImage` |
| `DataMigrationManager.kt` | 导出 ZIP：`settings.json` + `metadata.json` + `images/*`；导入时解压到临时目录、拷贝图片、合并元数据、恢复设置，`finally` 清理临时目录 |
| `AppLogStore.kt` | 512KB 滚动 `recent.log`，单条上限 4000 字符；脱敏 Authorization Bearer、api_key、access/refresh token；`exportRecent()` 输出带版本与设备信息的快照 |
| `NetworkLoggingInterceptor.kt` | 记录方法、去掉 query/fragment 的安全 URL、HTTP 状态与耗时；异常时写 error 日志 |

### 4.5 UI 层

- 每个页面遵循 `MutableStateFlow<XState>` + 暴露 `StateFlow` + companion `factory(...)` 的模式。
- `TextToImageViewModel` / `ImageEditViewModel`：维护 prompt、模型、尺寸、`isGenerating`、`elapsedSeconds`；`init` 订阅 `prefs.configVersion.drop(1)` 联动刷新；持 `generationJob` / `timerJob`，`stopGeneration()` 取消生成并停止计时。
- `SettingsViewModel`：按 provider 加载 API Key、预置/自定义/禁用模型、默认模型与尺寸；`selectProvider` 切换时重新解析 definition；模型或尺寸失效时回退到首个可用项；`save()` 一次性写入全部配置。
- `GalleryViewModel`：`computeDisplayedImages` 在 state 内组合筛选与排序；多选批量删除/收藏；`SaveResult` 为一次性事件，UI 展示后 `consumeSaveResult()`。
- `FullImageViewerScreen`：`HorizontalPager` + 每页独立 zoom state，缩放时禁用翻页；支持保存/分享/删除与回传重新生成、编辑。
- `AppStrings.kt` / `LocalStrings.kt`：ZH / EN 两套文案 + `staticCompositionLocalOf`，`resolveAppStrings` 处理 `system`；`ErrorMapper` 把 `ProviderException` 与常见错误关键词映射为本地化提示。

## 5. 关键类型

| 类型 | 位置 | 说明 |
|---|---|---|
| `ImageEntry` | `ImageStore.kt` | UI / Repository 间 DTO：`id / fileName / prompt / model / timestamp / type / size / negativePrompt / isFavorite / seed`，`type` 为 `text_to_image` 或 `image_edit` |
| `ImageEntity` | `db/ImageEntity.kt` | Room 实体，与 `ImageEntry` 一一对应，互转扩展函数集中在 `ImageStore.kt` |
| `ProviderImage` | `ImageProvider.kt` | `Remote(url)` 或 `Raw(bytes)`；`Raw` 自定义了按内容比较的 `equals` / `hashCode` |
| `ProviderException` | `ImageProvider.kt` | 携带 `code`、`message`、`isContentSafety`；`DashScopeException` / `AgnesException` 继承它 |
| `SizeOption` | `ProviderDefinition.kt` | `value = "宽*高"`、`label` 为展示文案；各 provider 通过 `supportedSizes(model)` 返回 |
| `SaveResult` | `GalleryViewModel.kt` | `Success` / `Error(message)` 一次性事件 |

## 6. 两个提供商对照

| 维度 | DashScope（`dashscope`） | Agnes AI（`agnes`） |
|---|---|---|
| 端点 | `dashscope.aliyuncs.com` 同步 `multimodal-generation/generation`、异步 `image-generation/generation`、任务查询 `/api/v1/tasks` | `https://apihub.agnes-ai.com/v1/images/generations` |
| 预设模型 | qwen-image-3.0-pro、qwen-image-2.0 系列、qwen-image-max/plus、wan2.7-image(-pro) | agnes-image-2.5-flash / 2.1-flash / 2.0-flash |
| 调用方式 | qwen 系列同步；`wan` 前缀走异步提交 + 轮询 | 文生图与图编辑共用同一个 POST |
| 尺寸传参 | 按模型族的固定 `W*H` 档位 | 2.5/2.1 用档位值（1K/2K/4K）+ ratio；2.0 用精确 `WxH` |
| 图像编辑 | 同步接口内联图片内容项；wan 系列走异步任务 | `extra_body.image` 传 Data URI 数组，`response_format` 必须置于 `extra_body` 内 |
| 反向提示词 | 文生图支持（`negative_prompt`）；编辑路径为空 | 不支持，请求中忽略 |
| 默认模型 | `qwen-image-2.0-pro` | `agnes-image-2.5-flash` |

新增提供商：实现 `ImageProvider` + 提供 `ProviderDefinition` + 在 `GenBrushApp.onCreate()` 注册；设置页选择器自动列出，配置按 `definition.id` 隔离。

## 7. 核心流程

### 7.1 文生图

```
用户输入 → TextToImageViewModel.generate()
  → GenerationRepository.generateTextToImage()
  → registry.resolve(prefs.providerId) → 校验 providerApiKey()
  → provider.generateTextToImage(credentials, TextToImageRequest)
  → ProviderImage.Remote / Raw
  → ImageStore.saveImageFromUrl / saveImageFromBytes → Room insert
  → ViewModel 更新 resultImageFile 或 mapError(e) → UI 渲染结果/错误卡片
```
全程由 `AppLogStore` 记录 generation start / success / failed（含 provider、model、size、耗时相关字段，但不记录提示词正文或密钥）。

### 7.2 取消生成

`stopGeneration()` 取消 `generationJob` → `executeCancellable` 的 `invokeOnCancellation { call.cancel() }` 取消 OkHttp 调用 → `CancellationException` 在 API 与 Repository 层均被显式重抛而非转成失败结果 → ViewModel `catch` 中静默结束，`finally` 停止计时并复位 `isGenerating`。

### 7.3 配置联动

设置页 `save()` 写入 `PreferencesManager` → `notifyConfigChanged()` 使 `configVersion` 自增 → 生成页 ViewModel 的 `drop(1).collect` 触发 `refreshModels(applyDefaults = true)` → 重新读取当前 provider 的预设/禁用/自定义模型、`supportedSizes`、默认模型与尺寸，并对失效选项做安全回退。避免了依赖页面重建或 `ON_RESUME` 时机。

### 7.4 相册筛选与排序

`loadImages()` 取 `repository.getGalleryImages()`（`ImageStore.getAllEntries()`），`computeDisplayedImages` 依次应用：收藏筛选 → 类型筛选 → prompt 关键词（lowercase contains）→ 排序（`TIME_DESC` / `TIME_ASC` / `FAVORITE_FIRST`），结果写入 `state.displayedImages`，UI 直接订阅。

### 7.5 数据迁移与日志导出

导出：`DataMigrationManager.exportData` 生成 ZIP（`settings.json`、`metadata.json`、`images/*`），设置同时包含顶层兼容键与所有 `api_key_*` 等命名空间键。导入：解压到 `cacheDir/import_temp_*` → 拷贝图片（跳过已存在文件）→ `importEntries`（跳过已存在 id）→ `applyImported`（先顶层键，再用命名空间键覆盖）→ 清理临时目录。日志导出由设置页 SAF launcher 调用 `AppLogStore.exportRecent()`。

## 8. 构建、签名与 CI

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK（有凭据才签名）
./gradlew test                   # JVM 单测：5 个测试类 / 22 个用例
./gradlew connectedAndroidTest   # 设备测试（当前仅模板）
```

- `settings.gradle.kts` 在非 CI 环境启用阿里云 Maven 镜像；`FAIL_ON_PROJECT_REPOS` 强制集中管理仓库。
- 依赖版本集中在 `gradle/libs.versions.toml`，Compose 由 BOM 统一；Java 11 兼容；`buildConfig = true`、`compose = true`。
- **签名**：版本库不含任何凭据。`app/build.gradle.kts` 从 Gradle property 或环境变量读取 `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_PASSWORD` / `SIGNING_KEY_ALIAS`，仅在项目根目录存在 `genbrush-release.jks` 且密码可用时创建 release signing config。缺失时构建不报错，release 输出 `app-release-unsigned.apk`。本地凭据放 `~/.gradle/gradle.properties` 或 `-P`；CI 由 secrets 注入。
- **CI**：`android.yml` 在 push / PR 执行 `assembleDebug` + `test` 并上传 debug APK；`release.yml` 在 `v*` tag 时校验签名 secrets、恢复 keystore、构建 release 并发布 GitHub Release。当前仓库只有 `v1.1` tag，v1.2 尚未发版。
- 未配置 lint / ktlint / detekt（`CONTRIBUTING.md` 提到的 `ktlintCheck` 实际不存在）。

## 9. 约定与已知限制

- **约定**：Kotlin DSL + version catalog；Compose 版本不由单个依赖指定；Provider 不碰持久化与 UI；模型族判断与尺寸表归属各 `*Definition`；新增文案需同时补 ZH / EN；不提交密钥、keystore、`local.properties`。
- Release **不启用 R8 混淆**，`proguard-rules.pro` 保留但未激活。
- `network_security_config.xml` 全局允许明文 HTTP，注释说明是为将来的本地端点类 provider 预留；当前两个 provider 均为 HTTPS，属于可收紧的敞口。
- `data_extraction_rules.xml` 仍是模板（含 TODO）；`AndroidManifest` 中 `allowBackup="false"`，因此云备份实际关闭。
- Room 为 version 1 且 `exportSchema = false`：字段一次性预留到位避免了迁移，但后续改表结构没有 schema 基线。
- `androidx.security:security-crypto` 使用 `1.1.0-alpha06`（alpha 版本）。
- 依赖装配为手写 Service Locator，无 DI 框架；`FullImageViewerScreen` 与 `SettingsScreen` 直接使用 repository / `DataMigrationManager`，未走 ViewModel。
- Agnes AI 与图像编辑路径不支持反向提示词；模型与尺寸表为硬编码，上游 API 变更需手工更新。
- 测试集中在 API DTO、尺寸参数、错误映射与日志脱敏；Repository、偏好迁移、Room、ViewModel 与 UI 尚无自动化测试。

## 10. 版本历史

| 版本 | 变化 |
|---|---|
| v1.1 及更早 | DashScope + SD WebUI 双后端，LoRA 选择器，`data/remote/` 远程层，`backend_type` 配置 |
| v1.2（当前代码） | 移除 SD WebUI / LoRA；引入 `ImageProvider` / `ProviderDefinition` / `ProviderRegistry`；新增 Agnes AI；配置按提供商命名空间化并兼容旧键；新增诊断日志、可取消生成、qwen-image-3.0 支持、发布签名与 CI；版本号 1.2 / versionCode 3，尚未打 tag 发布 |
