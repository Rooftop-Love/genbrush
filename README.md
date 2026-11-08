# GenBrush

AI 图像生成与编辑 Android 应用，基于 Jetpack Compose 构建。采用多提供商架构，内置 **DashScope（阿里云百炼）** 与 **Agnes AI** 两家图像服务，可在设置页切换，并分别配置 API Key、模型与默认尺寸。

> **文档约定**：`CLAUDE.md` 是项目结构与开发约定的唯一事实源，`docs/CODE_WIKI.md` 是详细代码地图，本文件面向使用者。三者如有冲突，以 `CLAUDE.md` 为准。

## Features

- **文生图** — 提示词 + 反向提示词，可选模型与尺寸；生成过程可随时停止，并显示已耗时
- **图像编辑** — 从相册选图，用提示词进行 AI 编辑
- **多提供商架构** — 设置页切换 DashScope / Agnes AI，配置按提供商隔离；新增提供商只需实现接口并注册
- **模型管理** — 每个提供商独立的预置模型启用/禁用、自定义模型增删
- **相册** — 搜索、类型筛选、排序、收藏、多选批量删除/收藏、保存到系统相册
- **图片详情** — 全屏翻页与缩放、保存/分享/删除、复制提示词、基于原图重新生成或继续编辑
- **数据迁移** — ZIP 导出/导入图片、元数据与设置（兼容旧版备份格式）
- **诊断日志** — 应用内记录最近日志（自动脱敏），设置页一键导出
- **安全存储** — API Key 与偏好设置经 `EncryptedSharedPreferences`（AES-256）加密存储
- **Material 3** — Android 12+ 动态取色；中英双语，支持跟随系统

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

> **升级提示**：v1.2 使用 2026-11-07 轮换后的新签名证书，与 v1.1 不同，v1.1 用户无法直接覆盖安装，需先卸载旧版再安装。

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
| 当前版本 | 1.2（versionCode 3，2026-11-07 已发布） |

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
│   │   ├── PreferenceKeys.kt       # 配置键名、旧键回退与导入合并规则
│   │   └── PreferencesManager.kt   # 加密偏好（provider_id 与按提供商命名空间的配置）
│   ├── network/
│   │   ├── OkHttpExtensions.kt           # executeCancellable：协程取消时取消 HTTP 请求
│   │   └── NetworkLoggingInterceptor.kt  # 请求/响应摘要写入 AppLogStore
│   ├── provider/
│   │   ├── ImageProvider.kt        # 接口 + 通用请求/结果/异常类型
│   │   ├── ProviderDefinition.kt   # 提供商元数据与能力声明
│   │   ├── ProviderRegistry.kt     # 注册表与未知 id 回退
│   │   ├── dashscope/              # DashScope：qwen 同步 / wan 异步轮询
│   │   └── agnes/                  # Agnes AI：OpenAI 兼容 images API
│   └── repository/
│       ├── GenerationCore.kt        # 纯 Kotlin 生成编排（可 JVM 单测）
│       └── GenerationRepository.kt  # Android 依赖适配、输入图编码
└── ui/
    ├── common/                     # ErrorMapper（错误本地化映射）
    ├── components/                 # ModelSelector、SizeSelector、结果/错误/加载卡片
    ├── gallery/                    # 相册列表、GalleryViewModel、全屏查看器
    ├── imageedit/                  # 图像编辑界面与 ViewModel
    ├── texttoimage/                # 文生图界面与 ViewModel
    ├── settings/                   # 提供商/API/模型/语言/迁移/日志导出
    ├── localization/               # AppStrings（ZH/EN）与 LocalStrings
    ├── navigation/                 # 底部导航与图片详情路由
    └── theme/                      # Material 3 主题
```

更完整的模块说明、数据流与约定参见 [docs/CODE_WIKI.md](docs/CODE_WIKI.md)。

## 接入新的图像提供商

1. 在 `data/provider/<id>/` 下实现 `ImageProvider`（文生图、图编辑、`supportedSizes`；可选 `fetchModels`）。
2. 提供对应的 `ProviderDefinition`（id、显示名、预设生成/编辑模型、默认模型、`supportsImageEdit`、API Key 提示）。
3. 在 `GenBrushApp.onCreate()` 中构造实现并注册进 `ProviderRegistry`。

设置页的提供商选择器会自动列出新注册项；API Key、默认模型/尺寸、禁用与自定义模型列表均按 `definition.id` 命名空间隔离存储。

## Testing

`./gradlew test` 运行 JVM 单元测试，覆盖 API 数据模型与尺寸参数转换、错误映射、日志脱敏、配置迁移与生成编排（7 个测试类 / 46 个用例）。`./gradlew connectedAndroidTest` 需要设备或模拟器，目前仅有模板用例。CI（`android.yml`）在 push / PR 时执行 `assembleDebug` + `test` + `lintDebug` 并上传 debug APK。

## Contributing

参见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## License

MIT License - 详见 [LICENSE](LICENSE)

## Acknowledgments

- [DashScope API](https://dashscope.aliyun.com/) - 阿里云百炼
- [Agnes AI](https://agnes-ai.com/) - 图像生成服务
- [Jetpack Compose](https://developer.android.com/jetpack/compose) / [Material 3](https://m3.material.io/)
- [Coil](https://github.com/coil-kt/coil) - 图片加载
