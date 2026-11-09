package com.example.genbrush.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
            val current = prefs.getString(PreferenceKeys.KEY_PROVIDER_ID, null)
            if (current != null) return current
            // 迁移旧版"backend_type"：sd_webui 源已移除，无论旧值是什么都落到默认提供商
            prefs.edit { putString(PreferenceKeys.KEY_PROVIDER_ID, DEFAULT_PROVIDER_ID) }
            return DEFAULT_PROVIDER_ID
        }
        set(value) {
            prefs.edit { putString(PreferenceKeys.KEY_PROVIDER_ID, value) }
            notifyConfigChanged()
        }

    /** 读取指定提供商的 API key；旧版本只存一个全局 key，对默认提供商做兼容回退 */
    fun providerApiKey(providerId: String): String =
        PreferenceKeys.resolveRead(
            scoped = prefs.getString(PreferenceKeys.apiKeyKey(providerId), null),
            legacy = prefs.getString(PreferenceKeys.KEY_API_KEY, null),
            providerId = providerId,
            defaultProviderId = DEFAULT_PROVIDER_ID
        ) ?: ""

    fun setProviderApiKey(providerId: String, value: String) {
        prefs.edit { putString(PreferenceKeys.apiKeyKey(providerId), value) }
    }

    /** 读取指定提供商的默认模型；未单独配置时，默认提供商兼容旧版全局键，其余回退到 fallback */
    fun defaultModel(providerId: String, fallback: String = DEFAULT_MODEL): String =
        PreferenceKeys.resolveRead(
            scoped = prefs.getString(PreferenceKeys.defaultModelKey(providerId), null),
            legacy = prefs.getString(PreferenceKeys.KEY_DEFAULT_MODEL, null),
            providerId = providerId,
            defaultProviderId = DEFAULT_PROVIDER_ID,
            blankIsMissing = true
        ) ?: fallback

    fun setDefaultModel(providerId: String, value: String) {
        prefs.edit { putString(PreferenceKeys.defaultModelKey(providerId), value) }
        notifyConfigChanged()
    }

    /** 读取指定提供商的默认尺寸 */
    fun defaultSize(providerId: String, fallback: String = DEFAULT_SIZE): String =
        PreferenceKeys.resolveRead(
            scoped = prefs.getString(PreferenceKeys.defaultSizeKey(providerId), null),
            legacy = prefs.getString(PreferenceKeys.KEY_DEFAULT_SIZE, null),
            providerId = providerId,
            defaultProviderId = DEFAULT_PROVIDER_ID,
            blankIsMissing = true
        ) ?: fallback

    fun setDefaultSize(providerId: String, value: String) {
        prefs.edit { putString(PreferenceKeys.defaultSizeKey(providerId), value) }
        notifyConfigChanged()
    }

    /** 指定提供商下被禁用的预置生成模型（默认全部启用，只存被禁用的） */
    fun disabledGenerationModels(providerId: String): Set<String> =
        readModelList(
            PreferenceKeys.disabledGenModelsKey(providerId),
            PreferenceKeys.KEY_DISABLED_GEN_MODELS,
            providerId
        ).toSet()

    fun setDisabledGenerationModels(providerId: String, value: Set<String>) {
        prefs.edit {
            putString(
                PreferenceKeys.disabledGenModelsKey(providerId),
                PreferenceKeys.joinModels(value)
            )
        }
        notifyConfigChanged()
    }

    /** 指定提供商下被禁用的预置编辑模型 */
    fun disabledEditModels(providerId: String): Set<String> =
        readModelList(
            PreferenceKeys.disabledEditModelsKey(providerId),
            PreferenceKeys.KEY_DISABLED_EDIT_MODELS,
            providerId
        ).toSet()

    fun setDisabledEditModels(providerId: String, value: Set<String>) {
        prefs.edit {
            putString(
                PreferenceKeys.disabledEditModelsKey(providerId),
                PreferenceKeys.joinModels(value)
            )
        }
        notifyConfigChanged()
    }

    /** 指定提供商下用户自定义的生成模型列表 */
    fun customGenerationModels(providerId: String): List<String> =
        readModelList(
            PreferenceKeys.customGenModelsKey(providerId),
            PreferenceKeys.KEY_CUSTOM_GEN_MODELS,
            providerId
        )

    fun setCustomGenerationModels(providerId: String, value: List<String>) {
        prefs.edit {
            putString(
                PreferenceKeys.customGenModelsKey(providerId),
                PreferenceKeys.joinModels(value)
            )
        }
        notifyConfigChanged()
    }

    /** 指定提供商下用户自定义的编辑模型列表 */
    fun customEditModels(providerId: String): List<String> =
        readModelList(
            PreferenceKeys.customEditModelsKey(providerId),
            PreferenceKeys.KEY_CUSTOM_EDIT_MODELS,
            providerId
        )

    fun setCustomEditModels(providerId: String, value: List<String>) {
        prefs.edit {
            putString(
                PreferenceKeys.customEditModelsKey(providerId),
                PreferenceKeys.joinModels(value)
            )
        }
        notifyConfigChanged()
    }

    /**
     * 读取指定提供商的模型清单缓存（模型列表的唯一数据源）。
     * 首次读取时用内置预设 + 旧版 disabled/custom 配置一次性播种；之后版本升级只补齐新增内置项，
     * 绝不重置用户已经调过的开关。读取路径上的内部写盘不触发 configVersion，避免信号回环。
     */
    fun modelCatalog(providerId: String, seed: ModelSeed): ModelCatalog {
        val stored = ModelCatalog.decode(prefs.getString(PreferenceKeys.catalogKey(providerId), null))
        if (stored.models.isEmpty()) {
            val initial = ModelCatalogRules.buildInitial(
                seed = seed,
                disabledGeneration = disabledGenerationModels(providerId),
                disabledEdit = disabledEditModels(providerId),
                customGeneration = customGenerationModels(providerId),
                customEdit = customEditModels(providerId)
            )
            writeModelCatalog(providerId, initial)
            return initial
        }
        val upgraded = ModelCatalogRules.ensureSeed(stored, seed)
        if (upgraded != stored) writeModelCatalog(providerId, upgraded)
        return upgraded
    }

    /** 写入模型清单并通知生成页刷新（设置页开关即时生效）。 */
    fun setModelCatalog(providerId: String, catalog: ModelCatalog) {
        writeModelCatalog(providerId, catalog)
        notifyConfigChanged()
    }

    private fun writeModelCatalog(providerId: String, catalog: ModelCatalog) {
        val disabledGen = catalog.models
            .filter { it.supportsGeneration && !it.enabledGeneration && it.source != ModelSource.CUSTOM }
            .map { it.id }
        val disabledEdit = catalog.models
            .filter { it.supportsEdit && !it.enabledEdit && it.source != ModelSource.CUSTOM }
            .map { it.id }
        val customGen = catalog.models
            .filter { it.source == ModelSource.CUSTOM && it.supportsGeneration }
            .map { it.id }
        val customEdit = catalog.models
            .filter { it.source == ModelSource.CUSTOM && it.supportsEdit }
            .map { it.id }
        prefs.edit {
            putString(PreferenceKeys.catalogKey(providerId), catalog.encode())
            // 同步旧键，保证 ZIP 备份里的顶层兼容键与降级到旧版本时的模型配置仍然正确。
            putString(PreferenceKeys.disabledGenModelsKey(providerId), PreferenceKeys.joinModels(disabledGen))
            putString(PreferenceKeys.disabledEditModelsKey(providerId), PreferenceKeys.joinModels(disabledEdit))
            putString(PreferenceKeys.customGenModelsKey(providerId), PreferenceKeys.joinModels(customGen))
            putString(PreferenceKeys.customEditModelsKey(providerId), PreferenceKeys.joinModels(customEdit))
        }
    }

    var defaultModel: String
        get() = defaultModel(providerId)
        set(value) = setDefaultModel(providerId, value)

    var defaultSize: String
        get() = defaultSize(providerId)
        set(value) = setDefaultSize(providerId, value)

    var language: String
        get() = prefs.getString(PreferenceKeys.KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_LANGUAGE, value) }

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

    private fun readModelList(scopedKey: String, legacyKey: String, providerId: String): List<String> {
        val raw = PreferenceKeys.resolveRead(
            scoped = prefs.getString(scopedKey, null),
            legacy = prefs.getString(legacyKey, null),
            providerId = providerId,
            defaultProviderId = DEFAULT_PROVIDER_ID
        ).orEmpty()
        return PreferenceKeys.parseModels(raw)
    }

    /** 一次性写入设置页的主要配置，只发一次 configVersion 信号，避免生成页被连续刷新多次。 */
    fun saveProviderSettings(
        providerId: String,
        apiKey: String,
        defaultModel: String,
        defaultSize: String,
        language: String
    ) {
        prefs.edit {
            putString(PreferenceKeys.KEY_PROVIDER_ID, providerId)
            putString(PreferenceKeys.apiKeyKey(providerId), apiKey)
            putString(PreferenceKeys.defaultModelKey(providerId), defaultModel)
            putString(PreferenceKeys.defaultSizeKey(providerId), defaultSize)
            putString(PreferenceKeys.KEY_LANGUAGE, language)
        }
        notifyConfigChanged()
    }

    /** 导出所有设置为 Map（用于数据迁移，兼顾旧版单提供商键名与新版多提供商命名空间键名） */
    fun exportAll(): Map<String, String> {
        val currentProvider = providerId
        val result = mutableMapOf(
            // 保留顶层键名携带当前提供商配置，保证旧版本 app 导入备份时可用
            PreferenceKeys.KEY_API_KEY to providerApiKey(currentProvider),
            PreferenceKeys.KEY_PROVIDER_ID to currentProvider,
            PreferenceKeys.KEY_DEFAULT_MODEL to defaultModel(currentProvider),
            PreferenceKeys.KEY_DEFAULT_SIZE to defaultSize(currentProvider),
            PreferenceKeys.KEY_LANGUAGE to language,
            PreferenceKeys.KEY_DISABLED_GEN_MODELS to
                PreferenceKeys.joinModels(disabledGenerationModels(currentProvider)),
            PreferenceKeys.KEY_DISABLED_EDIT_MODELS to
                PreferenceKeys.joinModels(disabledEditModels(currentProvider)),
            PreferenceKeys.KEY_CUSTOM_GEN_MODELS to
                PreferenceKeys.joinModels(customGenerationModels(currentProvider)),
            PreferenceKeys.KEY_CUSTOM_EDIT_MODELS to
                PreferenceKeys.joinModels(customEditModels(currentProvider))
        )
        // 同时导出所有已按提供商命名空间存储的配置项，完整保留多提供商设置
        prefs.all.forEach { (key, value) ->
            if (value is String && PreferenceKeys.isProviderScoped(key)) {
                result[key] = value
            }
        }
        return result
    }

    /** 从导入的 Map 恢复所有设置（兼容旧格式与按提供商命名空间的新格式） */
    fun applyImported(settings: Map<String, String>) {
        val resolved = PreferenceKeys.resolveImported(settings, providerId)
        if (resolved.isEmpty()) return
        val hasCatalog = PreferenceKeys.hasCatalogKeys(settings)
        val activeProviderId = settings[PreferenceKeys.KEY_PROVIDER_ID]?.takeIf { it.isNotEmpty() }
            ?: providerId
        prefs.edit {
            resolved.forEach { (key, value) -> putString(key, value) }
            // 旧版备份不含清单：清掉当前缓存，让下次读取按导入的 disabled/custom 键重新播种。
            if (!hasCatalog) remove(PreferenceKeys.catalogKey(activeProviderId))
        }
        notifyConfigChanged()
    }

    companion object {
        const val DEFAULT_PROVIDER_ID = PreferenceKeys.DEFAULT_PROVIDER_ID
        const val DEFAULT_MODEL = "qwen-image-2.0-pro"
        const val DEFAULT_SIZE = "2048*2048"
        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_ZH = "zh"
        const val LANGUAGE_EN = "en"
    }
}
