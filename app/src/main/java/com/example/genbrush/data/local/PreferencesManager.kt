package com.example.genbrush.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    /** 当前使用的图像生成提供商 id（ProviderRegistry 中注册的 definition.id） */
    var providerId: String
        get() {
            val current = prefs.getString(KEY_PROVIDER_ID, null)
            if (current != null) return current
            // 迁移旧版"backend_type"：sd_webui 源已移除，无论旧值是什么都落到当前唯一提供商
            prefs.edit().putString(KEY_PROVIDER_ID, DEFAULT_PROVIDER_ID).apply()
            return DEFAULT_PROVIDER_ID
        }
        set(value) = prefs.edit().putString(KEY_PROVIDER_ID, value).apply()

    /** 读取指定提供商的 API key；旧版本只存一个全局 key，做兼容回退 */
    fun providerApiKey(providerId: String): String =
        prefs.getString(providerApiKeyKey(providerId), null)
            ?: prefs.getString(KEY_API_KEY, "")
            ?: ""

    fun setProviderApiKey(providerId: String, value: String) {
        prefs.edit().putString(providerApiKeyKey(providerId), value).apply()
    }

    var defaultModel: String
        get() = prefs.getString(KEY_DEFAULT_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_DEFAULT_MODEL, value).apply()

    var defaultSize: String
        get() = prefs.getString(KEY_DEFAULT_SIZE, DEFAULT_SIZE) ?: DEFAULT_SIZE
        set(value) = prefs.edit().putString(KEY_DEFAULT_SIZE, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** 被禁用的预置生成模型（默认全部启用，只存被禁用的） */
    var disabledGenerationModels: Set<String>
        get() {
            val raw = prefs.getString(KEY_DISABLED_GEN_MODELS, "") ?: ""
            return if (raw.isBlank()) emptySet()
            else raw.split(MODEL_SEP).filter { it.isNotBlank() }.toSet()
        }
        set(value) = prefs.edit().putString(KEY_DISABLED_GEN_MODELS, value.joinToString(MODEL_SEP)).apply()

    /** 被禁用的预置编辑模型 */
    var disabledEditModels: Set<String>
        get() {
            val raw = prefs.getString(KEY_DISABLED_EDIT_MODELS, "") ?: ""
            return if (raw.isBlank()) emptySet()
            else raw.split(MODEL_SEP).filter { it.isNotBlank() }.toSet()
        }
        set(value) = prefs.edit().putString(KEY_DISABLED_EDIT_MODELS, value.joinToString(MODEL_SEP)).apply()

    /** 用户自定义的生成模型列表 */
    var customGenerationModels: List<String>
        get() {
            val raw = prefs.getString(KEY_CUSTOM_GEN_MODELS, "") ?: ""
            return if (raw.isBlank()) emptyList()
            else raw.split(MODEL_SEP).filter { it.isNotBlank() }
        }
        set(value) = prefs.edit().putString(KEY_CUSTOM_GEN_MODELS, value.joinToString(MODEL_SEP)).apply()

    /** 用户自定义的编辑模型列表 */
    var customEditModels: List<String>
        get() {
            val raw = prefs.getString(KEY_CUSTOM_EDIT_MODELS, "") ?: ""
            return if (raw.isBlank()) emptyList()
            else raw.split(MODEL_SEP).filter { it.isNotBlank() }
        }
        set(value) = prefs.edit().putString(KEY_CUSTOM_EDIT_MODELS, value.joinToString(MODEL_SEP)).apply()

    /** 导出所有设置为 Map（用于数据迁移） */
    fun exportAll(): Map<String, String> {
        val currentProvider = providerId
        return mapOf(
            // 继续用 "api_key" 键名携带当前提供商的 key，保证旧版本 app 导入备份时可用
            "api_key" to providerApiKey(currentProvider),
            "provider_id" to currentProvider,
            "default_model" to defaultModel,
            "default_size" to defaultSize,
            "language" to language,
            "disabled_gen_models" to (disabledGenerationModels.joinToString(MODEL_SEP)),
            "disabled_edit_models" to (disabledEditModels.joinToString(MODEL_SEP)),
            "custom_gen_models" to (customGenerationModels.joinToString(MODEL_SEP)),
            "custom_edit_models" to (customEditModels.joinToString(MODEL_SEP))
        )
    }

    /** 从导入的 Map 恢复所有设置（兼容旧格式：backend/sd_server_url 已废弃，忽略） */
    fun applyImported(settings: Map<String, String>) {
        settings["provider_id"]?.let { if (it.isNotEmpty()) providerId = it }
        settings["api_key"]?.let { if (it.isNotEmpty()) setProviderApiKey(providerId, it) }
        settings["default_model"]?.let { if (it.isNotEmpty()) defaultModel = it }
        settings["default_size"]?.let { if (it.isNotEmpty()) defaultSize = it }
        settings["language"]?.let { if (it.isNotEmpty()) language = it }
        settings["disabled_gen_models"]?.let {
            disabledGenerationModels = if (it.isBlank()) emptySet()
            else it.split(MODEL_SEP).filter { s -> s.isNotBlank() }.toSet()
        }
        settings["disabled_edit_models"]?.let {
            disabledEditModels = if (it.isBlank()) emptySet()
            else it.split(MODEL_SEP).filter { s -> s.isNotBlank() }.toSet()
        }
        settings["custom_gen_models"]?.let {
            customGenerationModels = if (it.isBlank()) emptyList()
            else it.split(MODEL_SEP).filter { s -> s.isNotBlank() }
        }
        settings["custom_edit_models"]?.let {
            customEditModels = if (it.isBlank()) emptyList()
            else it.split(MODEL_SEP).filter { s -> s.isNotBlank() }
        }
    }

    companion object {
        private fun providerApiKeyKey(providerId: String) = "api_key_$providerId"

        private const val KEY_API_KEY = "api_key" // 旧版全局 API key，仅作读取回退
        private const val KEY_DEFAULT_MODEL = "default_model"
        private const val KEY_DEFAULT_SIZE = "default_size"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_PROVIDER_ID = "provider_id"
        private const val KEY_BACKEND = "backend_type" // 旧版后端标识，已废弃；首次读取 provider_id 时被覆盖
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
