package com.example.genbrush.data.repository

import com.example.genbrush.data.local.ImageEntry
import com.example.genbrush.data.provider.ImageEditRequest
import com.example.genbrush.data.provider.ProviderCredentials
import com.example.genbrush.data.provider.ProviderImage
import com.example.genbrush.data.provider.ProviderRegistry
import com.example.genbrush.data.provider.TextToImageRequest
import kotlinx.coroutines.CancellationException

/** Repository 需要的配置读取能力，纯接口，便于 JVM 单测替换。 */
interface GenerationPreferences {
    val providerId: String
    fun apiKey(providerId: String): String
}

/** 生成结果的落盘能力，纯接口，屏蔽 Room / 文件系统。 */
interface GenerationStorage {
    suspend fun saveFromUrl(url: String, entry: ImageEntry): ImageEntry
    suspend fun saveFromBytes(bytes: ByteArray, entry: ImageEntry): ImageEntry
}

/** 诊断日志出口，纯接口，屏蔽 AppLogStore 的 Android 依赖。 */
interface GenerationLogSink {
    fun info(message: String)
    fun error(message: String, throwable: Throwable? = null)
}

/**
 * 生成流程的编排核心：解析提供商 → 校验凭据与能力 → 调用 provider → 结果落盘 → 记录日志。
 *
 * 这一层刻意保持纯 Kotlin（不含 Context / Uri / Bitmap / SharedPreferences），
 * 使「缺 Key、不支持编辑、provider 失败、保存失败、协作取消」等分支都能在 JVM 单测中覆盖。
 * [GenerationRepository] 负责把 Android 侧依赖适配进来。
 */
class GenerationCore(
    private val registry: ProviderRegistry,
    private val prefs: GenerationPreferences,
    private val storage: GenerationStorage,
    private val log: GenerationLogSink,
    private val createFileName: (String) -> String
) {

    suspend fun textToImage(request: TextToImageRequest): Result<ImageEntry> {
        val provider = registry.resolve(prefs.providerId)
        val providerId = provider.definition.id
        log.info(
            "Generation start type=text_to_image provider=$providerId model=${request.model} " +
                "size=${request.size} promptChars=${request.prompt.length} " +
                "negativePromptChars=${request.negativePrompt.length}"
        )
        val result = try {
            val credentials = ProviderCredentials(prefs.apiKey(providerId))
            if (credentials.apiKey.isBlank()) {
                Result.failure(Exception(ERROR_MISSING_API_KEY))
            } else {
                provider.generateTextToImage(credentials, request).fold(
                    onSuccess = {
                        saveImage(
                            it,
                            request.prompt,
                            request.model,
                            request.size,
                            request.negativePrompt,
                            "text_to_image"
                        )
                    },
                    onFailure = { Result.failure(it) }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result
            .onSuccess {
                log.info(
                    "Generation success type=text_to_image provider=$providerId " +
                        "model=${it.model} file=${it.fileName}"
                )
            }
            .onFailure {
                log.error(
                    "Generation failed type=text_to_image provider=$providerId " +
                        "model=${request.model} size=${request.size}: ${it.message}",
                    it
                )
            }
        return result
    }

    /**
     * [imageDataUri] 以 suspend lambda 传入：只有凭据与能力校验通过后才会读取并编码原图，
     * 避免无 Key / 不支持编辑时白白做一次图片解码。
     */
    suspend fun imageEdit(
        prompt: String,
        model: String,
        size: String,
        imageScheme: String,
        imageDataUri: suspend () -> String
    ): Result<ImageEntry> {
        val provider = registry.resolve(prefs.providerId)
        val providerId = provider.definition.id
        log.info(
            "Generation start type=image_edit provider=$providerId model=$model size=$size " +
                "promptChars=${prompt.length} imageScheme=$imageScheme"
        )
        val result = try {
            val credentials = ProviderCredentials(prefs.apiKey(providerId))
            if (!provider.definition.supportsImageEdit) {
                Result.failure(Exception(ERROR_EDIT_UNSUPPORTED))
            } else if (credentials.apiKey.isBlank()) {
                Result.failure(Exception(ERROR_MISSING_API_KEY))
            } else {
                provider.generateImageEdit(
                    credentials,
                    ImageEditRequest(
                        prompt = prompt,
                        negativePrompt = "",
                        model = model,
                        size = size,
                        imageDataUri = imageDataUri()
                    )
                ).fold(
                    onSuccess = { saveImage(it, prompt, model, size, "", "image_edit") },
                    onFailure = { Result.failure(it) }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result
            .onSuccess {
                log.info(
                    "Generation success type=image_edit provider=$providerId " +
                        "model=${it.model} file=${it.fileName}"
                )
            }
            .onFailure {
                log.error(
                    "Generation failed type=image_edit provider=$providerId model=$model " +
                        "size=$size: ${it.message}",
                    it
                )
            }
        return result
    }

    private suspend fun saveImage(
        image: ProviderImage,
        prompt: String,
        model: String,
        size: String,
        negativePrompt: String,
        type: String
    ): Result<ImageEntry> = try {
        val entry = ImageEntry(
            fileName = createFileName(type),
            prompt = prompt,
            model = model,
            type = type,
            size = size,
            negativePrompt = negativePrompt
        )
        val saved = when (image) {
            is ProviderImage.Remote -> storage.saveFromUrl(image.url, entry)
            is ProviderImage.Raw -> storage.saveFromBytes(image.bytes, entry)
        }
        Result.success(saved)
    } catch (e: Exception) {
        Result.failure(Exception("保存图片失败：${e.message}"))
    }

    companion object {
        const val ERROR_MISSING_API_KEY = "请先在设置中配置 API Key"
        const val ERROR_EDIT_UNSUPPORTED = "当前提供商不支持图像编辑"
    }
}
