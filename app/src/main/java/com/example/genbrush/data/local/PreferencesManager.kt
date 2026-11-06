package com.example.genbrush.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "GenBrush_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _configVersion = MutableStateFlow(0L)

    /**
     * 配置变更信号：影响提供商/模型/尺寸列表的偏好被写入时自增。
     * 各界面 ViewModel 订阅它做联动刷新，避免依赖页面重建或生命周期时机。
     */
    val configVersion: StateFlow<Long> = _configVersion.asStateFlow()

    private fun notifyConfigChanged() {
        _configVersion.value += 1
    }

    /** 当前使用的图像生成提供商 id（ProviderRegistry 中注册的 definition.id） */
    var providerId: String
        get() {
            val current = prefs.getString(KEY_PROVIDER_ID, null)
            if (current != null) return current
            // 迁移旧版"backend_type"：sd_webui 源已移除，无论旧值是什么都落到默认提供商
            prefs.edit().putString(KEY_PROVIDER_ID, DEFAULT_PROVIDER_ID).apply()
            return DEFAULT_PROVIDER_ID
        }
        set(value) {
            prefs.edit().putString(KEY_PROVIDER_ID, value).apply()
            notifyConfigChanged()
        }

    /** 读取指定提供商的 API key；旧版本只存一个全局 key，对默认提供商做兼容回退 */
    fun providerApiKey(providerId: String): String =
        prefs.getString(providerApiKeyKey(providerId), null)
            ?: (if (providerId == DEFAULT_PROVIDER_ID) prefs.getString(KEY_API_KEY, "") else null)
            ?: ""

    fun setProviderApiKey(providerId: String, value: String) {
        prefs.edit().putString(providerApiKeyKey(providerId), value).apply()
    }

    /** 读取指定提供商的默认模型；未单独配置时，默认提供商兼容旧版全局键，其余回退到 fallback */
    fun defaultModel(providerId: String, fallback: String = DEFAULT_MODEL): String =
        prefs.getString(defaultModelKey(providerId), null)?.takeIf { it.isNotBlank() }
            ?: (if (providerId == DEFAULT_PROVIDER_ID) prefs.getString(KEY_DEFAULT_MODEL, null)?.takeIf { it.isNotBlank() } else null)
            ?: fallback

    fun setDefaultModel(providerId: String, value: String) {
        prefs.edit().putString(defaultModelKey(providerId), value).apply()
        notifyConfigChanged()
    }

    /** 读取指定提供商的默认尺寸 */
    fun defaultSize(providerId: String, fallback: String = DEFAULT_SIZE): String =
        prefs.getString(defaultSizeKey(providerId), null)?.takeIf { it.isNotBlank() }
            ?: (if (providerId == DEFAULT_PROVIDER_ID) prefs.getString(KEY_DEFAULT_SIZE, null)?.takeIf { it.isNotBlank() } else null)
            ?: fallback

    fun setDefaultSize(providerId: String, value: String) {
        prefs.edit().putString(defaultSizeKey(providerId), value).apply()
        notifyConfigChanged()
    }

    /** 指定提供商下被禁用的预置生成模型（默认全部启用，只存被禁用的） */
    fun disabledGenerationModels(providerId: String): Set<String> =
        readModelSet(disabledGenModelsKey(providerId), KEY_DISABLED_GEN_MODELS, providerId)

    fun setDisabledGenerationModels(providerId: String, value: Set<String>) {
        prefs.edit().putString(disabledGenModelsKey(providerId), value.joinToString(MODEL_SEP)).apply()
        notifyConfigChanged()
    }

    /** 指定提供商下被禁用的预置编辑模型 */
    fun disabledEditModels(providerId: String): Set<String> =
        readModelSet(disabledEditModelsKey(providerId), KEY_DISABLED_EDIT_MODELS, providerId)

    fun setDisabledEditModels(providerId: String, value: Set<String>) {
        prefs.edit().putString(disabledEditModelsKey(providerId), value.joinToString(MODEL_SEP)).apply()
        notifyConfigChanged()
    }

    /** 指定提供商下用户自定义的生成模型列表 */
    fun customGenerationModels(providerId: String): List<String> =
        readModelList(customGenModelsKey(providerId), KEY_CUSTOM_GEN_MODELS, providerId)

    fun setCustomGenerationModels(providerId: String, value: List<String>) {
        prefs.edit().putString(customGenModelsKey(providerId), value.joinToString(MODEL_SEP)).apply()
        notifyConfigChanged()
    }

    /** 指定提供商下用户自定义的编辑模型列表 */
    fun customEditModels(providerId: String): List<String> =
        readModelList(customEditModelsKey(providerId), KEY_CUSTOM_EDIT_MODELS, providerId)

    fun setCustomEditModels(providerId: String, value: List<String>) {
        prefs.edit().putString(customEditModelsKey(providerId), value.joinToString(MODEL_SEP)).apply()
        notifyConfigChanged()
    }

    var defaultModel: String
        get() = defaultModel(providerId)
        set(value) = setDefaultModel(providerId, value)

    var defaultSize: String
        get() = defaultSize(providerId)
        set(value) = setDefaultSize(providerId, value)

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var disabledGenerationModels: Set<String>
        get() = disabledGenerationModels(providerId)
        set(value) = setDisabledGenerationModels(providerId, value)

    var disabledEditModels: Set<String>
        get() = disabledEditModels(providerId)
        set(value) = setDisabledEditModels(providerId, value)

    var customGenerationModels: List<String>
        get() = customGenerationModels(providerId)
        set(value) = setCustomGenerationModels(providerId, value)

    var customEditModels: List<String>
        get() = customEditModels(providerId)
        set(value) = setCustomEditModels(providerId, value)

    private fun readModelSet(scopedKey: String, legacyKey: String, providerId: String): Set<String> =
        readModelList(scopedKey, legacyKey, providerId).toSet()

    private fun readModelList(scopedKey: String, legacyKey: String, providerId: String): List<String> {
        val raw = prefs.getString(scopedKey, null)
            ?: (if (providerId == DEFAULT_PROVIDER_ID) prefs.getString(legacyKey, "") else null)
            ?: ""
        return if (raw.isBlank()) emptyList()
        else raw.split(MODEL_SEP).filter { it.isNotBlank() }
    }

    /** 导出所有设置为 Map（用于数据迁移，兼顾旧版单提供商键名与新版多提供商命名空间键名） */
    fun exportAll(): Map<String, String> {
        val currentProvider = providerId
        val result = mutableMapOf(
            // 保留顶层键名携带当前提供商配置，保证旧版本 app 导入备份时可用
            "api_key" to providerApiKey(currentProvider),
            "provider_id" to currentProvider,
            "default_model" to defaultModel(currentProvider),
            "default_size" to defaultSize(currentProvider),
            "language" to language,
            "disabled_gen_models" to disabledGenerationModels(currentProvider).joinToString(MODEL_SEP),
            "disabled_edit_models" to disabledEditModels(currentProvider).joinToString(MODEL_SEP),
            "custom_gen_models" to customGenerationModels(currentProvider).joinToString(MODEL_SEP),
            "custom_edit_models" to customEditModels(currentProvider).joinToString(MODEL_SEP)
        )
        // 同时导出所有已按提供商命名空间存储的配置项，完整保留多提供商设置
        prefs.all.forEach { (key, value) ->
            if (value is String && PROVIDER_SCOPED_PREFIXES.any { key.startsWith(it) }) {
                result[key] = value
            }
        }
        return result
    }

    /** 从导入的 Map 恢复所有设置（兼容旧格式与按提供商命名空间的新格式） */
    fun applyImported(settings: Map<String, String>) {
        settings["provider_id"]?.let { if (it.isNotEmpty()) providerId = it }
        val activeProvider = providerId
        settings["api_key"]?.let { if (it.isNotEmpty()) setProviderApiKey(activeProvider, it) }
        settings["default_model"]?.let { if (it.isNotEmpty()) setDefaultModel(activeProvider, it) }
        settings["default_size"]?.let { if (it.isNotEmpty()) setDefaultSize(activeProvider, it) }
        settings["language"]?.let { if (it.isNotEmpty()) language = it }
        settings["disabled_gen_models"]?.let {
            setDisabledGenerationModels(
                activeProvider,
                if (it.isBlank()) emptySet() else it.split(MODEL_SEP).filter(String::isNotBlank).toSet()
            )
        }
        settings["disabled_edit_models"]?.let {
            setDisabledEditModels(
                activeProvider,
                if (it.isBlank()) emptySet() else it.split(MODEL_SEP).filter(String::isNotBlank).toSet()
            )
        }
        settings["custom_gen_models"]?.let {
            setCustomGenerationModels(
                activeProvider,
                if (it.isBlank()) emptyList() else it.split(MODEL_SEP).filter(String::isNotBlank)
            )
        }
        settings["custom_edit_models"]?.let {
            setCustomEditModels(
                activeProvider,
                if (it.isBlank()) emptyList() else it.split(MODEL_SEP).filter(String::isNotBlank)
            )
        }

        // 恢复按提供商命名空间的键（若备份中存在，覆盖对应提供商配置）
        val editor = prefs.edit()
        var hasScopedUpdate = false
        settings.forEach { (key, value) ->
            if (PROVIDER_SCOPED_PREFIXES.any { key.startsWith(it) }) {
                editor.putString(key, value)
                hasScopedUpdate = true
            }
        }
        if (hasScopedUpdate) {
            editor.apply()
            notifyConfigChanged()
        }
    }

    companion object {
        private fun providerApiKeyKey(providerId: String) = "api_key_$providerId"
        private fun defaultModelKey(providerId: String) = "default_model_$providerId"
        private fun defaultSizeKey(providerId: String) = "default_size_$providerId"
        private fun disabledGenModelsKey(providerId: String) = "disabled_gen_models_$providerId"
        private fun disabledEditModelsKey(providerId: String) = "disabled_edit_models_$providerId"
        private fun customGenModelsKey(providerId: String) = "custom_gen_models_$providerId"
        private fun customEditModelsKey(providerId: String) = "custom_edit_models_$providerId"

        private val PROVIDER_SCOPED_PREFIXES = listOf(
            "api_key_",
            "default_model_",
            "default_size_",
            "disabled_gen_models_",
            "disabled_edit_models_",
            "custom_gen_models_",
            "custom_edit_models_"
        )

        private const val KEY_API_KEY = "api_key" // 旧版全局键，仅作默认提供商的读取回退
        private const val KEY_DEFAULT_MODEL = "default_model"
        private const val KEY_DEFAULT_SIZE = "default_size"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_PROVIDER_ID = "provider_id"
        private const val KEY_DISABLED_GEN_MODELS = "disabled_gen_models"
        private const val KEY_DISABLED_EDIT_MODELS = "disabled_edit_models"
        private const val KEY_CUSTOM_GEN_MODELS = "custom_gen_models"
        private const val KEY_CUSTOM_EDIT_MODELS = "custom_edit_models"
        private const val MODEL_SEP = "|||"
        const val DEFAULT_PROVIDER_ID = "dashscope"
        const val DEFAULT_MODEL = "qwen-image-2.0-pro"
        const val DEFAULT_SIZE = "2048*2048"
        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_ZH = "zh"
        const val LANGUAGE_EN = "en"
    }
}
