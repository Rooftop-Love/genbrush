package com.example.genbrush

import com.example.genbrush.data.local.ImageEntry
import com.example.genbrush.data.provider.ImageEditRequest
import com.example.genbrush.data.provider.ImageProvider
import com.example.genbrush.data.provider.ProviderCredentials
import com.example.genbrush.data.provider.ProviderDefinition
import com.example.genbrush.data.provider.ProviderImage
import com.example.genbrush.data.provider.ProviderRegistry
import com.example.genbrush.data.provider.SizeOption
import com.example.genbrush.data.provider.TextToImageRequest
import com.example.genbrush.data.repository.GenerationCore
import com.example.genbrush.data.repository.GenerationLogSink
import com.example.genbrush.data.repository.GenerationPreferences
import com.example.genbrush.data.repository.GenerationStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖生成编排的分支：凭据校验、提供商回退、结果落盘、错误透传与协作取消。 */
class GenerationCoreTest {

    private class FakeProvider(
        override val definition: ProviderDefinition,
        var textResult: Result<ProviderImage> = Result.success(ProviderImage.Raw(byteArrayOf(1))),
        var editResult: Result<ProviderImage> = Result.success(ProviderImage.Raw(byteArrayOf(2))),
        var textThrowable: Throwable? = null,
        var editThrowable: Throwable? = null
    ) : ImageProvider {
        var textCalls = 0
        var editCalls = 0
        var lastCredentials: ProviderCredentials? = null
        var lastTextRequest: TextToImageRequest? = null
        var lastEditRequest: ImageEditRequest? = null

        override suspend fun generateTextToImage(
            credentials: ProviderCredentials,
            request: TextToImageRequest
        ): Result<ProviderImage> {
            textCalls++
            lastCredentials = credentials
            lastTextRequest = request
            textThrowable?.let { throw it }
            return textResult
        }

        override suspend fun generateImageEdit(
            credentials: ProviderCredentials,
            request: ImageEditRequest
        ): Result<ProviderImage> {
            editCalls++
            lastCredentials = credentials
            lastEditRequest = request
            editThrowable?.let { throw it }
            return editResult
        }

        override fun supportedSizes(model: String?): List<SizeOption> = emptyList()
    }

    private class FakePrefs(
        override var providerId: String,
        var key: String
    ) : GenerationPreferences {
        val queriedIds = mutableListOf<String>()
        override fun apiKey(providerId: String): String {
            queriedIds += providerId
            return key
        }
    }

    private class FakeStorage : GenerationStorage {
        var urlCalls = 0
        var bytesCalls = 0
        var lastEntry: ImageEntry? = null
        var failWith: Throwable? = null

        override suspend fun saveFromUrl(url: String, entry: ImageEntry): ImageEntry {
            urlCalls++
            lastEntry = entry
            failWith?.let { throw it }
            return entry
        }

        override suspend fun saveFromBytes(bytes: ByteArray, entry: ImageEntry): ImageEntry {
            bytesCalls++
            lastEntry = entry
            failWith?.let { throw it }
            return entry
        }
    }

    private class FakeLog : GenerationLogSink {
        val infos = mutableListOf<String>()
        val errors = mutableListOf<String>()
        override fun info(message: String) { infos += message }
        override fun error(message: String, throwable: Throwable?) { errors += message }
    }

    private data class Harness(
        val core: GenerationCore,
        val prefs: FakePrefs,
        val storage: FakeStorage,
        val log: FakeLog
    )

    private fun definition(id: String, supportsEdit: Boolean = true) = ProviderDefinition(
        id = id,
        nameZh = id,
        nameEn = id,
        defaultModel = "model-$id",
        presetGenerationModels = listOf("model-$id"),
        presetEditModels = listOf("model-$id"),
        supportsImageEdit = supportsEdit
    )

    private fun harness(
        vararg providers: FakeProvider,
        apiKey: String = "sk-test",
        providerId: String = "dashscope",
        storage: FakeStorage = FakeStorage(),
        log: FakeLog = FakeLog()
    ): Harness {
        val prefs = FakePrefs(providerId, apiKey)
        val core = GenerationCore(
            registry = ProviderRegistry(providers.toList()),
            prefs = prefs,
            storage = storage,
            log = log,
            createFileName = { "fixed_$it.jpg" }
        )
        return Harness(core, prefs, storage, log)
    }

    private fun textRequest() = TextToImageRequest(
        prompt = "a cat",
        negativePrompt = "blurry",
        model = "model-dashscope",
        size = "1024*1024"
    )

    @Test
    fun `缺少 API Key 时直接失败且不调用 provider`() {
        val provider = FakeProvider(definition("dashscope"))
        val h = harness(provider, apiKey = "  ")

        val result = runBlocking { h.core.textToImage(textRequest()) }

        assertTrue(result.isFailure)
        assertEquals(GenerationCore.ERROR_MISSING_API_KEY, result.exceptionOrNull()?.message)
        assertEquals(0, provider.textCalls)
        assertEquals(0, h.storage.urlCalls + h.storage.bytesCalls)
        assertTrue(h.log.errors.any { it.contains("Generation failed") })
    }

    @Test
    fun `未知提供商 id 回退到首个注册项`() {
        val primary = FakeProvider(definition("dashscope"))
        val secondary = FakeProvider(definition("agnes"))
        val h = harness(primary, secondary, providerId = "unknown-provider")

        val result = runBlocking { h.core.textToImage(textRequest()) }

        assertTrue(result.isSuccess)
        assertEquals("dashscope", h.prefs.queriedIds.single())
        assertEquals(1, primary.textCalls)
        assertEquals(0, secondary.textCalls)
        assertTrue(h.log.infos.first().contains("provider=dashscope"))
    }

