# GenBrush Code Wiki

> AI 图像生成与编辑 Android 应用的结构化代码文档。本文档基于源码分析生成，涵盖项目整体架构、主要模块职责、关键类与函数说明、依赖关系以及项目运行方式。

> **⚠️ 架构变更说明（v1.2，2026-09）**：自 v1.2 起，SD WebUI（Stable Diffusion）源已被移除，项目改为**多提供商架构**（类似 Chatbox 的提供商模型，当前内置百炼 DashScope 与 Agnes AI）。核心变化：
> - 新增 `data/provider/` 包：`ImageProvider`（提供商接口 + 通用请求/结果类型）、`ProviderDefinition`（提供商元数据：显示名/预设模型/能力）、`ProviderRegistry`（提供商注册表，按 id 解析当前提供商）。
> - DashScope 实现迁入 `data/provider/dashscope/`：`DashScopeProvider`（编排同步/异步生成）、`DashScopeDefinition`（预设模型表、各模型系列尺寸表、模型族判断——原 `ModelSelector`/`SizeSelector`/`ApiModels` 中的知识迁至此处）、`DashScopeApi` + `ApiModels`（原 `data/remote/` 迁入）。
,Target:
> - Agnes AI 实现位于 `data/provider/agnes/`：`AgnesProvider` + `AgnesDefinition`（agnes-image-2.5/2.1/2.0-flash 模型表；2.5/2.1 按"档位值+比例"传参，内部 "宽*高" 由 `apiSizeParams` 反查转换，2.0 用精确尺寸）+ `AgnesApi`（文生图与图生图共用 `POST /v1/images/generations`，OpenAI 兼容；图生图输入走 `extra_body.image` Data URI 数组，`response_format` 必须放 `extra_body` 内，Base URL `https://apihub.agnes-ai.com/v1`）。
> - `GenerationRepository` 改为从 `ProviderRegistry` 解析当前提供商并负责落盘入库；SD 相关代码（`StableDiffusionApi`、`SdApiModels`、`LoraSelector`、LoRA 逻辑、SD 设置页与尺寸表）全部删除。
> - 设置页改为提供商下拉选择器（列表来自 `ProviderRegistry`），首选项存于 `provider_id`（旧 `backend_type` 自动迁移）；API key 按提供商命名空间化存储（`api_key_<providerId>`，兼容读取旧全局 `api_key`）。
> - **接入新提供商**：实现 `ImageProvider` + 提供 `ProviderDefinition` + 在 `GenBrushApp` 注册，设置页选择器会自动列出。
>
> 下文正文中关于"双后端"、"SD WebUI"、"LoRA"的章节描述的是 v1.1 及之前的架构，保留作为历史参考。

---

## 目录

