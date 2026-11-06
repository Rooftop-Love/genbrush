package com.example.genbrush.data.provider

import java.util.Locale

/** 尺寸档位：value 为 "宽*高"，label 为带比例的展示文案 */
data class SizeOption(val value: String, val label: String)

/**
 * 提供商元数据：显示名、预设模型、能力声明。纯数据，不含网络逻辑。
 * 显示名沿用项目"代码内本地化"的模式（见 ui/localization/AppStrings）。
 */
data class ProviderDefinition(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val defaultModel: String,
    /** 预置生成模型（完整列表，供设置页勾选启用/禁用） */
    val presetGenerationModels: List<String>,
    /** 预置编辑模型 */
    val presetEditModels: List<String>,
    /** 是否支持图生图（不支持时 UI 可隐藏/禁用编辑入口） */
    val supportsImageEdit: Boolean = true,
    /** 设置页 API Key 输入框下方的提示文案（中/英） */
    val apiKeyHintZh: String = "",
    val apiKeyHintEn: String = ""
) {
    fun displayName(language: String): String = localize(language, nameZh, nameEn)

    fun apiKeyHint(language: String): String = localize(language, apiKeyHintZh, apiKeyHintEn)

    private fun localize(language: String, zh: String, en: String): String = when {
        language == LANGUAGE_ZH -> zh
        language == LANGUAGE_EN -> en
        else -> if (Locale.getDefault().language == "zh") zh else en
    }

    companion object {
        const val LANGUAGE_ZH = "zh"
        const val LANGUAGE_EN = "en"
    }
}
