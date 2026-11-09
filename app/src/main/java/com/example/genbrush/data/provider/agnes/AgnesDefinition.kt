package com.example.genbrush.data.provider.agnes

import com.example.genbrush.data.provider.ProviderDefinition
import com.example.genbrush.data.provider.SizeOption

/** Agnes AI（apihub.agnes-ai.com）的静态知识：元数据、预设模型、各模型系列的尺寸档位。 */
object AgnesDefinition {

    const val ID = "agnes"

    /** 预置生成模型（完整列表，供设置页勾选使用） */
    val PRESET_GENERATION_MODELS = listOf(
        "agnes-image-2.5-flash",
        "agnes-image-2.1-flash",
        "agnes-image-2.0-flash"
    )

    /** 预置编辑模型（三个模型均通过 extra_body.image 支持图生图） */
    val PRESET_EDIT_MODELS = listOf(
        "agnes-image-2.5-flash",
        "agnes-image-2.1-flash",
        "agnes-image-2.0-flash"
    )

    val definition = ProviderDefinition(
        id = ID,
        nameZh = "Agnes AI",
        nameEn = "Agnes AI",
        defaultModel = "agnes-image-2.5-flash",
        presetGenerationModels = PRESET_GENERATION_MODELS,
        presetEditModels = PRESET_EDIT_MODELS,
        supportsImageEdit = true,
        apiKeyHintZh = "请输入 Agnes AI 平台的 API Key（可在 agnes-ai.com 免费创建）。",
        apiKeyHintEn = "Enter your Agnes AI API key (create one for free at agnes-ai.com).",
        supportsModelListing = true
    )

    /** 从 GET /models 的全量列表里筛出图像生成模型。 */
    fun isImageModelId(id: String): Boolean = id.trim().lowercase().startsWith("agnes-image-")

    /**
     * 2.5 / 2.1 模型：官方推荐 size 用档位值（1K/2K/3K/4K）+ ratio 传参；
     * 应用内部仍按约定存 "宽*高"，发送前由 [apiSizeParams] 反查回档位+比例。
     * 以下像素值均取自官方输出尺寸表，属于原生支持的精确尺寸。
     */
    private val AGNES_IMAGE_2_5_SIZES = listOf(
        SizeOption("1024*1024", "1024x1024 (1K · 1:1)"),
        SizeOption("1312*736", "1312x736 (1K · 16:9)"),
        SizeOption("736*1312", "736x1312 (1K · 9:16)"),
        SizeOption("1152*864", "1152x864 (1K · 4:3)"),
        SizeOption("864*1152", "864x1152 (1K · 3:4)"),
        SizeOption("2048*2048", "2048x2048 (2K · 1:1)"),
        SizeOption("2624*1472", "2624x1472 (2K · 16:9)"),
        SizeOption("1472*2624", "1472x2624 (2K · 9:16)"),
        SizeOption("2304*1728", "2304x1728 (2K · 4:3)"),
        SizeOption("1728*2304", "1728x2304 (2K · 3:4)"),
        SizeOption("4096*4096", "4096x4096 (4K · 1:1)"),
        SizeOption("5248*2944", "5248x2944 (4K · 16:9)"),
        SizeOption("2944*5248", "2944x5248 (4K · 9:16)")
    )

    /** 2.1 与 2.5 的参数、尺寸表一致，但最高只到 2K */
    private val AGNES_IMAGE_2_1_SIZES = AGNES_IMAGE_2_5_SIZES.filter { !it.label.contains("4K") }

    /** 2.0 模型：无档位表，使用精确尺寸（官方示例值） */
    private val AGNES_IMAGE_2_0_SIZES = listOf(
        SizeOption("1024*1024", "1024x1024 (1:1)"),
        SizeOption("1024*768", "1024x768 (4:3)"),
        SizeOption("768*1024", "768x1024 (3:4)")
    )

    /** 档位尺寸 → (档位值, 比例)，用于把内部 "宽*高" 反查成官方推荐的传参方式 */
    private val TIER_RATIO_BY_SIZE: Map<String, Pair<String, String>> = mapOf(
        "1024*1024" to ("1K" to "1:1"),
        "1312*736" to ("1K" to "16:9"),
        "736*1312" to ("1K" to "9:16"),
        "1152*864" to ("1K" to "4:3"),
        "864*1152" to ("1K" to "3:4"),
        "2048*2048" to ("2K" to "1:1"),
        "2624*1472" to ("2K" to "16:9"),
        "1472*2624" to ("2K" to "9:16"),
        "2304*1728" to ("2K" to "4:3"),
        "1728*2304" to ("2K" to "3:4"),
        "4096*4096" to ("4K" to "1:1"),
        "5248*2944" to ("4K" to "16:9"),
        "2944*5248" to ("4K" to "9:16")
    )

    /** 根据模型名称返回该模型支持的尺寸列表，未知/自定义模型回退到 2.5 档位。 */
    fun supportedSizes(model: String?): List<SizeOption> = when {
        model != null && is25Model(model) -> AGNES_IMAGE_2_5_SIZES
        model != null && is21Model(model) -> AGNES_IMAGE_2_1_SIZES
        model != null && is20Model(model) -> AGNES_IMAGE_2_0_SIZES
        else -> AGNES_IMAGE_2_5_SIZES
    }

    fun is25Model(model: String): Boolean = model.startsWith("agnes-image-2.5")

    fun is21Model(model: String): Boolean = model.startsWith("agnes-image-2.1")

    fun is20Model(model: String): Boolean = model.startsWith("agnes-image-2.0")

    private fun isTierModel(model: String): Boolean = is25Model(model) || is21Model(model)

    /**
     * 把应用内部的 "宽*高" 转成 API 的 size（及 ratio）传参。
     * 2.1/2.5 优先用官方推荐的档位值+比例；表外尺寸回退为精确 WxH 写法（API 兼容）。
     * 2.0 及未知模型始终使用精确 WxH。
     */
    fun apiSizeParams(model: String, size: String): ApiSizeParams {
        val dims = size.split("*").map { it.filter(Char::isDigit) }
        val width = dims.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "1024"
        val height = dims.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: "1024"
        val exact = "${width}x$height"

        if (!isTierModel(model)) return ApiSizeParams(size = exact, ratio = null)

        val tierRatio = TIER_RATIO_BY_SIZE[size]
        return if (tierRatio != null) {
            ApiSizeParams(size = tierRatio.first, ratio = tierRatio.second)
        } else {
            ApiSizeParams(size = exact, ratio = null)
        }
    }

    data class ApiSizeParams(val size: String, val ratio: String?)
}
