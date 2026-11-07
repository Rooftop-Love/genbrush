# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Source of truth.** This file is authoritative for project structure, build setup and conventions. `README.md` (user-facing overview) and `docs/CODE_WIKI.md` (detailed code map) are derived from it — if they disagree, fix them to match this file. Current code state: **v1.2** (versionName `1.2`, versionCode `3`).

## Project Overview

GenBrush is an Android app for AI image generation and editing, built with Jetpack Compose and Kotlin. It uses a multi-provider architecture (`data/provider/`) with **DashScope (Alibaba Cloud)** and **Agnes AI** as the registered providers; new providers are added by implementing `ImageProvider`, supplying a `ProviderDefinition` and registering it in `GenBrushApp`. Package / application id: `com.example.genbrush`. Single Gradle module `:app`.

## Build & Run

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (signed only when credentials are available)
./gradlew test                   # JVM unit tests
./gradlew testDebugUnitTest --tests "com.example.genbrush.ErrorMapperTest"
./gradlew connectedAndroidTest   # instrumented tests (currently template only)
./gradlew clean
```

On Windows, use `gradlew.bat` instead of `./gradlew`. Requires **JDK 17+** and Android Studio Ladybug or newer. `settings.gradle.kts` enables Aliyun Maven mirrors locally and skips them on CI (`CI` env var).

### Release signing

Signing credentials are **never** stored in the repository. `app/build.gradle.kts` reads `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD` and `SIGNING_KEY_ALIAS` from Gradle properties (`-P` or `~/.gradle/gradle.properties`) or environment variables, and only creates the release signing config when `genbrush-release.jks` exists at the project root **and** a store password is present. Without them, debug and release builds still succeed and release produces `app-release-unsigned.apk`.

`.github/workflows/release.yml` (triggered by `v*` tags) restores the keystore from secret `SIGNING_KEY_STORE_BASE64`, injects the password secrets as env vars, fails fast when required secrets are missing, then uploads the APK to a GitHub Release. `.github/workflows/android.yml` runs `assembleDebug` + `test` on push/PR and uploads the debug APK.

## Tech Stack

- **Language**: Kotlin 2.2.10; Java 11 bytecode target
- **UI**: Jetpack Compose + Material 3 (BOM 2026.02.01), Navigation Compose 2.9.6
- **Networking**: OkHttp 4.12.0 + Gson 2.11.0
- **Image loading**: Coil 3.0.4
- **Local storage**: Room 2.7.0 (KSP) + EncryptedSharedPreferences (`androidx.security:security-crypto` 1.1.0-alpha06)
- **Build**: Gradle 9.4.1, AGP 9.2.1, KSP 2.3.6, Kotlin DSL, version catalog at `gradle/libs.versions.toml`
- **SDK**: min 24, target/compile 36 (`compileSdk { release(36) { minorApiLevel = 1 } }`)
- **Version**: 1.2 (versionCode 3)

## Architecture

- MVVM + Repository, single module, **hand-wired dependencies** (no Hilt/Koin): `GenBrushApp` (Application) builds the `OkHttpClient` (60s connect, 300s read/write, 360s call), Gson, `ImageStore`, `ProviderRegistry`, `GenerationRepository` and the Coil `SingletonImageLoader` (25% memory / 2% disk cache). `MainActivity` is a `ComponentActivity` that reads those dependencies from `application` and mounts `AppNavigation` inside `GenBrushTheme`.
- **Provider abstraction** (`data/provider/`):
  - `ImageProvider.kt` — interface (`generateTextToImage`, `generateImageEdit`, `supportedSizes`, optional `fetchModels`) plus provider-agnostic `TextToImageRequest` / `ImageEditRequest` / `ProviderImage` (`Remote` URL or `Raw` bytes) / `ProviderCredentials` / `ProviderException`.
  - `ProviderDefinition.kt` — id, localized display name, preset generation/edit models, default model, `supportsImageEdit`, API key hints, `SizeOption`.
  - `ProviderRegistry.kt` — list of registered providers, `find(id)` and `resolve(id)` falling back to the first registered provider.
  - `data/provider/dashscope/` — sync qwen-image generation, async wan task submit + 5s/120-attempt polling, image edit (sync or async by model family); `DashScopeDefinition` owns model families and size tables.
  - `data/provider/agnes/` — OpenAI-compatible `POST /v1/images/generations` for both text-to-image and image edit (`extra_body.image` Data URI array; `response_format` must be inside `extra_body`); `AgnesDefinition` converts internal `W*H` sizes to tier+ratio for 2.1/2.5 models and exact `WxH` for 2.0.
- `GenerationRepository` resolves the active provider from `prefs.providerId`, validates the API key and `supportsImageEdit`, encodes edit input as a downsampled JPEG Data URI, then saves the returned `ProviderImage` via `ImageStore` (URL download or raw bytes) into Room. It owns logging and lets `CancellationException` propagate.
- **Networking helpers** (`data/network/`): `OkHttpExtensions.executeCancellable` bridges OkHttp calls to `suspendCancellableCoroutine` and cancels the call on coroutine cancellation; `NetworkLoggingInterceptor` writes method/URL/status summaries (query and fragment stripped) to `AppLogStore`. DEBUG builds additionally add `HttpLoggingInterceptor` at BODY level with `Authorization` / `X-API-Key` redacted.
- **Diagnostics**: `AppLogStore` keeps a bounded 512KB `recent.log` under `filesDir/logs`, redacts bearer tokens, api keys and access/refresh tokens, and exports a versioned snapshot through SAF from the Settings screen.
- **Local storage** (`data/local/`): Room database `genbrush.db` (version 1, `exportSchema = false`, `images` table with `timestamp` / `isFavorite` indices); `ImageStore` exposes `ImageEntry` DTOs, stores files under `filesDir/generated_images`, and migrates legacy `metadata.json` into Room once (renaming it to `.bak`); `ImageSaver` saves to the system gallery with sampling/OOM protection; `DataMigrationManager` exports/imports a ZIP of `settings.json`, `metadata.json` and `images/*`.
- `PreferencesManager` uses `EncryptedSharedPreferences` (AES256_GCM master key, AES256_SIV key / AES256_GCM value encryption). All provider-scoped settings use keys suffixed with the provider id (`api_key_<id>`, `default_model_<id>`, etc.), with legacy global-key fallbacks and `backend_type → provider_id` migration. `configVersion: StateFlow<Long>` increments on config writes so generation ViewModels refresh models/sizes without relying on lifecycle timing.
- **UI** (`ui/`): four bottom-tab screens (`TextToImage`, `ImageEdit`, `Gallery`, `Settings`) plus a `gallery/image/{imageId}` detail route; `savedStateHandle` carries regenerate/edit parameters back to generation screens. Each screen has a ViewModel exposing `StateFlow` state with a companion `factory(...)`. `GalleryViewModel.computeDisplayedImages` composes favorite/type/search filters and sorting in state; `FullImageViewerScreen` uses `HorizontalPager` with per-page zoom state. `AppStrings` (ZH/EN) + `LocalStrings` provide code-level localization; `GenBrushApp.language: StateFlow<String>` drives recomposition. `ErrorMapper` maps `ProviderException` and common error keywords to localized messages.
- `AndroidManifest.xml`: `allowBackup="false"`, INTERNET / READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE (maxSdk 32), FileProvider `${applicationId}.fileprovider` exposing `generated_images/` and `image_cache/`.

## Key Conventions

- Kotlin DSL build scripts with version-catalog aliases; **do not pin individual Compose library versions** (BOM manages them).
- Release builds **do not enable R8 minification**; `proguard-rules.pro` is retained but inactive. `buildConfig = true`, `compose = true`.
- Provider implementations must stay free of persistence and UI concerns; size tables and model-family predicates live in each `*Definition`, not in Compose components.
- All user-facing strings live in `ui/localization/AppStrings.kt` (ZH and EN). Adding a string requires both translations.
- Never commit signing credentials, API keys or `local.properties`. Keystore files are ignored by `.gitignore`.
- `network_security_config.xml` currently permits cleartext HTTP globally, reserved for a future local-endpoint provider; both registered providers use HTTPS.
- `data_extraction_rules.xml` is still the Android Studio template (contains a TODO), while `allowBackup="false"` disables cloud backup.
- Tests: 5 JVM test classes / 22 cases covering API DTOs, size params, `ErrorMapper` and log redaction. No lint/ktlint/detekt is configured despite `CONTRIBUTING.md` mentioning `ktlintCheck`; instrumented tests are still the template.

## Documentation

- `README.md` — user-facing features, requirements, build and signing instructions, directory overview.
- `docs/CODE_WIKI.md` — detailed module responsibilities, key types, data flows, provider comparison, conventions and known limitations.
- When changing architecture, build config or dependencies, update this file first, then the other two.
