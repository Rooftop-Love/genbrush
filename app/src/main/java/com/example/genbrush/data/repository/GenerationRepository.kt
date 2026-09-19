package com.example.genbrush.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.genbrush.data.local.AppLogStore
import com.example.genbrush.data.local.ImageEntry
import com.example.genbrush.data.local.ImageStore
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.provider.ImageEditRequest
import com.example.genbrush.data.provider.ProviderCredentials
import com.example.genbrush.data.provider.ProviderImage
import com.example.genbrush.data.provider.ProviderRegistry
import com.example.genbrush.data.provider.TextToImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 生成入口：按 prefs 中的 provider id 从 [ProviderRegistry] 解析当前提供商并转发请求。
 * 本类只负责编排（凭据、日志、取消）与结果落盘入库；远端调用细节在各 ImageProvider 实现里。
 */
class GenerationRepository(
    private val registry: ProviderRegistry,
    private val imageStore: ImageStore,
    private val prefs: PreferencesManager
) {
    suspend fun generateTextToImage(
        prompt: String,
        negativePrompt: String,
        model: String,
        size: String
    ): Result<ImageEntry> {
        val provider = registry.resolve(prefs.providerId)
        val providerId = provider.definition.id
        AppLogStore.info(
            "Generation start type=text_to_image provider=$providerId model=$model size=$size " +
                "promptChars=${prompt.length} negativePromptChars=${negativePrompt.length}"
        )
        val result = try {
            val credentials = ProviderCredentials(prefs.providerApiKey(providerId))
            if (credentials.apiKey.isBlank()) {
                Result.failure(Exception("请先在设置中配置 API Key"))
            } else {
                provider.generateTextToImage(
                    credentials,
                    TextToImageRequest(
                        prompt = prompt,
                        negativePrompt = negativePrompt,
                        model = model,
                        size = size
                    )
                ).fold(
                    onSuccess = { saveImage(it, prompt, model, size, negativePrompt, "text_to_image") },
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
                AppLogStore.info(
                    "Generation success type=text_to_image provider=$providerId model=${it.model} " +
                        "file=${it.fileName}"
                )
            }
            .onFailure {
                AppLogStore.error(
                    "Generation failed type=text_to_image provider=$providerId model=$model size=$size: ${it.message}",
                    it
                )
            }
        return result
    }

    suspend fun generateImageEdit(
        imageUri: Uri,
        prompt: String,
        model: String,
        size: String,
        context: Context
    ): Result<ImageEntry> {
        val provider = registry.resolve(prefs.providerId)
        val providerId = provider.definition.id
        AppLogStore.info(
            "Generation start type=image_edit provider=$providerId model=$model size=$size " +
                "promptChars=${prompt.length} imageScheme=${imageUri.scheme.orEmpty()}"
        )
        val result = try {
            val credentials = ProviderCredentials(prefs.providerApiKey(providerId))
            if (credentials.apiKey.isBlank()) {
                Result.failure(Exception("请先在设置中配置 API Key"))
            } else {
                val imageDataUri = "data:image/jpeg;base64," + encodeImageToBase64(imageUri, context)
                provider.generateImageEdit(
                    credentials,
                    ImageEditRequest(
                        prompt = prompt,
                        negativePrompt = "",
                        model = model,
                        size = size,
                        imageDataUri = imageDataUri
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
                AppLogStore.info(
                    "Generation success type=image_edit provider=$providerId model=${it.model} file=${it.fileName}"
                )
            }
            .onFailure {
                AppLogStore.error(
                    "Generation failed type=image_edit provider=$providerId model=$model size=$size: ${it.message}",
                    it
                )
            }
        return result
    }

    // ==================== 结果落盘入库 ====================

    private suspend fun saveImage(
        image: ProviderImage,
        prompt: String,
        model: String,
        size: String,
        negativePrompt: String,
        type: String
    ): Result<ImageEntry> = try {
        val fileName = ImageStore.createFileName(type)
        val entry = ImageEntry(
            fileName = fileName,
            prompt = prompt,
            model = model,
            type = type,
            size = size,
            negativePrompt = negativePrompt
        )
        val saved = when (image) {
            is ProviderImage.Remote -> imageStore.saveImageFromUrl(image.url, entry)
            is ProviderImage.Raw -> imageStore.saveImageFromBytes(image.bytes, entry)
        }
        Result.success(saved)
    } catch (e: Exception) {
        Result.failure(Exception("保存图片失败：${e.message}"))
    }

    // ==================== 画廊 ====================

    suspend fun getGalleryImages(): List<ImageEntry> = imageStore.getAllEntries()

    fun getLocalImagePath(entry: ImageEntry) = imageStore.getImageFile(entry)

    suspend fun getLocalImagePathById(id: String) = imageStore.getImageFileById(id)

    suspend fun getEntryById(id: String) = imageStore.getEntryById(id)

    suspend fun deleteImage(entry: ImageEntry) = imageStore.deleteImage(entry)

    suspend fun setFavorite(id: String, favorite: Boolean) = imageStore.setFavorite(id, favorite)

    // ==================== 工具 ====================

    private suspend fun encodeImageToBase64(uri: Uri, context: Context): String =
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("无法打开图片")
            val bytes = inputStream.readBytes()
            inputStream.close()

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            val maxDim = maxOf(options.outWidth, options.outHeight)
            val sampleSize = if (maxDim > 2048) {
                var sample = 1
                while (maxDim / (sample * 2) >= 2048) sample *= 2
                sample
            } else 1

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                ?: throw Exception("解码图片失败")

            val resized = ImageStore.resizeBitmapIfNeeded(bitmap, 2048)

            val outputStream = ByteArrayOutputStream()
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
            if (resized !== bitmap) resized.recycle()
            bitmap.recycle()

            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
}