    @Test
    fun `文生图 Remote 结果按 URL 落盘`() {
        val provider = FakeProvider(definition("dashscope")).apply {
            textResult = Result.success(ProviderImage.Remote("https://example.com/a.png"))
        }
        val h = harness(provider)

        val result = runBlocking { h.core.textToImage(textRequest()) }

        assertTrue(result.isSuccess)
        assertEquals(1, h.storage.urlCalls)
        assertEquals(0, h.storage.bytesCalls)
        val entry = h.storage.lastEntry!!
        assertEquals("fixed_text_to_image.jpg", entry.fileName)
        assertEquals("text_to_image", entry.type)
        assertEquals("blurry", entry.negativePrompt)
        assertSame(entry, result.getOrNull())
    }

    @Test
    fun `文生图 Raw 结果按字节落盘`() {
        val provider = FakeProvider(definition("dashscope")).apply {
            textResult = Result.success(ProviderImage.Raw(byteArrayOf(9, 8, 7)))
        }
        val h = harness(provider)

        val result = runBlocking { h.core.textToImage(textRequest()) }

        assertTrue(result.isSuccess)
        assertEquals(1, h.storage.bytesCalls)
        assertEquals(0, h.storage.urlCalls)
    }

    @Test
    fun `provider 失败时透传原始异常且不落盘`() {
        val failure = IllegalStateException("quota exceeded")
        val provider = FakeProvider(definition("dashscope")).apply {
            textResult = Result.failure(failure)
        }
        val h = harness(provider)

        val result = runBlocking { h.core.textToImage(textRequest()) }

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertEquals(0, h.storage.urlCalls + h.storage.bytesCalls)
    }

    @Test
    fun `落盘异常被包装为保存失败`() {
        val provider = FakeProvider(definition("dashscope"))
        val storage = FakeStorage().apply { failWith = Exception("disk full") }
        val h = harness(provider, storage = storage)

        val result = runBlocking { h.core.textToImage(textRequest()) }

        assertTrue(result.isFailure)
        assertEquals("保存图片失败：disk full", result.exceptionOrNull()?.message)
    }

    @Test
    fun `文生图请求与凭据原样传给 provider`() {
        val provider = FakeProvider(definition("dashscope"))
        val h = harness(provider, apiKey = "sk-abc")

        runBlocking { h.core.textToImage(textRequest()) }

        assertEquals("sk-abc", provider.lastCredentials?.apiKey)
        assertEquals(textRequest(), provider.lastTextRequest)
    }

    @Test
    fun `不支持图像编辑时失败且不读取原图`() {
        val provider = FakeProvider(definition("dashscope", supportsEdit = false))
        val h = harness(provider)
        var encoded = false

        val result = runBlocking {
            h.core.imageEdit("cat", "m", "1024*1024", "content") {
                encoded = true
                "data:image/jpeg;base64,AAA"
            }
        }

        assertTrue(result.isFailure)
        assertEquals(GenerationCore.ERROR_EDIT_UNSUPPORTED, result.exceptionOrNull()?.message)
        assertFalse(encoded)
        assertEquals(0, provider.editCalls)
    }

    @Test
    fun `图像编辑缺少 API Key 时不读取原图`() {
        val provider = FakeProvider(definition("dashscope"))
        val h = harness(provider, apiKey = "")
        var encoded = false

        val result = runBlocking {
            h.core.imageEdit("cat", "m", "1024*1024", "content") {
                encoded = true
                "data:image/jpeg;base64,AAA"
            }
        }

        assertTrue(result.isFailure)
        assertEquals(GenerationCore.ERROR_MISSING_API_KEY, result.exceptionOrNull()?.message)
        assertFalse(encoded)
    }

    @Test
    fun `图像编辑成功时传入 Data URI 且反向提示词为空`() {
        val provider = FakeProvider(definition("dashscope"))
        val h = harness(provider)

        val result = runBlocking {
            h.core.imageEdit("cat", "m", "1024*1024", "content") {
                "data:image/jpeg;base64,AAA"
            }
        }

        assertTrue(result.isSuccess)
        val request = provider.lastEditRequest!!
        assertEquals("data:image/jpeg;base64,AAA", request.imageDataUri)
        assertEquals("", request.negativePrompt)
        assertEquals("cat", request.prompt)
        assertEquals("image_edit", h.storage.lastEntry?.type)
        assertEquals("", h.storage.lastEntry?.negativePrompt)
        assertTrue(h.log.infos.first().contains("imageScheme=content"))
    }

    @Test
    fun `取消异常向上抛出且不写成失败日志`() {
        val provider = FakeProvider(definition("dashscope")).apply {
            textThrowable = CancellationException("stopped by user")
        }
        val h = harness(provider)

        assertThrows(CancellationException::class.java) {
            runBlocking { h.core.textToImage(textRequest()) }
        }

        assertTrue(h.log.errors.isEmpty())
        assertEquals(0, h.storage.urlCalls + h.storage.bytesCalls)
    }

    @Test
    fun `成功路径记录 start 与 success 日志`() {
        val provider = FakeProvider(definition("dashscope"))
        val h = harness(provider)

        runBlocking { h.core.textToImage(textRequest()) }

        assertEquals(2, h.log.infos.size)
        assertTrue(h.log.infos[0].startsWith("Generation start type=text_to_image"))
        assertTrue(h.log.infos[1].startsWith("Generation success type=text_to_image"))
        assertTrue(h.log.errors.isEmpty())
    }
}
