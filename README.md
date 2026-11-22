# GenBrush

AI 图像生成与编辑 Android 应用，基于 Jetpack Compose 构建。采用多提供商架构，内置 **DashScope（阿里云百炼）** 与 **Agnes AI** 两家图像服务，可在设置页切换，并分别配置 API Key、模型与默认尺寸。

> **文档约定**：`CLAUDE.md` 是项目结构与开发约定的唯一事实源，`docs/CODE_WIKI.md` 是详细代码地图，本文件面向使用者。三者如有冲突，以 `CLAUDE.md` 为准。

## Features

- **文生图** — 提示词 + 反向提示词，可选模型与尺寸；生成过程可随时停止，并显示已耗时
- **图像编辑** — 从相册选图，用提示词进行 AI 编辑
- **多提供商架构** — 设置页切换 DashScope / Agnes AI，配置按提供商隔离；新增提供商只需实现接口并注册
- **模型清单** — 每个提供商一份本地缓存的模型清单；可手动从上游拉取可用模型，勾选即控制生成/编辑页显示，并支持自定义模型增删
- **相册** — 搜索、类型筛选、排序、收藏、多选批量删除/收藏、保存到系统相册
- **图片详情** — 全屏翻页与缩放、保存/分享/删除、复制提示词、基于原图重新生成或继续编辑
- **数据迁移** — ZIP 导出/导入图片、元数据与设置（兼容旧版备份格式）
- **诊断日志** — 应用内记录最近日志（自动脱敏），设置页一键导出
- **安全存储** — API Key 与偏好设置经 `EncryptedSharedPreferences`（AES-256）加密存储
- **Material 3 / Studio Minimal** — 浅色纸白与深色暗房炭黑双主题（跟随系统 / 浅色 / 深色切换），支持 Android 12+ 可选动态取色（默认关闭）；中英双语，支持跟随系统

## What's new in v1.4

- **Studio Minimal 视觉重构**：全新浅色纸白 / 深色暗房炭黑配色与蓝紫微光点缀，支持应用内外观主题切换（跟随系统 / 浅色 / 深色）与可选动态壁纸取色，修复暗色冷启动闪白与双倍状态栏留白。
- **创作工作台升级**：文生图与图像编辑升级为「画布优先 + 底部控制坞」布局；新增可视化画幅比例与清晰度（1K/2K/4K）选择抽屉、极光流光生成占位与等宽计时器；图像编辑支持「长按对比原图」与「继续编辑」迭代闭环。
- **瀑布流画廊与纯黑沉浸看图器**：相册升级为按真实画幅展示的无遮罩瀑布流、折叠式搜索与底部悬浮多选工具坞；看图器改为全屏纯黑画布，支持单击显隐界面、弹簧双击缩放及一键同款再生成 / 去编辑。
- **分组设置与独立模型管理**：设置页重构为静态分组列表（含服务商密钥状态微标与行内保存）；模型管理拆分为独立路由页，提供文生图 / 编辑双 Tab、`Switch` 开关与来源徽标。

## Requirements

