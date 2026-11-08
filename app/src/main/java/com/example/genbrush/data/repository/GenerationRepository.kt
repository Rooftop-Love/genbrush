package com.example.genbrush.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.genbrush.data.local.AppLogStore
import com.example.genbrush.data.local.ImageEntry
import com.example.genbrush.data.local.ImageStore
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.provider.ProviderRegistry
import com.example.genbrush.data.provider.TextToImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 生成入口：把 Android 侧依赖（图片存储、加密偏好、日志）适配给 [GenerationCore]，
 * 编排逻辑本身在 Core 中，可脱离设备做单元测试。
 */
class GenerationRepository(
    private val registry: ProviderRegistry,
    private val imageStore: ImageStore,
    private val prefs: PreferencesManager
) : GenerationPreferences {

    override val providerId: String
        get() = prefs.providerId

    override fun apiKey(providerId: String): String = prefs.providerApiKey(providerId)

    private val storage = object : GenerationStorage {
        override suspend fun saveFromUrl(url: String, entry: ImageEntry): ImageEntry =
            imageStore.saveImageFromUrl(url, entry)

        override suspend fun saveFromBytes(bytes: ByteArray, entry: ImageEntry): ImageEntry =
            imageStore.saveImageFromBytes(bytes, entry)
    }

    private val log = object : GenerationLogSink {
        override fun info(message: String) = AppLogStore.info(message)

        override fun error(message: String, throwable: Throwable?) =
            AppLogStore.error(message, throwable)
    }

    private val core = GenerationCore(
        registry = registry,
        prefs = this,
        storage = storage,
        log = log,
        createFileName = { ImageStore.createFileName(it) }
    )

    suspend fun generateTextToImage(
        prompt: String,
        negativePrompt: String,
        model: String,
        size: String
    ): Result<ImageEntry> = core.textToImage(
        TextToImageRequest(
            prompt = prompt,
            negativePrompt = negativePrompt,
            model = model,
            size = size
        )
    )

    suspend fun generateImageEdit(
        imageUri: Uri,
        prompt: String,
        model: String,
        size: String,
        context: Context
    ): Result<ImageEntry> = core.imageEdit(
        prompt = prompt,
        model = model,
        size = size,
        imageScheme = imageUri.scheme.orEmpty(),
        imageDataUri = { "data:image/jpeg;base64," + encodeImageToBase64(imageUri, context) }
    )

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
