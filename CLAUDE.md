# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GenBrush is an Android app for AI image generation and editing, built with Jetpack Compose and Kotlin. It uses a multi-provider architecture (`data/provider/`) with DashScope (Alibaba Cloud) and Agnes AI as the currently registered providers; new API providers can be added by implementing `ImageProvider` and registering it in `GenBrushApp`. Package: `com.example.genbrush`.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew testDebugUnitTest --tests "com.example.genbrush.ExampleUnitTest"

# Run instrumented (on-device) tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Tech Stack

- **Language**: Kotlin (2.2.10)
- **UI**: Jetpack Compose with Material 3 (BOM 2026.02.01)
- **Networking**: OkHttp + Gson
- **Image Loading**: Coil 3
- **Local Storage**: Room + EncryptedSharedPreferences
- **Build**: Gradle 9.4.1, AGP 9.2.1, Kotlin DSL build scripts
- **Min SDK**: 24 (Android 7.0), **Target/Compile SDK**: 36
- **Dependency catalog**: `gradle/libs.versions.toml` (version catalog)

## Architecture

- Single-module Gradle project (`app`)
- MVVM with Repository pattern and a provider abstraction:
  - `data/provider/ImageProvider.kt` — provider interface + provider-agnostic request/result/exception types (`ProviderException`); `ProviderRegistry` holds all registered providers
  - `data/provider/dashscope/` — DashScope implementation (sync qwen-image API, async wan task submit+poll, `DashScopeDefinition` owns preset models & size tables)
  - `data/provider/agnes/` — Agnes AI implementation (OpenAI-compatible images API with tier+ratio size mapping, `AgnesDefinition` owns preset models & size tables)
  - `GenerationRepository` resolves the active provider from `prefs.providerId` and handles persistence (ImageStore/Room) + logging
  - The active provider id, per-provider API keys, default model/size, and disabled/custom model lists are stored in EncryptedSharedPreferences (`PreferencesManager`)
- `MainActivity` is the entry point, using `ComponentActivity` with Compose `setContent`
- `GenBrushApp` (Application) initializes the HTTP client, builds the `ProviderRegistry`, and wires dependencies
- Theme: `GenBrushTheme` in `ui/theme/` with dynamic color support (Android 12+)
- All source lives under `app/src/main/java/com/example/genbrush/`

## Key Conventions

- Build scripts use Kotlin DSL (`.gradle.kts`) with version catalog aliases from `libs.versions.toml`
- Compose dependencies are managed via the Compose BOM — do not pin individual Compose library versions
- Release builds skip R8 minification (open-source project); `proguard-rules.pro` is retained but not active