- Android 7.0（API 24）或更高版本
- 至少一个图像服务提供商的 API Key（在应用内「设置 → 图像服务」填写）：
  - DashScope（阿里云百炼）：[控制台](https://dashscope.aliyun.com/)
  - Agnes AI：`agnes-ai.com` 平台创建
- 构建环境：JDK 17+、Android Studio Ladybug（2024.2）或更新版本（项目自带 Gradle Wrapper）

## Getting Started

1. 克隆仓库：
   ```bash
   git clone https://github.com/Rooftop-Love/GenBrush.git
   ```

2. 在 Android Studio 中打开项目根目录，等待 Gradle Sync 完成（国内环境默认启用阿里云镜像，CI 自动跳过）。

3. 构建并运行到设备或模拟器。

4. 首次启动后进入 **设置 → 图像服务**，选择提供商并填写对应的 API Key。

## Build

```bash
# 调试构建
./gradlew assembleDebug

# 发布构建
./gradlew assembleRelease

# 单元测试
./gradlew test

# 设备测试
./gradlew connectedAndroidTest
```

Windows 上使用 `gradlew.bat` 代替 `./gradlew`。Release 构建默认**不启用 R8 混淆**，但保留 `proguardFiles` 配置。

### 发布签名

版本库中**不保存任何签名凭据**。签名配置仅在「keystore 文件存在 + 密码可用」时才生效，凭据缺失不会阻断 `assembleDebug` / `assembleRelease`（此时 release 产出未签名 APK）。

- 本地：将 `genbrush-release.jks` 放到项目根目录（已被 `.gitignore` 忽略），在 `~/.gradle/gradle.properties` 中配置 `SIGNING_STORE_PASSWORD`、`SIGNING_KEY_PASSWORD`、`SIGNING_KEY_ALIAS`，或用 `-P` 参数传入。
- CI：`.github/workflows/release.yml` 在推送 `v*` tag 时，从 GitHub Secrets 读取 `SIGNING_KEY_STORE_BASE64` 恢复 keystore，并注入上述三个密码环境变量；缺少必需 secret 时工作流直接失败，避免发布未签名包。

> **升级提示**：v1.2 使用 2026-11-07 轮换后的新签名证书，与 v1.1 不同，v1.1 用户无法直接覆盖安装，需先卸载旧版再安装。v1.3 沿用同一证书，v1.2 用户可直接覆盖安装。

## Tech Stack

| 组件 | 技术 |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3（BOM 2026.02.01） |
| 架构 | MVVM + Repository + Provider 抽象（手写依赖装配，无 DI 框架） |
| 网络 | OkHttp 4.12.0 + Gson 2.11.0（协程可取消调用） |
| 图片加载 | Coil 3.0.4 |
| 本地存储 | Room 2.7.0 + EncryptedSharedPreferences |
| 构建 | Gradle 9.4.1（Kotlin DSL）+ AGP 9.2.1 + KSP 2.3.6 |
| SDK | min 24 / target 36 / compile 36 |
| 当前版本 | 1.3（versionCode 4，2026-11-09 发布） |

依赖版本集中在 `gradle/libs.versions.toml`，Compose 组件由 BOM 统一管理，不单独指定版本。

## Project Structure

```
app/src/main/java/com/example/genbrush/
├── GenBrushApp.kt                  # Application：OkHttp/Gson/Coil、ProviderRegistry、依赖装配
├── MainActivity.kt                 # 入口 Activity（Compose setContent + 全局语言流）
├── data/
│   ├── local/
│   │   ├── db/                     # Room：AppDatabase / ImageDao / ImageEntity
│   │   ├── AppLogStore.kt          # 512KB 滚动诊断日志（脱敏 + 导出）
│   │   ├── DataMigrationManager.kt # ZIP 导入导出
│   │   ├── ImageSaver.kt           # 保存到系统相册（OOM 保护）
│   │   ├── ImageStore.kt           # 图片文件 + Room 封装 + 旧 JSON 迁移
│   │   ├── ModelCatalog.kt         # 模型清单缓存、合并规则与 JSON 编解码（纯 Kotlin）
│   │   ├── PreferenceKeys.kt       # 配置键名、旧键回退与导入合并规则
│   │   └── PreferencesManager.kt   # 加密偏好（provider_id 与按提供商命名空间的配置）
│   ├── network/
│   │   ├── OkHttpExtensions.kt           # executeCancellable：协程取消时取消 HTTP 请求
│   │   └── NetworkLoggingInterceptor.kt  # 请求/响应摘要写入 AppLogStore
│   ├── provider/
│   │   ├── ImageProvider.kt        # 接口 + 通用请求/结果/异常类型
│   │   ├── OpenAIModelListApi.kt  # OpenAI 兼容 GET /models（手动刷新用）
│   │   ├── ProviderDefinition.kt   # 提供商元数据与能力声明
│   │   ├── ProviderRegistry.kt     # 注册表与未知 id 回退
│   │   ├── dashscope/              # DashScope：qwen 同步 / wan 异步轮询
│   │   └── agnes/                  # Agnes AI：OpenAI 兼容 images API
│   └── repository/
│       ├── GenerationCore.kt        # 纯 Kotlin 生成编排（可 JVM 单测）
│       └── GenerationRepository.kt  # Android 依赖适配、输入图编码
└── ui/
    ├── common/                     # ErrorMapper（错误本地化映射）
    ├── components/                 # ModelPickerSheet、AspectRatioSizeSheet、画布占位/结果/错误卡片
    ├── gallery/                    # 瀑布流相册、GalleryViewModel、全屏沉浸查看器
    ├── imageedit/                  # 图像编辑界面与 ViewModel
    ├── texttoimage/                # 文生图界面与 ViewModel
    ├── settings/                   # 分组设置、独立模型管理页、迁移与日志导出
    ├── localization/               # AppStrings（ZH/EN）与 LocalStrings
    ├── navigation/                 # 极简底栏导航、模型管理与图片详情路由
    └── theme/                      # Studio Minimal 浅/深色主题、排版与圆角规范
```

更完整的模块说明、数据流与约定参见 [docs/CODE_WIKI.md](docs/CODE_WIKI.md)。

## 接入新的图像提供商

1. 在 `data/provider/<id>/` 下实现 `ImageProvider`（文生图、图编辑、`supportedSizes`；可选 `fetchModels(credentials)` 配合 `supportsModelListing = true` 支持在线发现模型）。
2. 提供对应的 `ProviderDefinition`（id、显示名、预置生成/编辑模型作为清单种子、默认模型、`supportsImageEdit`、API Key 提示）。
3. 在 `GenBrushApp.onCreate()` 中构造实现并注册进 `ProviderRegistry`。

设置页的提供商选择器会自动列出新注册项；API Key、默认模型/尺寸与模型清单缓存（`catalog_<id>`）均按 `definition.id` 命名空间隔离存储。清单首次由预置模型播种，之后只由设置页的开关和「从上游获取模型」按钮更新。

## Testing

`./gradlew test` 运行 JVM 单元测试，覆盖 API 数据模型与尺寸参数转换、画幅解析、错误映射、日志脱敏、配置迁移、模型清单合并规则与生成编排（9 个测试类 / 65 个用例）。`./gradlew connectedAndroidTest` 需要设备或模拟器，目前仅有模板用例。CI（`android.yml`）在 push / PR 时执行 `assembleDebug` + `test` + `lintDebug` 并上传 debug APK。

## Contributing

参见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## License

MIT License - 详见 [LICENSE](LICENSE)

## Acknowledgments

- [DashScope API](https://dashscope.aliyun.com/) - 阿里云百炼
- [Agnes AI](https://agnes-ai.com/) - 图像生成服务
- [Jetpack Compose](https://developer.android.com/jetpack/compose) / [Material 3](https://m3.material.io/)
- [Coil](https://github.com/coil-kt/coil) - 图片加载
