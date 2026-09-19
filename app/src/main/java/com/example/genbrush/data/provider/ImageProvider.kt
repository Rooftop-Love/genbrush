package com.example.genbrush.data.provider

/**
 * 提供商无关的生成请求/结果类型。provider 实现负责把它们翻译成自家 API 的形状；
 * 图片落盘与入库由 GenerationRepository 统一处理。
 */
data class TextToImageRequest(
    val prompt: String,
    val negativePrompt: String,
    val model: String,
    val size: String
)

data class ImageEditRequest(
    val prompt: String,
    val negativePrompt: String,
    val model: String,
    val size: String,
    /** 输入图片，data URI 形式（data:image/jpeg;base64,...） */
    val imageDataUri: String
)

/** provider 返回的生成结果：远端 URL 或原始图片字节 */
sealed interface ProviderImage {
    data class Remote(val url: String) : ProviderImage
    data class Raw(val bytes: ByteArray) {
        override fun equals(other: Any?) = other is Raw && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }
}

/**
 * 图像生成提供商抽象。
 *
 * 新增一个提供商的步骤：
 * 1. 实现本接口（放在 data/provider/<id>/ 下）；
 * 2. 提供对应的 [ProviderDefinition]（预设模型、显示名等元数据）；
 * 3. 在 GenBrushApp 中构造并注册进 [ProviderRegistry]。
 */
interface ImageProvider {
    val definition: ProviderDefinition

    suspend fun generateTextToImage(credentials: ProviderCredentials, request: TextToImageRequest): Result<ProviderImage>

    suspend fun generateImageEdit(credentials: ProviderCredentials, request: ImageEditRequest): Result<ProviderImage>

    /**
     * 该模型系列下可用的尺寸档位（供尺寸选择器渲染）。
     * 未知/自定义模型由各 provider 自行回退到默认档位。
     */
    fun supportedSizes(model: String?): List<SizeOption>

    /**
     * 在线拉取模型列表；不支持的提供商返回 null（此时使用 definition 中的预设模型）。
     */
    suspend fun fetchModels(): List<String>? = null
}

/** 调用提供商 API 所需的凭据。以后接入需要 baseUrl 等配置的提供商时在此扩展。 */
data class ProviderCredentials(val apiKey: String)
