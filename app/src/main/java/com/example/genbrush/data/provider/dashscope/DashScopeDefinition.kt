package com.example.genbrush.data.provider.dashscope

import com.example.genbrush.data.provider.ProviderDefinition
import com.example.genbrush.data.provider.SizeOption

/** DashScope（阿里云百炼）的静态知识：元数据、预设模型、各模型系列的尺寸档位。 */
object DashScopeDefinition {

    const val ID = "dashscope"

    /** 预置生成模型（完整列表，供设置页勾选使用） */
    val PRESET_GENERATION_MODELS = listOf(
        "qwen-image-3.0-pro",
        "qwen-image-2.0-pro-2026-06-22",
        "qwen-image-2.0-pro-2026-04-22",
        "qwen-image-2.0-pro",
        "qwen-image-2.0",
        "qwen-image-max",
        "qwen-image-plus",
        "wan2.7-image-pro",
        "wan2.7-image"
    )

    /** 预置编辑模型（完整列表，供设置页勾选使用） */
    val PRESET_EDIT_MODELS = listOf(
        "qwen-image-3.0-pro",
        "qwen-image-2.0-pro-2026-06-22",
        "qwen-image-2.0-pro-2026-04-22",
        "qwen-image-edit-max",
        "qwen-image-edit-plus",
        "qwen-image-2.0-pro",
        "qwen-image-2.0",
        "wan2.7-image-pro",
        "wan2.7-image"
    )

    val definition = ProviderDefinition(
        id = ID,
        nameZh = "阿里云百炼 (DashScope)",
        nameEn = "DashScope (Alibaba Cloud)",
        defaultModel = "qwen-image-2.0-pro",
        presetGenerationModels = PRESET_GENERATION_MODELS,
        presetEditModels = PRESET_EDIT_MODELS,
        supportsImageEdit = true,
        apiKeyHintZh = "请输入阿里云百炼平台的 DashScope API Key。",
        apiKeyHintEn = "Enter your DashScope API key from Alibaba Cloud console.",
        supportsModelListing = true
    )

    /** 从 GET /models 的全量列表里筛出图像生成模型（平台同时返回文本、语音、视频模型）。 */
    fun isImageModelId(id: String): Boolean {
        val normalized = id.trim().lowercase()
        return normalized.startsWith("qwen-image") ||
            (normalized.startsWith("wan") && normalized.contains("image"))
    }

    /**
     * qwen-image-2.0 系列（qwen-image-2.0-pro / qwen-image-2.0）
     * 总像素需在 512*512 ~ 2048*2048 之间，以下为官方推荐分辨率
     */
    private val QWEN_IMAGE_2_0_SIZES = listOf(
        SizeOption("2048*2048", "2048x2048 (1:1)"),
        SizeOption("2688*1536", "2688x1536 (16:9)"),
        SizeOption("1536*2688", "1536x2688 (9:16)"),
        SizeOption("2368*1728", "2368x1728 (4:3)"),
        SizeOption("1728*2368", "1728x2368 (3:4)")
    )

    /**
     * qwen-image-3.0-pro supports arbitrary sizes within the documented pixel-area
     * and aspect-ratio limits. These common presets also satisfy those limits.
     */
    private val QWEN_IMAGE_3_0_SIZES = listOf(
        SizeOption("2048*2048", "2048x2048 (1:1)"),
        SizeOption("2688*1536", "2688x1536 (16:9)"),
        SizeOption("1536*2688", "1536x2688 (9:16)"),
        SizeOption("2368*1728", "2368x1728 (4:3)"),
        SizeOption("1728*2368", "1728x2368 (3:4)")
    )

    /**
     * qwen-image-max / qwen-image-plus 系列（含 edit 变体）
     * 仅支持以下固定档位
     */
    private val QWEN_IMAGE_MAX_SIZES = listOf(
        SizeOption("1328*1328", "1328x1328 (1:1)"),
        SizeOption("1664*928", "1664x928 (16:9)"),
        SizeOption("928*1664", "928x1664 (9:16)"),
        SizeOption("1472*1104", "1472x1104 (4:3)"),
        SizeOption("1104*1472", "1104x1472 (3:4)")
    )

    /**
     * wan2.7-image 系列
     * 文生图支持 4K，图像编辑/组图最高 2K；以下为官方常见比例推荐分辨率
     */
    private val WAN27_IMAGE_SIZES = listOf(
        SizeOption("1280*1280", "1280x1280 (1:1)"),
        SizeOption("1280*720", "1280x720 (16:9)"),
        SizeOption("720*1280", "720x1280 (9:16)"),
        SizeOption("1280*960", "1280x960 (4:3)"),
        SizeOption("960*1280", "960x1280 (3:4)")
    )

    /**
     * 根据模型名称判断所属系列，返回该模型支持的尺寸列表。
     * 未知/自定义模型回退到 qwen-image-2.0 系列档位。
     */
    fun supportedSizes(model: String?): List<SizeOption> {
        if (model.isNullOrBlank()) return QWEN_IMAGE_2_0_SIZES
        return when {
            isQwenImage3Model(model) -> QWEN_IMAGE_3_0_SIZES
            isQwenImage2Model(model) -> QWEN_IMAGE_2_0_SIZES
            isQwenImageMaxOrPlusModel(model) -> QWEN_IMAGE_MAX_SIZES
            isWanModel(model) -> WAN27_IMAGE_SIZES
            else -> QWEN_IMAGE_2_0_SIZES
        }
    }

    /** wan 系列走异步任务接口，qwen-image 系列走同步接口 */
    fun isWanModel(model: String): Boolean = model.startsWith("wan")

    /** qwen-image-3.0 需要额外的 prompt_extend_mode 参数 */
    fun isQwenImage3Model(model: String): Boolean =
        model == "qwen-image-3.0-pro" || model == "qwen-image-3.0"

    private fun isQwenImage2Model(model: String): Boolean =
        model.startsWith("qwen-image-2") || model.startsWith("qwen-image-edit-2")

    private fun isQwenImageMaxOrPlusModel(model: String): Boolean =
        model.startsWith("qwen-image-max") ||
            model.startsWith("qwen-image-edit-max") ||
            model.startsWith("qwen-image-plus") ||
            model.startsWith("qwen-image-edit-plus") ||
            model == "qwen-image" ||
            model.startsWith("qwen-image-edit@")
}
