package com.example.genbrush.data.provider.agnes

// --- 图像生成请求（POST images/generations，OpenAI 兼容格式） ---
// 注意：response_format 必须放在 extra_body 内（放在顶层会被官方文档明确标记为不生效）。
data class GenerationRequest(
    val model: String,
    val prompt: String,
    /** 档位值（1K/2K/4K）或精确尺寸（1024x768）；两者 API 均接受 */
    val size: String? = null,
    /** 宽高比；仅与档位值搭配使用（2.1/2.5 模型），精确尺寸时不传 */
    val ratio: String? = null,
    /** 扩展参数：图生图的输入图像数组（URL 或 Data URI Base64）、输出格式 */
    val extra_body: ExtraBody? = null
) {
    companion object {
        const val RESPONSE_FORMAT_URL = "url"
        const val RESPONSE_FORMAT_B64_JSON = "b64_json"
    }
}

data class ExtraBody(
    /** 图生图输入图像：公共 HTTPS URL 或 Data URI Base64；多图传入多个元素 */
    val image: List<String>? = null,
    val response_format: String? = null
)

// --- 生成响应（OpenAI images 兼容格式） ---
data class ImageApiResponse(
    val created: Long? = null,
    val data: List<ImageItem>? = null,
    /** 错误响应体（OpenAI 兼容格式：{"error": {...}}） */
    val error: ApiError? = null
)

data class ImageItem(
    val url: String? = null,
    val b64_json: String? = null,
    val revised_prompt: String? = null
)

data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
