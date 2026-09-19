package com.example.genbrush.data.local

import android.content.Context
import android.graphics.Bitmap
import com.example.genbrush.data.local.db.AppDatabase
import com.example.genbrush.data.local.db.ImageDao
import com.example.genbrush.data.local.db.ImageEntity
import com.example.genbrush.data.network.NetworkLoggingInterceptor
import com.example.genbrush.data.network.executeCancellable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 图片存储统一入口。
 *
 * 对外继续以 [ImageEntry] 作为 DTO（Repository / UI 层不感知数据库），
 * 内部持久化由 Room 数据库承担。首次启动时会检测旧的 metadata.json，
 * 若数据库为空则将其一次性导入并备份为 metadata.json.bak。
 */
data class ImageEntry(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val prompt: String,
    val model: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "text_to_image" or "image_edit"
    val size: String? = null,
    val negativePrompt: String? = null,
    val isFavorite: Boolean = false,
    val seed: Long? = null
)

class ImageStore(private val context: Context) {

    private val gson = Gson()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .addInterceptor(NetworkLoggingInterceptor("ImageDownload"))
        .build()
    private val imageDir: File
        get() = File(context.filesDir, "generated_images").also { it.mkdirs() }

    /** 旧的元数据文件，迁移后会被重命名为 .bak */
    private val legacyMetadataFile: File
        get() = File(imageDir, "metadata.json")

    private val dao: ImageDao by lazy { AppDatabase.getInstance(context).imageDao() }

    /**
     * 首次启动迁移：数据库为空且存在旧 metadata.json 时，
     * 全量导入并备份原文件。在 IO 线程执行一次。
     */
    private suspend fun migrateLegacyJsonIfNeeded() = withContext(Dispatchers.IO) {
        if (!legacyMetadataFile.exists()) return@withContext
        if (dao.count() > 0) return@withContext
        try {
            val json = legacyMetadataFile.readText()
            if (json.isNotBlank()) {
                val type = object : TypeToken<List<ImageEntry>>() {}.type
                val entries: List<ImageEntry> = gson.fromJson(json, type) ?: emptyList()
                if (entries.isNotEmpty()) {
                    dao.insertAll(entries.map { it.toEntity() })
                }
            }
        } catch (e: Exception) {
            // 迁移失败不阻塞启动，保留原文件以便排查
            return@withContext
        }
        // 迁移成功后备份原文件
        runCatching {
            val bak = File(imageDir, "metadata.json.bak")
            legacyMetadataFile.renameTo(bak)
        }
    }

    suspend fun saveImage(bitmap: Bitmap, entry: ImageEntry): ImageEntry = withContext(Dispatchers.IO) {
        ensureMigrated()
        val file = File(imageDir, entry.fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        dao.insert(entry.toEntity())
        entry
    }

    suspend fun saveImageFromUrl(url: String, entry: ImageEntry): ImageEntry = withContext(Dispatchers.IO) {
        ensureMigrated()
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.executeCancellable(request)
        if (!response.isSuccessful) {
            throw Exception("下载图片失败: HTTP ${response.code}")
        }
        val body = response.body ?: throw Exception("下载图片失败: 响应体为空")
        val file = File(imageDir, entry.fileName)
        body.byteStream().use { input ->
            FileOutputStream(file).use { out ->
                input.copyTo(out)
            }
        }
        dao.insert(entry.toEntity())
        entry
    }

    suspend fun saveImageFromBytes(bytes: ByteArray, entry: ImageEntry): ImageEntry = withContext(Dispatchers.IO) {
        ensureMigrated()
        val file = File(imageDir, entry.fileName)
        FileOutputStream(file).use { out ->
            out.write(bytes)
        }
        dao.insert(entry.toEntity())
        entry
    }

    suspend fun getAllEntries(): List<ImageEntry> = withContext(Dispatchers.IO) {
        ensureMigrated()
        dao.getAll().map { it.toEntry() }
    }

    fun getImageFile(entry: ImageEntry): File = File(imageDir, entry.fileName)

    suspend fun getImageFileById(id: String): File? = withContext(Dispatchers.IO) {
        ensureMigrated()
        val entity = dao.getById(id) ?: return@withContext null
        val file = File(imageDir, entity.fileName)
        if (file.exists()) file else null
    }

    suspend fun getEntryById(id: String): ImageEntry? = withContext(Dispatchers.IO) {
        ensureMigrated()
        dao.getById(id)?.toEntry()
    }

    suspend fun deleteImage(entry: ImageEntry) = withContext(Dispatchers.IO) {
        ensureMigrated()
        val file = File(imageDir, entry.fileName)
        if (file.exists()) file.delete()
        dao.deleteById(entry.id)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        ensureMigrated()
        dao.setFavorite(id, favorite)
    }

    /**
     * 导入历史条目（用于数据迁移 zip 导入），跳过已存在的 id。
     * 调用方需保证对应的图片文件已拷贝到 [imageDir]。
     */
    suspend fun importEntries(entries: List<ImageEntry>): Int = withContext(Dispatchers.IO) {
        ensureMigrated()
        val existingIds = dao.getAll().map { it.id }.toSet()
        val toInsert = entries.filter { it.id !in existingIds && File(imageDir, it.fileName).exists() }
        if (toInsert.isNotEmpty()) {
            dao.insertAll(toInsert.map { it.toEntity() })
        }
        toInsert.size
    }

    private val migrated = kotlinx.coroutines.sync.Mutex()

    private suspend fun ensureMigrated() {
        if (!legacyMetadataFile.exists()) return
        migrated.lock()
        try {
            migrateLegacyJsonIfNeeded()
        } finally {
            migrated.unlock()
        }
    }

    companion object {
        fun createFileName(type: String): String {
            val ts = System.currentTimeMillis()
            val uuid = UUID.randomUUID().toString().take(8)
            return "${ts}_${uuid}.jpg"
        }

        fun resizeBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int = 2048): Bitmap {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= maxDimension && h <= maxDimension) return bitmap
            val scale = maxDimension.toFloat() / maxOf(w, h)
            val newW = (w * scale).toInt()
            val newH = (h * scale).toInt()
            return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        }
    }
}

/** Entity ↔ Entry 映射，集中在一处保持单一数据源 */
private fun ImageEntry.toEntity() = ImageEntity(
    id = id,
    fileName = fileName,
    prompt = prompt,
    model = model,
    timestamp = timestamp,
    type = type,
    size = size,
    negativePrompt = negativePrompt,
    isFavorite = isFavorite,
    seed = seed
)

private fun ImageEntity.toEntry() = ImageEntry(
    id = id,
    fileName = fileName,
    prompt = prompt,
    model = model,
    timestamp = timestamp,
    type = type,
    size = size,
    negativePrompt = negativePrompt,
    isFavorite = isFavorite,
    seed = seed
)
