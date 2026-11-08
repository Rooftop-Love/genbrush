package com.example.genbrush.data.local

/**
 * 偏好设置的键名、旧键回退与备份导入合并规则。
 *
 * 这里保持纯 Kotlin（不引用 Android / SharedPreferences / PreferencesManager），
 * 让「v1.1 全局键 → v1.2 按提供商命名空间」的迁移逻辑可以在 JVM 单测中直接覆盖。
 * [PreferencesManager] 只负责把 SharedPreferences 的读写接到这些纯函数上。
 */
object PreferenceKeys {

    const val MODEL_SEP = "|||"

    const val KEY_API_KEY = "api_key"
    const val KEY_DEFAULT_MODEL = "default_model"
    const val KEY_DEFAULT_SIZE = "default_size"
    const val KEY_LANGUAGE = "language"
    const val KEY_PROVIDER_ID = "provider_id"
    const val KEY_DISABLED_GEN_MODELS = "disabled_gen_models"
    const val KEY_DISABLED_EDIT_MODELS = "disabled_edit_models"
    const val KEY_CUSTOM_GEN_MODELS = "custom_gen_models"
    const val KEY_CUSTOM_EDIT_MODELS = "custom_edit_models"

    const val DEFAULT_PROVIDER_ID = "dashscope"

    fun apiKeyKey(providerId: String) = "api_key_$providerId"
    fun defaultModelKey(providerId: String) = "default_model_$providerId"
    fun defaultSizeKey(providerId: String) = "default_size_$providerId"
    fun disabledGenModelsKey(providerId: String) = "disabled_gen_models_$providerId"
    fun disabledEditModelsKey(providerId: String) = "disabled_edit_models_$providerId"
    fun customGenModelsKey(providerId: String) = "custom_gen_models_$providerId"
    fun customEditModelsKey(providerId: String) = "custom_edit_models_$providerId"

    private val SCOPED_PREFIXES = listOf(
        "api_key_",
        "default_model_",
        "default_size_",
        "disabled_gen_models_",
        "disabled_edit_models_",
        "custom_gen_models_",
        "custom_edit_models_"
    )

    /** 是否属于「按提供商命名空间存储」的键（导出/导入时需要单独处理）。 */
    fun isProviderScoped(key: String): Boolean = SCOPED_PREFIXES.any { key.startsWith(it) }

    fun parseModels(raw: String?): List<String> =
        if (raw.isNullOrBlank()) emptyList()
        else raw.split(MODEL_SEP).filter { it.isNotBlank() }

    fun joinModels(values: Collection<String>): String =
        values.filter { it.isNotBlank() }.joinToString(MODEL_SEP)

    /** 规范化备份里的模型列表：去掉空段与多余分隔符。 */
    fun normalizeModels(raw: String): String = joinModels(parseModels(raw))

    /**
     * 读取回退：优先按提供商命名空间的键；只有默认提供商才允许回退到旧版全局键。
     * [blankIsMissing] 用于模型名/尺寸这类「空白等同未配置」的设置。
     */
    fun resolveRead(
        scoped: String?,
        legacy: String?,
        providerId: String,
        defaultProviderId: String = DEFAULT_PROVIDER_ID,
        blankIsMissing: Boolean = false
    ): String? {
        val scopedValue = if (blankIsMissing) scoped?.takeIf { it.isNotBlank() } else scoped
        if (scopedValue != null) return scopedValue
        if (providerId != defaultProviderId) return null
        return if (blankIsMissing) legacy?.takeIf { it.isNotBlank() } else legacy
    }

    /**
     * 把导入的 settings.json 映射为「应写入 SharedPreferences 的最终键值」。
     *
     * - 旧格式只有顶层键：落到 activeProvider 的命名空间，保证 v1.1 备份可用；
     * - 新格式还带命名空间键：最后写入，覆盖顶层键派生出的值；
     * - 未知键一律忽略，避免把无关配置写进加密偏好。
     */
    fun resolveImported(
        settings: Map<String, String>,
        currentProviderId: String
    ): Map<String, String> {
        val active = settings[KEY_PROVIDER_ID]?.takeIf { it.isNotEmpty() } ?: currentProviderId
        val result = LinkedHashMap<String, String>()

        if (settings[KEY_PROVIDER_ID]?.isNotEmpty() == true) {
            result[KEY_PROVIDER_ID] = active
        }

        fun copyText(sourceKey: String, targetKey: String) {
            val value = settings[sourceKey] ?: return
            if (value.isNotEmpty()) result[targetKey] = value
        }

        fun copyModels(sourceKey: String, targetKey: String) {
            val value = settings[sourceKey] ?: return
            result[targetKey] = normalizeModels(value)
        }

        copyText(KEY_API_KEY, apiKeyKey(active))
        copyText(KEY_DEFAULT_MODEL, defaultModelKey(active))
        copyText(KEY_DEFAULT_SIZE, defaultSizeKey(active))
        copyText(KEY_LANGUAGE, KEY_LANGUAGE)
        copyModels(KEY_DISABLED_GEN_MODELS, disabledGenModelsKey(active))
        copyModels(KEY_DISABLED_EDIT_MODELS, disabledEditModelsKey(active))
        copyModels(KEY_CUSTOM_GEN_MODELS, customGenModelsKey(active))
        copyModels(KEY_CUSTOM_EDIT_MODELS, customEditModelsKey(active))

        settings.forEach { (key, value) ->
            if (isProviderScoped(key)) result[key] = value
        }
        return result
    }
}