1. [项目概述](#1-项目概述)
2. [整体架构](#2-整体架构)
3. [目录结构](#3-目录结构)
4. [主要模块职责](#4-主要模块职责)
5. [关键类与函数说明](#5-关键类与函数说明)
6. [依赖关系](#6-依赖关系)
7. [项目运行方式](#7-项目运行方式)
8. [数据流与核心流程](#8-数据流与核心流程)
9. [设计要点与约定](#9-设计要点与约定)

---

## 1. 项目概述

**GenBrush** 是一款基于 Jetpack Compose 构建的 Android 原生应用，用于 AI 图像生成与编辑。它采用多提供商架构：

- **DashScope（阿里云百炼）**：云端 API，支持 qwen-image 系列同步生成与 wan 系列异步任务生成
- **Agnes AI**：云端 API（`apihub.agnes-ai.com`），OpenAI 兼容图像接口，支持 agnes-image-2.5/2.1/2.0-flash 文生图与图像编辑

| 属性 | 值 |
|---|---|
| 包名 | `com.example.genbrush` |
| 应用 ID | `com.example.genbrush` |
| 版本 | 1.0 (versionCode 1) |
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3 (BOM 2026.02.01) |
| 架构 | MVVM + Repository |
| Min SDK | 24 (Android 7.0) |
| Target/Compile SDK | 36 |
| 构建 | Gradle 9.4.1 (Kotlin DSL) + AGP 9.2.1 |

核心功能：文生图、图像编辑、双后端切换、LoRA 支持、模型管理（预置 + 自定义）、相册浏览（搜索/筛选/排序/收藏/批量操作）、全屏图片查看、数据导入导出、AES-256 加密存储、Material 3 动态取色、中英双语。

---

## 2. 整体架构

GenBrush 采用经典的 **MVVM + Repository** 单模块架构，通过 Repository 层实现双后端调度，UI 层不感知后端细节。

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI 层 (ui/)                              │
│  Compose Screens + ViewModels (StateFlow 驱动)                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────┐ ┌───────────┐   │
│  │ TextToImage  │ │  ImageEdit   │ │ Gallery  │ │ Settings  │   │
│  └──────┬───────┘ └──────┬───────┘ └────┬─────┘ └─────┬─────┘   │
│         │                │              │             │          │
│         └────────────────┴──────────────┴─────────────┘          │
│                            │ 调用                                │
├────────────────────────────▼─────────────────────────────────────┤
│                  Repository 层 (data/repository)                 │
│         GenerationRepository —— 双后端调度中枢                   │
│         根据 prefs.backend 分发到 DashScope 或 SD WebUI          │
├──────────────┬──────────────────────┬────────────────────────────┤
│              │                      │                            │
│   ┌──────────▼─────────┐  ┌─────────▼──────────┐                 │
│   │ DashScopeApi       │  │ StableDiffusionApi │  远程层         │
│   │ (云端, 同步/异步)   │  │ (局域网, REST)     │  (data/remote) │
│   └────────────────────┘  └────────────────────┘                 │
│                            │                                      │
├────────────────────────────▼─────────────────────────────────────┤
│                    本地存储层 (data/local)                        │
│  ┌──────────────┐  ┌────────────────────┐  ┌──────────────────┐  │
│  │ ImageStore   │  │ PreferencesManager │  │DataMigrationMgr  │  │
│  │ (图片+Room DB)│  │ (EncryptedSharedPref)│  │ (ZIP 导入导出)  │  │
│  └──────┬───────┘  └────────────────────┘  └──────────────────┘  │
│         │                                                        │
│  ┌──────▼─────────────────────────────────┐                      │
│  │ Room: AppDatabase / ImageDao /          │                      │
│  │       ImageEntity (genbrush.db)         │                      │
│  └─────────────────────────────────────────┘                      │
└──────────────────────────────────────────────────────────────────┘
```

**分层职责：**

- **UI 层**：Compose 屏幕渲染 + ViewModel 状态管理，所有状态通过 `StateFlow` 单向流动
- **Repository 层**：唯一数据出口，封装双后端调度、图片落盘、元数据持久化
- **远程层**：纯 HTTP API 客户端，返回 `Result<T>`，不持有业务状态
- **本地存储层**：Room 数据库 + 加密偏好 + 图片文件 + 数据迁移

---

## 3. 目录结构

```
genbrush/
├── app/
│   ├── build.gradle.kts                    # 应用模块构建脚本
│   ├── proguard-rules.pro                  # ProGuard 规则（release 构建保留但 minify 已关闭）
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml         # 清单（权限、Activity、FileProvider）
│       │   ├── java/com/example/genbrush/
│       │   │   ├── GenBrushApp.kt          # Application：依赖初始化、双 OkHttp、Coil
│       │   │   ├── MainActivity.kt         # 入口 Activity
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── db/                           # Room 数据库
│       │   │   │   │   │   ├── AppDatabase.kt
│       │   │   │   │   │   ├── ImageDao.kt
│       │   │   │   │   │   └── ImageEntity.kt
│       │   │   │   │   ├── DataMigrationManager.kt     # ZIP 导入导出
│       │   │   │   │   ├── ImageSaver.kt           # 保存到系统相册（OOM 保护 + MediaStore）
│       │   │   │   │   ├── ImageStore.kt                # 图片存储 + Room 封装 + 旧 JSON 迁移
│       │   │   │   │   └── PreferencesManager.kt     # EncryptedSharedPreferences
│       │   │   │   ├── remote/
│       │   │   │   │   ├── model/                    # API 数据模型
│       │   │   │   │   │   ├── ApiModels.kt          # DashScope 模型
│       │   │   │   │   │   └── SdApiModels.kt        # SD WebUI 模型
│       │   │   │   │   ├── DashScopeApi.kt           # DashScope API 客户端
│       │   │   │   │   └── StableDiffusionApi.kt     # SD WebUI API 客户端
│       │   │   │   └── repository/
│       │   │   │       └── GenerationRepository.kt   # 数据仓库（双后端调度）
│       │   │   └── ui/
│       │   │       ├── common/ErrorMapper.kt         # 错误信息映射
│       │   │       ├── components/                    # 可复用 Compose 组件
│       │   │       │   ├── ErrorResultCard.kt
│       │   │       │   ├── ImageResultCard.kt
│       │   │       │   ├── LoadingOverlay.kt
│       │   │       │   ├── LoraSelector.kt
│       │   │       │   ├── ModelSelector.kt          # 含预置模型常量
│       │   │       │   └── SizeSelector.kt
│       │   │       ├── gallery/
│       │   │       │   ├── FullImageViewerScreen.kt # 全屏查看（缩放/翻页）
│       │   │       │   ├── GalleryScreen.kt          # 相册列表
│       │   │       │   └── GalleryViewModel.kt
│       │   │       ├── imageedit/
│       │   │       │   ├── ImageEditScreen.kt
│       │   │       │   └── ImageEditViewModel.kt
│       │   │       ├── localization/
│       │   │       │   ├── AppStrings.kt             # 中英文字符串（含 ZH/EN）
│       │   │       │   └── LocalStrings.kt           # CompositionLocal
│       │   │       ├── navigation/
│       │   │       │   ├── AppNavigation.kt          # NavHost + 底部导航
│       │   │       │   └── Screen.kt                 # 路由 sealed class
│       │   │       ├── settings/
│       │   │       │   ├── SettingsScreen.kt
│       │   │       │   └── SettingsViewModel.kt
│       │   │       ├── texttoimage/
│       │   │       │   ├── TextToImageScreen.kt
│       │   │       │   └── TextToImageViewModel.kt
│       │   │       └── theme/
│       │   │           ├── Color.kt
│       │   │           ├── Theme.kt                  # GenBrushTheme
│       │   │           └── Type.kt
│       │   └── res/
│       │       ├── xml/
│       │       │   ├── backup_rules.xml
│       │       │   ├── data_extraction_rules.xml
│       │       │   ├── file_provider_paths.xml       # FileProvider 路径
│       │       │   └── network_security_config.xml   # 允许明文 HTTP
│       │       └── values/                            # colors/strings/themes
│       ├── androidTest/                               # 设备测试
│       └── test/java/.../ErrorMapperTest.kt           # 单元测试
├── docs/
│   ├── SD_WEBUI_INTEGRATION.md                        # SD 集成方案
│   └── plan-lora-support.md                           # LoRA 实现计划
├── gradle/
│   ├── libs.versions.toml                             # 版本目录
│   └── wrapper/                                       # Gradle Wrapper
├── build.gradle.kts                                   # 根构建脚本
├── settings.gradle.kts                                # 含阿里云镜像配置
├── README.md
├── CLAUDE.md
└── CONTRIBUTING.md
```

---

## 4. 主要模块职责

### 4.1 应用入口层

| 文件 | 职责 |
|---|---|
| [GenBrushApp.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/GenBrushApp.kt) | `Application` 子类。初始化全部依赖：构造两个 `OkHttpClient`（普通 60s 超时 + SD 专用 300s 超时并信任所有证书）、`DashScopeApi`、`StableDiffusionApi`、`ImageStore`、`GenerationRepository`、`PreferencesManager`；配置 Coil 3 全局 `ImageLoader`（内存缓存 25%、磁盘缓存 2%）；通过 `language: StateFlow<String>` 驱动全局语言切换 |
| [MainActivity.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/MainActivity.kt) | 入口 `ComponentActivity`，启用 edge-to-edge，从 `application` 取出 `GenBrushApp` 的依赖，在 `GenBrushTheme` 下挂载 `AppNavigation` 并传入语言流 |

### 4.2 导航层

| 文件 | 职责 |
|---|---|
| [Screen.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/navigation/Screen.kt) | `sealed class Screen`，定义四个底部 Tab 路由：`TextToImage`、`ImageEdit`、`Gallery`、`Settings`，每个携带图标与本地化标题函数 |
| [AppNavigation.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/navigation/AppNavigation.kt) | `NavHost` 容器。提供底部 `NavigationBar`（仅 Tab 路由显示），为每个目的地通过 `viewModel(factory=...)` 创建 ViewModel；处理 `gallery/image/{imageId}` 详情路由，并通过 `savedStateHandle` 传递"重新生成"和"编辑此图片"的回调参数 |

### 4.3 数据层（data/）

#### 4.3.1 远程层（remote/）

| 文件 | 职责 |
|---|---|
| [DashScopeApi.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/remote/DashScopeApi.kt) | DashScope REST 客户端。三个核心方法：`generateImage`（同步，qwen-image 系列）、`submitAsyncTask`（提交异步任务，wan 系列）、`pollTaskStatus`（5 秒间隔轮询，最多 120 次/10 分钟）。统一返回 `Result<T>`，错误封装为 `DashScopeException(code, message)`。端点常量：`SYNC_ENDPOINT`、`ASYNC_ENDPOINT`、`TASK_ENDPOINT` |
| [StableDiffusionApi.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/remote/StableDiffusionApi.kt) | SD WebUI REST 客户端。方法：`txt2img`、`img2img`、`getModels`、`getLoras`、`getProgress`、`testConnection`。所有调用基于 `$serverUrl/sdapi/v1/*` 端点 |
| [ApiModels.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/remote/model/ApiModels.kt) | DashScope 请求/响应数据类。关键函数 `isWanModel(model)` 判断是否走异步任务路径（模型名以 `wan` 开头） |
| [SdApiModels.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/remote/model/SdApiModels.kt) | SD WebUI 请求/响应数据类。默认参数适配 Flux 模型（`cfg_scale=1`、`distilled_cfg_scale=3`、`sampler_name="Euler"`、`scheduler="Beta"`） |

#### 4.3.2 本地存储层（local/）

| 文件 | 职责 |
|---|---|
| [PreferencesManager.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/PreferencesManager.kt) | 加密偏好设置。基于 `EncryptedSharedPreferences`（AES256_GCM 主密钥 + AES256_SIV 键加密）。管理：API Key、默认模型/尺寸、语言、后端类型、SD 服务器地址、禁用/自定义模型列表（以 `|||` 分隔存储）。提供 `exportAll()` / `applyImported()` 支持数据迁移 |
| [ImageStore.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/ImageStore.kt) | 图片存储统一入口。对外以 `ImageEntry` DTO 暴露，内部由 Room 持久化。职责：保存图片（`saveImageFromUrl`/`saveImageFromBytes`/`saveImage`）、查询/删除/收藏、导入条目。**首次启动自动迁移**：检测旧 `metadata.json`，导入到数据库后重命名为 `.bak`（`Mutex` 保证只执行一次）。图片存放于 `filesDir/generated_images/` |
| [ImageSaver.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/ImageSaver.kt) | 保存图片到系统相册的统一工具。`suspend fun saveToGallery(context, file, desc)`：OOM 保护（`inJustDecodeBounds` + `inSampleSize`，最大 2048px）、Android Q+ 用 `MediaStore` + `RELATIVE_PATH`、低版本用 `insertImage`，返回 `Result<Unit>` |
| [DataMigrationManager.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/DataMigrationManager.kt) | 数据迁移。`exportData` 生成 ZIP（`settings.json` + `metadata.json` + `images/*`）；`importData` 解压到临时目录、拷贝图片文件、合并元数据（跳过已存在 id）、应用设置 |
| [AppDatabase.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/db/AppDatabase.kt) | Room 数据库（`@Database version=1, exportSchema=false`），单例懒加载，库名 `genbrush.db` |
| [ImageDao.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/db/ImageDao.kt) | Room DAO。提供 `insert`/`insertAll`/`update`/`delete`/`deleteById`/`deleteByIds`/`count`/`getAll`/`getById`/`observeAll`/`observeFavorites`/`observeById`/`searchByPrompt`/`setFavorite`/`setFavoriteForIds`。设计原则：DAO 只提供基础查询，收藏/搜索/筛选的组合逻辑由 VM 层完成 |
| [ImageEntity.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/db/ImageEntity.kt) | Room 实体（表 `images`）。字段一次性预留到位（`size`/`negativePrompt`/`isFavorite`/`seed`）以避免 Migration。索引：`timestamp`、`isFavorite` |

#### 4.3.3 仓库层（repository/）

| 文件 | 职责 |
|---|---|
| [GenerationRepository.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/repository/GenerationRepository.kt) | **数据仓库中枢**。两个公开入口：`generateTextToImage` 与 `generateImageEdit`，根据 `prefs.backend` 分发。内部实现四条路径：SD txt2img、SD img2img、DashScope 同步、DashScope 异步任务。负责图片下载/解码/Base64 处理、尺寸解析（`"1024*1024"` → width/height）、大图采样压缩（>2048px 自动降采样）、构造 `ImageEntry` 并落盘。同时提供相册查询、删除、收藏、路径获取等通用方法 |

### 4.4 UI 层（ui/）

#### 4.4.1 主题与本地化

| 文件 | 职责 |
|---|---|
| [Theme.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/theme/Theme.kt) | `GenBrushTheme`。Android 12+ 启用动态取色（`dynamicLightColorScheme`/`dynamicDarkColorScheme`），低版本回退到 `LightColorScheme`/`DarkColorScheme` |
| [Color.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/theme/Color.kt) | 颜色常量（Purple40/80、PurpleGrey40/80、Pink40/80） |
| [Type.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/theme/Type.kt) | Material 字体配置（自定义 `bodyLarge`） |
| [AppStrings.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/localization/AppStrings.kt) | 所有 UI 文案集中在此 data class，`companion object` 提供 `ZH`/`EN` 两套完整翻译 |
| [LocalStrings.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/localization/LocalStrings.kt) | `staticCompositionLocalOf` 提供 `LocalStrings.current`。`resolveAppStrings(language)` 处理 `system` 跟随系统语言 |

#### 4.4.2 公共组件（components/）

| 文件 | 职责 |
|---|---|
| [ModelSelector.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/components/ModelSelector.kt) | 模型下拉选择器（`ExposedDropdownMenuBox`）。定义预置模型常量 `PRESET_GENERATION_MODELS` / `PRESET_EDIT_MODELS`，以及 `buildAvailableModels`、`getConfiguredGenerationModels`、`getConfiguredEditModels` 工具函数（启用预置 + 自定义，去重） |
| [SizeSelector.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/components/SizeSelector.kt) | 尺寸下拉选择器。`SIZE_OPTIONS` 预置 7 种比例（1:1、9:16、16:9、3:4、4:3 等），值格式 `"宽*高"` |
| [LoraSelector.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/components/LoraSelector.kt) | LoRA 选择器。可折叠 Card，每个 LoRA 一行：Checkbox + 名称 + 权重 Slider（0.0~2.0，默认 1.0）。仅在 SD 后端显示 |
| [ImageResultCard.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/components/ImageResultCard.kt) | 生成结果展示卡片。Coil `AsyncImage` + 提示词/模型/类型 + 保存/分享按钮。保存委托 `ImageSaver.saveToGallery`，分享用 `shareImage`（FileProvider） |
| [ErrorResultCard.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/components/ErrorResultCard.kt) | 错误展示卡片（红色边框 + 错误图标 + 标题 + 详情） |
| [LoadingOverlay.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/components/LoadingOverlay.kt) | 加载浮层（`AnimatedVisibility` + `CircularProgressIndicator` + 提示文案，SD 模式有专属提示） |
| [ErrorMapper.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/common/ErrorMapper.kt) | `mapError(e, s)`：注入 `AppStrings` 支持本地化；`DashScopeException` 直接展示原始错误码与消息；其他异常按关键词映射为友好提示 |

#### 4.4.3 各功能页面

| 页面 | Screen 文件 | ViewModel 文件 | 职责 |
|---|---|---|---|
| 文生图 | [TextToImageScreen.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/texttoimage/TextToImageScreen.kt) | [TextToImageViewModel.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/texttoimage/TextToImageViewModel.kt) | 提示词/反向提示词输入、模型/尺寸/LoRA 选择、生成。`ON_RESUME` 时 `refreshModels` 同步后端状态 |
| 图像编辑 | [ImageEditScreen.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/imageedit/ImageEditScreen.kt) | [ImageEditViewModel.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/imageedit/ImageEditViewModel.kt) | Photo Picker 选图、编辑提示词、模型/尺寸/LoRA 选择、生成。逻辑与文生图对称 |
| 相册 | [GalleryScreen.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/gallery/GalleryScreen.kt) | [GalleryViewModel.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/gallery/GalleryViewModel.kt) | 网格列表（2 列）、搜索、类型筛选 Chip、排序下拉、收藏、单/批量删除、批量收藏、保存到相册。`displayedImages` 在 state 内计算 |
| 全屏查看 | [FullImageViewerScreen.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/gallery/FullImageViewerScreen.kt) | （直接使用 repository） | `HorizontalPager` 翻页 + 双指缩放（每页独立 zoom state，缩放时禁用翻页）、信息底部弹窗、保存/分享/删除、基于原图重新生成/编辑 |
| 设置 | [SettingsScreen.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/settings/SettingsScreen.kt) | [SettingsViewModel.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/settings/SettingsViewModel.kt) | 后端 Tab 切换（DashScope/SD）、API Key、SD 服务器配置与连接测试、默认模型/尺寸、模型管理二级页（预置勾选 + 自定义增删）、语言、数据迁移、关于。可折叠分组面板 |

---

## 5. 关键类与函数说明

### 5.1 核心数据类

#### `ImageEntry`（DTO）— [ImageStore.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/ImageStore.kt)

UI 与 Repository 之间传递的图片记录，屏蔽数据库细节：

```kotlin
data class ImageEntry(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val prompt: String,
    val model: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,            // "text_to_image" | "image_edit"
    val size: String? = null,
    val negativePrompt: String? = null,
    val isFavorite: Boolean = false,
    val seed: Long? = null
)
```

#### `ImageEntity`（Room 实体）— [ImageEntity.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/db/ImageEntity.kt)

数据库表 `images`，字段与 `ImageEntry` 一一对应，通过 `toEntity()` / `toEntry()` 私有扩展函数互转（定义在 [ImageStore.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/ImageStore.kt) 末尾）。

### 5.2 核心类

#### `GenerationRepository` — [GenerationRepository.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/repository/GenerationRepository.kt)

**双后端调度中枢**，构造函数注入四个依赖：

```kotlin
class GenerationRepository(
    private val dashScopeApi: DashScopeApi,
    private val sdApi: StableDiffusionApi,
    private val imageStore: ImageStore,
    private val prefs: PreferencesManager
)
```

**公开 API：**

| 方法 | 说明 |
|---|---|
| `generateTextToImage(prompt, negativePrompt, model, size)` | 根据 `prefs.backend` 分发到 SD txt2img 或 DashScope（同步/异步） |
| `generateImageEdit(imageUri, prompt, model, size, context)` | 分发到 SD img2img 或 DashScope 图像编辑 |
| `getGalleryImages()` | 返回全部图片条目（按时间倒序） |
| `getLocalImagePath(entry)` / `getLocalImagePathById(id)` | 获取图片文件 |
| `getEntryById(id)` | 查询单条记录 |
| `deleteImage(entry)` | 删除文件 + 数据库记录 |
| `setFavorite(id, favorite)` | 切换收藏 |

**私有路径：**

- `generateWithSdTxt2Img` / `generateWithSdImg2Img`：调用 SD API，Base64 解码后 `saveImageFromBytes`
- `generateWithDashScope`：根据 `isWanModel(model)` 选择同步或异步
- `generateTextToImageSync`：qwen-image 同步生成，下载 URL 落盘
- `generateTextToImageAsync`：wan 系列，提交任务后 `pollTaskStatus` 轮询
- `generateImageEditDashScope`：图片 Base64 内联到 `ContentItem.image`，同步生成
- `encodeImageToBase64`：图片读取 + 自动降采样（>2048px）+ JPEG 85% 压缩 + Base64 编码

#### `DashScopeApi` — [DashScopeApi.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/remote/DashScopeApi.kt)

| 方法 | 说明 |
|---|---|
| `generateImage(apiKey, request)` | 同步生成（`SYNC_ENDPOINT`），解析 `output.choices[0].message.content[0].image` |
| `submitAsyncTask(apiKey, request)` | 提交异步任务（`ASYNC_ENDPOINT`，带 `X-DashScope-Async: enable` 头） |
| `pollTaskStatus(apiKey, taskId)` | 5 秒间隔轮询（`TASK_ENDPOINT/{taskId}`），最多 120 次（10 分钟），状态 `SUCCEEDED`/`FAILED`/`CANCELED` |

错误处理：HTTP 非 2xx 或响应含 `code` 字段时返回 `DashScopeException(code, message)`，保留 API 原始错误信息。

#### `StableDiffusionApi` — [StableDiffusionApi.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/remote/StableDiffusionApi.kt)

六个方法均返回 `Result<T>`：`txt2img`、`img2img`、`getModels`、`getLoras`、`getProgress`、`testConnection`。所有请求基于 `$serverUrl/sdapi/v1/*`，失败时返回 `Exception("SD API 错误: HTTP {code} - {body}")`。

#### `ImageStore` — [ImageStore.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/ImageStore.kt)

| 方法 | 说明 |
|---|---|
| `saveImage(bitmap, entry)` | Bitmap → JPEG 文件 + 入库 |
| `saveImageFromUrl(url, entry)` | 下载 URL → 文件 + 入库 |
| `saveImageFromBytes(bytes, entry)` | 写入字节 + 入库（SD Base64 路径） |
| `getAllEntries()` / `getEntryById(id)` | 查询 |
| `getImageFile(entry)` / `getImageFileById(id)` | 获取 File |
| `deleteImage(entry)` | 删文件 + 删记录 |
| `setFavorite(id, favorite)` | 更新收藏 |
| `importEntries(entries)` | 导入条目（跳过已存在 id，要求文件已就位） |
| `migrateLegacyJsonIfNeeded()` | 首次启动迁移旧 `metadata.json` |
| `createFileName(type)` | 生成 `${timestamp}_${uuid8}.jpg` |
| `resizeBitmapIfNeeded(bitmap, maxDimension)` | 按最大边降采样 |

#### `PreferencesManager` — [PreferencesManager.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/local/PreferencesManager.kt)

常量定义（`companion object`）：

```kotlin
const val DEFAULT_MODEL = "qwen-image-2.0-pro"
const val DEFAULT_SIZE = "1024*1024"
const val LANGUAGE_SYSTEM = "system" / LANGUAGE_ZH = "zh" / LANGUAGE_EN = "en"
const val BACKEND_DASHSCOPE = "dashscope" / BACKEND_SD_WEBUI = "sd_webui"
```

属性读写均委托 `EncryptedSharedPreferences`，模型列表用 `|||` 分隔。`exportAll()` / `applyImported()` 用于数据迁移。

### 5.3 ViewModel 与 State

每个 ViewModel 遵循统一模式：持有 `MutableStateFlow<XxxState>`，对外暴露 `val state: StateFlow<XxxState>`，通过 `factory(...)` 静态方法提供 `ViewModelProvider.Factory`。

| ViewModel | 关键状态字段 | 关键方法 |
|---|---|---|
| `TextToImageViewModel` | `prompt`、`negativePrompt`、`selectedModel`、`selectedSize`、`isGenerating`、`resultImageFile`、`error`、`isSdBackend`、`availableModels`、`availableLoras`、`selectedLoras: Map<String,Float>` | `generate()`（拼接 `<lora:name:weight>` 到 prompt）、`refreshModels()`、`toggleLora`、`updateLoraWeight` |
| `ImageEditViewModel` | `sourceImageUri`、其余同上 | `generate(context)`、`setSourceImage(uri)` |
| `GalleryViewModel` | `images`、`displayedImages`、`selectionMode`、`selectedIds`、`sortMode`、`showFavoritesOnly`、`searchQuery`、`typeFilter`、`pendingDelete`、`saveResult` | `loadImages`、`computeDisplayedImages`（纯函数组合筛选/排序）、`enterSelectionMode`、`confirmBatchDelete`、`saveToDeviceGallery`（委托 `ImageSaver`，OOM 保护 + 一次性事件） |
| `SettingsViewModel` | `apiKey`、`backend`、`sdServerUrl`、`sdConnectionStatus`、`disabledGenerationModels`、`customGenerationModels`、`expandedSections`、`isMigrating`、`showModelManagement` | `testSdConnection`、`toggleGenerationModel`、`addCustomGenerationModel`、`save()`、`toggleSection` |

**`GalleryViewModel` 设计亮点：**

- `displayedImages` 在 state 内通过 `computeDisplayedImages(s)` 计算，避免 UI 层重复计算（Bug #7 fix）
- `SaveResult` 为 `sealed class` 一次性事件，UI 层 `LaunchedEffect` 观察后 `consumeSaveResult()`（Bug #12 fix）
- 批量删除使用 loading 状态保护（Bug #11 fix）

**`FullImageViewerScreen` 设计亮点：**

- 每页独立 zoom state（`ZoomableImage`），离开页面时重置（Bug #2 fix）
- 缩放时通过 `onZoomChanged` 回调禁用 `HorizontalPager` 滑动（Bug #10 fix）
- 仅在 `isZoomed` 时挂载 `detectTransformGestures`，1x 时让 Pager 接管滑动

### 5.4 关键工具函数

#### `isWanModel(model)` — [ApiModels.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/data/remote/model/ApiModels.kt)

```kotlin
fun isWanModel(model: String): Boolean = model.startsWith("wan")
```

决定 DashScope 走同步还是异步任务路径。

#### `buildAvailableModels(presetModels, disabledModels, customModels)` — [ModelSelector.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/components/ModelSelector.kt)

返回"启用的预置模型 + 自定义模型"去重列表。

#### `resolveAppStrings(language)` — [LocalStrings.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/localization/LocalStrings.kt)

`"zh"` → `AppStrings.ZH`，`"en"` → `AppStrings.EN`，`"system"`/其他 → 根据 `Locale.getDefault().language` 判断。

#### `mapError(e)` — [ErrorMapper.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/common/ErrorMapper.kt)

错误信息映射中枢，确保 API 原始错误不被吞掉（详见单元测试 [ErrorMapperTest.kt](file:///d:/Learning/vibe/genbrush/app/src/test/java/com/example/genbrush/ErrorMapperTest.kt)）。

---

## 6. 依赖关系

### 6.1 模块间依赖

```
MainActivity
   └─ GenBrushApp (Application)
         ├─ PreferencesManager
         ├─ DashScopeApi        ← OkHttpClient(60s) + Gson
         ├─ StableDiffusionApi  ← OkHttpClient(300s, trustAllCerts) + Gson
         ├─ ImageStore          ← Context + AppDatabase(ImageDao)
         └─ GenerationRepository ← 上述四者

AppNavigation
   ├─ TextToImageViewModel  ← repository, prefs, sdApi
   ├─ ImageEditViewModel    ← repository, prefs, sdApi
   ├─ GalleryViewModel      ← repository
   └─ SettingsViewModel     ← prefs, sdApi

FullImageViewerScreen ← repository（直接使用，无 ViewModel）
SettingsScreen        ← DataMigrationManager（直接 new）
```

### 6.2 第三方依赖

定义于 [libs.versions.toml](file:///d:/Learning/vibe/genbrush/gradle/libs.versions.toml)，在 [app/build.gradle.kts](file:///d:/Learning/vibe/genbrush/app/build.gradle.kts) 通过 catalog 别名引用：

| 类别 | 依赖 | 版本 | 用途 |
|---|---|---|---|
| Compose BOM | `androidx.compose:compose-bom` | 2026.02.01 | 统一 Compose 版本（不单独 pin） |
| UI | `material3`、`material-icons-extended`、`ui`、`ui-graphics`、`ui-tooling-preview` | BOM 管理 | Material 3 组件 |
| Activity | `androidx.activity:activity-compose` | 1.10.1 | `ComponentActivity` / `setContent` |
| Lifecycle | `lifecycle-runtime-ktx`、`lifecycle-viewmodel-compose` | 2.9.1 | ViewModel + Lifecycle |
| Navigation | `navigation-compose` | 2.9.6 | `NavHost` |
| Security | `androidx.security:security-crypto` | 1.1.0-alpha06 | `EncryptedSharedPreferences` |
| 网络 | `okhttp`、`okhttp-logging-interceptor` | 4.12.0 | HTTP 客户端 |
| JSON | `gson` | 2.11.0 | 序列化 |
| 图片加载 | `coil-compose`、`coil-network-okhttp` | 3.0.4 | Coil 3 |
| 数据库 | `room-runtime`、`room-ktx` | 2.7.0 | Room（KSP 编译器） |
| Core | `core-ktx` | 1.16.0 | Android KTX |
| 测试 | `junit`、`androidx.junit`、`espresso-core`、`compose-ui-test-junit4` | — | 单元/设备测试 |

**构建插件：** `com.android.application` (AGP 9.2.1)、`org.jetbrains.kotlin.plugin.compose` (Kotlin 2.2.10)、`com.google.devtools.ksp` (2.3.6)。

### 6.3 外部服务依赖

| 服务 | 用途 | 配置位置 |
|---|---|---|
| DashScope API | 云端图像生成（`dashscope.aliyuncs.com`） | 设置页填 API Key |
| Agnes AI API | 云端图像生成/编辑（`apihub.agnes-ai.com/v1`） | 设置页填 API Key |

---

## 7. 项目运行方式

### 7.1 环境要求

- **Android Studio**：Ladybug (2024.2) 或更新版本
- **JDK**：17 或更新
- **Gradle**：9.4.1（项目内置 Wrapper）
- **设备/模拟器**：Android 7.0 (API 24) 或更高

### 7.2 构建命令

```bash
# 调试构建
./gradlew assembleDebug

# 发布构建（启用 R8 混淆）
./gradlew assembleRelease

# 单元测试
./gradlew test

# 运行单个测试类
./gradlew testDebugUnitTest --tests "com.example.genbrush.ErrorMapperTest"

# 设备测试
./gradlew connectedAndroidTest

# 清理
./gradlew clean
```

> Windows 上使用 `gradlew.bat` 代替 `./gradlew`。

### 7.3 运行配置

1. 克隆仓库：`git clone https://github.com/Rooftop-Love/genbrush.git`
2. 在 Android Studio 中打开项目根目录
3. 等待 Gradle Sync 完成（[settings.gradle.kts](file:///d:/Learning/vibe/genbrush/settings.gradle.kts) 已配置阿里云镜像加速，CI 环境自动跳过）
4. 连接设备/模拟器，点击 Run

### 7.4 首次启动配置

首次运行后进入 **设置** 页：

- **DashScope 提供商**：输入阿里云百炼平台 API Key（[申请地址](https://dashscope.aliyuncs.com/)），占位符 `sk-...`
- **Agnes AI 提供商**：输入 Agnes AI 平台 API Key（可在 `agnes-ai.com` 免费创建）

### 7.5 关键权限

定义于 [AndroidManifest.xml](file:///d:/Learning/vibe/genbrush/app/src/main/AndroidManifest.xml)：

- `INTERNET`：网络访问
- `READ_MEDIA_IMAGES`：读取媒体图片（编辑选图）
- `READ_EXTERNAL_STORAGE`（maxSdkVersion=32）：旧版存储权限

FileProvider（`${applicationId}.fileprovider`）配置于 [file_provider_paths.xml](file:///d:/Learning/vibe/genbrush/app/src/main/res/xml/file_provider_paths.xml)，暴露 `generated_images/` 与 `image_cache/` 路径以支持分享。

---

## 8. 数据流与核心流程

### 8.1 文生图流程（以 DashScope 同步为例）

```
用户输入 prompt → TextToImageScreen
   │ viewModel.generate()
   ▼
TextToImageViewModel.generate()
   │ 1. 拼接 LoRA 后缀到 prompt
   │ 2. repository.generateTextToImage(prompt, negPrompt, model, size)
   ▼
GenerationRepository.generateTextToImage()
   │ prefs.backend == DASHSCOPE → generateWithDashScope()
   │ isWanModel(model)? → 否 → generateTextToImageSync()
   ▼
DashScopeApi.generateImage(apiKey, GenerationRequest)
   │ POST SYNC_ENDPOINT, Bearer 鉴权
   │ 返回 Result<GenerationResponse>
   ▼
Repository: 解析 output.choices[0].message.content[0].image
   │ imageStore.saveImageFromUrl(imageUrl, ImageEntry)
   ▼
ImageStore.saveImageFromUrl()
   │ 1. ensureMigrated()（首次迁移 metadata.json）
   │ 2. OkHttp 下载 → 写入 generated_images/{fileName}
   │ 3. dao.insert(entry.toEntity())
   │ 返回 ImageEntry
   ▼
ViewModel: result.fold(...)
   │ onSuccess → state.resultImageFile = repository.getLocalImagePath(entry)
   │ onFailure → state.error = mapError(e)
   ▼
TextToImageScreen: collectAsState() 重组渲染
   │ resultImageFile != null → ImageResultCard
   │ error != null → ErrorResultCard
```

### 8.2 双后端分发决策

```
generateTextToImage / generateImageEdit
   │
   ├─ prefs.backend == SD_WEBUI
   │     ├─ txt2img → sdApi.txt2img → Base64 解码 → saveImageFromBytes
   │     └─ img2img → encodeImageToBase64 → sdApi.img2img → saveImageFromBytes
   │
   └─ prefs.backend == DASHSCOPE (默认)
         ├─ isWanModel(model) == true
         │     └─ submitAsyncTask → pollTaskStatus(5s×120) → saveImageFromUrl
         └─ isWanModel(model) == false
               ├─ 文生图 → generateImage(同步) → saveImageFromUrl
               └─ 图像编辑 → 图片 Base64 内联 → generateImage → saveImageFromUrl
```

### 8.3 相册筛选/排序流程

`GalleryViewModel` 的 `computeDisplayedImages(s)` 是纯函数，依次应用：收藏筛选 → 类型筛选 → 关键词搜索（prompt lowercase contains）→ 排序（`TIME_DESC`/`TIME_ASC`/`FAVORITE_FIRST`）。每次状态变更（`updateSearchQuery`、`toggleFavoriteFilter` 等）都重新计算 `displayedImages` 并写入 state，UI 直接订阅。

### 8.4 数据迁移流程

```
导出: DataMigrationManager.exportData(outputStream)
   → ZIP{settings.json, metadata.json, images/*}
   → 返回图片数量

导入: DataMigrationManager.importData(inputStream)
   → 解压到 cacheDir/import_temp_*
   → 拷贝 images/* 到 generated_images/（跳过已存在）
   → imageStore.importEntries(metadata)（跳过已存在 id）
   → PreferencesManager.applyImported(settings)
   → 返回 ImportResult{imageCount, settingsApplied}
   → finally: tempDir.deleteRecursively()
```

---

## 9. 设计要点与约定

### 9.1 架构约定

- **单 Gradle 模块**（`:app`），所有源码在 `com.example.genbrush` 包下
- **MVVM 单向数据流**：ViewModel 通过 `StateFlow` 暴露不可变状态，UI 通过事件回调触发 ViewModel 方法
- **Repository 作为唯一数据出口**：UI 层不直接接触 API 或 DAO
- **DTO 隔离**：`ImageEntry`（DTO）与 `ImageEntity`（Room 实体）分离，互转函数私有
- **DAO 最小化**：只提供基础查询，业务组合逻辑放 VM 层，避免 DAO 膨胀

### 9.2 构建约定

- Kotlin DSL (`.gradle.kts`) + 版本目录（[libs.versions.toml](file:///d:/Learning/vibe/genbrush/gradle/libs.versions.toml)）
- Compose 依赖通过 BOM 管理，不单独 pin 版本
- Release 构建跳过 R8 混淆（开源项目）；`proguard-rules.pro` 保留但未激活
- Java 11 兼容

### 9.3 网络约定

- **双 OkHttpClient**：通用客户端 60s 超时；SD 专用客户端 300s 读/写超时（Flux 模型慢），并信任所有证书（适配 FRP 内网穿透自签名证书）
- DEBUG 构建启用 `HttpLoggingInterceptor` BODY 级日志
- 允许明文 HTTP 流量（SD WebUI 局域网默认无 SSL）

### 9.4 安全约定

- API Key 通过 `EncryptedSharedPreferences`（AES-256-GCM 主密钥 + AES-256-SIV 键加密）存储
- 备份关闭（`android:allowBackup="false"`），自定义 [backup_rules.xml](file:///d:/Learning/vibe/genbrush/app/src/main/res/xml/backup_rules.xml) 与 [data_extraction_rules.xml](file:///d:/Learning/vibe/genbrush/app/src/main/res/xml/data_extraction_rules.xml)

### 9.5 本地化约定

- 所有 UI 文案集中在 [AppStrings.kt](file:///d:/Learning/vibe/genbrush/app/src/main/java/com/example/genbrush/ui/localization/AppStrings.kt)，通过 `LocalStrings.current`（`staticCompositionLocalOf`）访问
- 支持 `system`（跟随系统）、`zh`、`en` 三种模式
- 语言切换通过 `GenBrushApp.language: StateFlow` 全局驱动重组

### 9.6 兼容性约定

- Room 实体字段一次性预留到位（`size`/`negativePrompt`/`isFavorite`/`seed`），避免 Migration
- 旧版 `metadata.json` 自动迁移到 Room 数据库，原文件备份为 `.bak`
- 图片保存到系统相册时，Android 10+ 用 `MediaStore` + `RELATIVE_PATH`，低版本用废弃的 `insertImage`

### 9.7 已知设计细节（Bug Fix 标注）

代码中多处注释标注了历史 Bug 修复，体现工程演进：

- **Bug #2**：`FullImageViewerScreen` 每页独立 zoom state
- **Bug #5/#12**：`GalleryViewModel.saveToDeviceGallery` OOM 保护 + 一次性 `SaveResult` 事件
- **Bug #7**：`displayedImages` 在 state 内计算
- **Bug #10**：缩放时禁用 Pager 滑动
- **Bug #11**：批量删除使用 loading 状态

---

> 本文档基于源码静态分析生成，反映仓库当前状态。如需了解 SD WebUI 集成与 LoRA 支持的设计背景，参见 [docs/SD_WEBUI_INTEGRATION.md](file:///d:/Learning/vibe/genbrush/docs/SD_WEBUI_INTEGRATION.md) 与 [docs/plan-lora-support.md](file:///d:/Learning/vibe/genbrush/docs/plan-lora-support.md)。
