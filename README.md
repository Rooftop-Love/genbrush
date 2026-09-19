# GenBrush

AI 图像生成与编辑 Android 应用，基于 Jetpack Compose 构建。采用多提供商架构，当前内置 DashScope（阿里云百炼）提供商。

## Features

- **文生图** - 输入提示词生成图片，支持反向提示词
- **图像编辑** - 选择本地图片，用提示词进行 AI 编辑
- **多提供商架构** - 设置页可切换图像服务提供商（当前内置阿里云百炼，可扩展接入更多 API）
- **模型管理** - 预置模型 + 自定义模型，可自由启用/禁用
- **相册** - 浏览生成历史，支持搜索、类型筛选、排序、收藏、批量操作
- **图片详情** - 全屏查看、保存、分享、复制提示词、基于原图重新生成/编辑
- **数据迁移** - 导出/导入图片和设置数据
- **安全存储** - API Key 通过 AES-256 加密存储（EncryptedSharedPreferences）
- **Material 3** - 现代 UI，Android 12+ 支持动态取色
- **中英双语** - 支持中文/英文界面切换

## Requirements

- Android 7.0 (API 24) 或更高版本
- 阿里云百炼平台 API Key（[申请地址](https://dashscope.aliyun.com/)）

## Getting Started

1. 克隆仓库：
   ```bash
   git clone https://github.com/Rooftop-Love/genbrush.git
   ```

2. 在 Android Studio（建议 Ladybug 或更新版本）中打开项目。

3. 构建并运行到设备或模拟器。

4. 首次启动后进入 **设置 → 图像服务**，选择提供商并输入 API Key。

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

Windows 上使用 `gradlew.bat` 代替 `./gradlew`。

## Tech Stack

| 组件 | 技术 |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository |
| 网络 | OkHttp + Gson |
| 图片加载 | Coil 3 |
| 本地存储 | Room + EncryptedSharedPreferences |
| 构建 | Gradle 9.4.1 (Kotlin DSL) + AGP 9.2.1 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

## Project Structure

```
app/src/main/java/com/example/genbrush/
├── GenBrushApp.kt                 # Application（HTTP 客户端、提供商注册、依赖初始化）
├── MainActivity.kt                # 入口 Activity
├── data/
│   ├── local/
│   │   ├── db/                    # Room 数据库（AppDatabase、ImageDao、ImageEntity）
│   │   ├── DataMigrationManager.kt # 数据导入/导出
│   │   ├── ImageSaver.kt          # 保存图片到系统相册（OOM 保护）
│   │   ├── ImageStore.kt          # 图片文件存储与管理
│   │   └── PreferencesManager.kt  # 加密偏好设置（含提供商 id 与 API key）
│   ├── provider/
│   │   ├── ImageProvider.kt       # 提供商抽象（接口 + 通用请求/结果类型）
│   │   ├── ProviderDefinition.kt  # 提供商元数据（显示名、预设模型、能力）
│   │   ├── ProviderRegistry.kt    # 已注册提供商集合
│   │   └── dashscope/             # DashScope（阿里云百炼）实现
│   └── repository/
│       └── GenerationRepository.kt # 数据仓库（解析当前提供商并落盘入库）
└── ui/
    ├── common/                    # 通用工具（ErrorMapper）
    ├── components/                # 可复用组件（ModelSelector、SizeSelector 等）
    ├── gallery/                   # 相册（列表、搜索、筛选、批量操作）
    ├── imageedit/                 # 图像编辑界面
    ├── texttoimage/               # 文生图界面
    ├── settings/                  # 设置（提供商选择、API 配置、模型管理、数据迁移）
    ├── localization/              # 中英文字符串资源
    ├── navigation/                # 底部导航
    └── theme/                     # Material 3 主题
```

## 接入新的图像提供商

1. 在 `data/provider/<id>/` 下实现 `ImageProvider` 接口（文生图 + 图编辑）；
2. 提供该提供商的 `ProviderDefinition`（显示名、预设模型、默认模型等）；
3. 在 `GenBrushApp.onCreate()` 中构造并注册进 `ProviderRegistry`。

设置页的提供商选择器会自动列出新注册的提供商。

## Contributing

参见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## License

MIT License - 详见 [LICENSE](LICENSE)

## Acknowledgments

- [DashScope API](https://dashscope.aliyun.com/) - 阿里云百炼
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3](https://m3.material.io/)
- [Coil](https://github.com/coil-kt/coil) - 图片加载
