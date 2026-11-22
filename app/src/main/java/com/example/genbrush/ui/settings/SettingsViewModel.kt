package com.example.genbrush.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.genbrush.data.local.ModelCapability
import com.example.genbrush.data.local.ModelCatalog
import com.example.genbrush.data.local.ModelCatalogRules
import com.example.genbrush.data.local.ModelSeed
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.provider.ProviderCredentials
import com.example.genbrush.data.provider.ProviderDefinition
import com.example.genbrush.data.provider.ProviderRegistry
import com.example.genbrush.data.provider.SizeOption
import com.example.genbrush.ui.common.mapError
import com.example.genbrush.ui.localization.resolveAppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val apiKey: String = "",
    val apiKeyDirty: Boolean = false,
    val isApiKeyVisible: Boolean = false,
    val defaultModel: String = PreferencesManager.DEFAULT_MODEL,
    val defaultSize: String = PreferencesManager.DEFAULT_SIZE,
    val language: String = PreferencesManager.LANGUAGE_ZH,
    val providerId: String = PreferencesManager.DEFAULT_PROVIDER_ID,
    val providers: List<ProviderDefinition> = emptyList(),
    val configuredProviderIds: Set<String> = emptySet(),
    val supportsImageEdit: Boolean = true,
    val supportsModelListing: Boolean = false,
    val saveSuccess: Boolean = false,

    // 模型清单缓存：设置页编辑，生成页只读；改动即时写入加密偏好
    val modelCatalog: ModelCatalog = ModelCatalog(),
    val customGenModelInput: String = "",
    val customEditModelInput: String = "",
    val modelManageError: String? = null,
    val isRefreshingModels: Boolean = false,
    val modelRefreshMessage: String? = null,
    val modelRefreshFailed: Boolean = false,

    // 数据迁移状态
    val isMigrating: Boolean = false,
    val migrationMessage: String? = null,
    val isExportingLogs: Boolean = false,

    // 默认尺寸可选项（跟随默认模型变化）
    val availableSizes: List<SizeOption> = emptyList()
)

class SettingsViewModel(
    private val prefs: PreferencesManager,
    private val registry: ProviderRegistry
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        load()
    }

    fun updateApiKey(value: String) {
        val savedKey = prefs.providerApiKey(_state.value.providerId).trim()
        _state.update {
            it.copy(
                apiKey = value,
                apiKeyDirty = value.trim() != savedKey,
                saveSuccess = false
            )
        }
    }

    fun toggleApiKeyVisibility() {
        _state.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
    }

    fun saveApiKey() {
        val current = _state.value
        val trimmed = current.apiKey.trim()
        prefs.setProviderApiKey(current.providerId, trimmed)
        _state.update {
            it.copy(
                apiKey = trimmed,
                apiKeyDirty = false,
                configuredProviderIds = computeConfiguredProviders(),
                saveSuccess = true
            )
        }
    }

    fun consumeSaveSuccess() {
        _state.update { it.copy(saveSuccess = false) }
    }

    fun selectModel(model: String) {
        val providerId = _state.value.providerId
        val sizes = registry.resolve(providerId).supportedSizes(model)
        val safeSize = ensureSupportedSize(_state.value.defaultSize, sizes)
        prefs.setDefaultModel(providerId, model)
        if (safeSize != _state.value.defaultSize) {
            prefs.setDefaultSize(providerId, safeSize)
        }
        _state.update {
            it.copy(
                defaultModel = model,
                availableSizes = sizes,
                defaultSize = safeSize
            )
        }
    }

    fun selectSize(size: String) {
        val providerId = _state.value.providerId
        prefs.setDefaultSize(providerId, size)
        _state.update { it.copy(defaultSize = size) }
    }

    fun selectLanguage(lang: String) {
        prefs.language = lang
        _state.update { it.copy(language = lang) }
    }

    fun selectProvider(providerId: String) {
        if (providerId == _state.value.providerId) return
        prefs.providerId = providerId
        applyProviderState(providerId)
    }

    /** 导入备份后重新加载（提供商、模型清单与外观偏好可能被备份覆盖）。 */
    fun reload() {
        applyProviderState(prefs.providerId)
    }

    fun readImportedAppearance(): Triple<String, String, Boolean> =
        Triple(prefs.language, prefs.themeMode, prefs.dynamicColor)

    fun updateCustomGenModelInput(value: String) {
        _state.update { it.copy(customGenModelInput = value, modelManageError = null) }
    }

    fun updateCustomEditModelInput(value: String) {
        _state.update { it.copy(customEditModelInput = value, modelManageError = null) }
    }

    fun toggleGenerationModel(model: String) {
        val catalog = _state.value.modelCatalog
        val entry = catalog.models.firstOrNull { it.id == model } ?: return
        applyCatalogChange(
            ModelCatalogRules.setEnabled(
                catalog, model, ModelCapability.GENERATION,
                !entry.isEnabled(ModelCapability.GENERATION)
            )
        )
    }

    fun toggleEditModel(model: String) {
        val catalog = _state.value.modelCatalog
        val entry = catalog.models.firstOrNull { it.id == model } ?: return
        applyCatalogChange(
            ModelCatalogRules.setEnabled(
                catalog, model, ModelCapability.EDIT,
                !entry.isEnabled(ModelCapability.EDIT)
            )
        )
    }

    fun addCustomGenerationModel() {
        addCustomModel(_state.value.customGenModelInput, ModelCapability.GENERATION) {
            copy(customGenModelInput = "")
        }
    }

    fun addCustomEditModel() {
        addCustomModel(_state.value.customEditModelInput, ModelCapability.EDIT) {
            copy(customEditModelInput = "")
        }
    }

    fun removeCustomGenerationModel(model: String) {
        applyCatalogChange(
            ModelCatalogRules.removeCustom(_state.value.modelCatalog, model, ModelCapability.GENERATION)
        )
    }

    fun removeCustomEditModel(model: String) {
        applyCatalogChange(
            ModelCatalogRules.removeCustom(_state.value.modelCatalog, model, ModelCapability.EDIT)
        )
    }

    /** 手动从上游拉取模型列表：只新增条目，不覆盖已有开关；失败时缓存保持原样。 */
    fun refreshRemoteModels() {
        val state = _state.value
        if (state.isRefreshingModels) return
        val provider = registry.resolve(state.providerId)
        if (!provider.definition.supportsModelListing) return
        val s = resolveAppStrings(prefs.language)
        val apiKey = state.apiKey.trim().ifBlank { prefs.providerApiKey(state.providerId) }
        if (apiKey.isBlank()) {
            _state.update { it.copy(modelRefreshMessage = s.errApiKey, modelRefreshFailed = true) }
            return
        }
        _state.update {
            it.copy(isRefreshingModels = true, modelRefreshMessage = null, modelRefreshFailed = false)
        }
        viewModelScope.launch {
            val result = try {
                provider.fetchModels(ProviderCredentials(apiKey))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            // 刷新期间若用户切换了提供商，丢弃这次结果，避免写进另一个提供商的清单。
            if (_state.value.providerId != state.providerId) return@launch
            result.fold(
                onSuccess = { ids ->
                    if (ids.isEmpty()) {
                        _state.update {
                            it.copy(
                                isRefreshingModels = false,
                                modelRefreshMessage = s.modelRefreshEmpty,
                                modelRefreshFailed = false
                            )
                        }
                        return@fold
                    }
                    val merged = ModelCatalogRules.mergeRemote(
                        _state.value.modelCatalog,
                        ids,
                        provider.definition.supportsImageEdit,
                        System.currentTimeMillis()
                    )
                    prefs.setModelCatalog(state.providerId, merged.catalog)
                    _state.update {
                        it.copy(
                            modelCatalog = merged.catalog,
                            isRefreshingModels = false,
                            modelRefreshMessage = if (merged.added > 0) {
                                s.modelRefreshSuccess.format(merged.added)
                            } else {
                                s.modelRefreshNoNew
                            },
                            modelRefreshFailed = false
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isRefreshingModels = false,
                            modelRefreshMessage = s.modelRefreshFailed.format(mapError(error, s)),
                            modelRefreshFailed = true
                        )
                    }
                }
            )
        }
    }

    fun dismissModelManageError() {
        _state.update { it.copy(modelManageError = null) }
    }

    fun dismissModelRefreshMessage() {
        _state.update { it.copy(modelRefreshMessage = null, modelRefreshFailed = false) }
    }

    fun setMigrationMessage(msg: String?) {
        _state.update { it.copy(migrationMessage = msg) }
    }

    fun setMigrating(migrating: Boolean) {
        _state.update { it.copy(isMigrating = migrating) }
    }

    fun setExportingLogs(exporting: Boolean) {
        _state.update { it.copy(isExportingLogs = exporting) }
    }

    private fun load() {
        applyProviderState(prefs.providerId)
    }

    private fun computeConfiguredProviders(): Set<String> =
        registry.all()
            .map { it.definition.id }
            .filter { prefs.providerApiKey(it).isNotBlank() }
            .toSet()

    private fun addCustomModel(
        input: String,
        capability: ModelCapability,
        clearInput: SettingsState.() -> SettingsState
    ) {
        val s = resolveAppStrings(prefs.language)
        val result = ModelCatalogRules.addCustom(_state.value.modelCatalog, input, capability)
        if (!result.added) {
            _state.update {
                it.copy(
                    modelManageError = if (input.isBlank()) s.settingsModelEmpty else s.settingsModelExists
                )
            }
            return
        }
        applyCatalogChange(result.catalog)
        _state.update { clearInput(it.copy(modelManageError = null)) }
    }

    /**
     * 把清单变更立即写盘（configVersion 自增使生成页联动刷新），
     * 并在默认生成模型被关闭时回退到首个仍可用的模型。
     */
    private fun applyCatalogChange(catalog: ModelCatalog) {
        val providerId = _state.value.providerId
        prefs.setModelCatalog(providerId, catalog)
        _state.update { state ->
            val visible = ModelCatalogRules.visibleIds(catalog, ModelCapability.GENERATION)
            if (visible.isEmpty() || state.defaultModel in visible) {
                state.copy(modelCatalog = catalog)
            } else {
                val fallback = visible.first()
                val sizes = registry.resolve(providerId).supportedSizes(fallback)
                val safeSize = ensureSupportedSize(state.defaultSize, sizes)
                prefs.setDefaultModel(providerId, fallback)
                if (safeSize != state.defaultSize) {
                    prefs.setDefaultSize(providerId, safeSize)
                }
                state.copy(
                    modelCatalog = catalog,
                    defaultModel = fallback,
                    availableSizes = sizes,
                    defaultSize = safeSize
                )
            }
        }
    }

    /** 按指定 providerId 加载该提供商专属的密钥、模型清单与默认模型/尺寸。 */
    private fun applyProviderState(requestedProviderId: String) {
        val provider = registry.resolve(requestedProviderId)
        val definition = provider.definition
        val resolvedId = definition.id
        val catalog = prefs.modelCatalog(resolvedId, seedOf(definition))
        val configuredGenModels = ModelCatalogRules.visibleIds(catalog, ModelCapability.GENERATION)
        val savedDefaultModel = prefs.defaultModel(resolvedId, definition.defaultModel)
        val effectiveDefaultModel = when {
            savedDefaultModel in configuredGenModels -> savedDefaultModel
            definition.defaultModel in configuredGenModels -> definition.defaultModel
            else -> configuredGenModels.firstOrNull() ?: definition.defaultModel
        }
        val sizes = provider.supportedSizes(effectiveDefaultModel)
        val safeSize = ensureSupportedSize(prefs.defaultSize(resolvedId), sizes)

        _state.update {
            it.copy(
                apiKey = prefs.providerApiKey(resolvedId),
                apiKeyDirty = false,
                defaultModel = effectiveDefaultModel,
                defaultSize = safeSize,
                language = prefs.language,
                providerId = resolvedId,
                providers = registry.all().map { p -> p.definition },
                configuredProviderIds = computeConfiguredProviders(),
                supportsImageEdit = definition.supportsImageEdit,
                supportsModelListing = definition.supportsModelListing,
                modelCatalog = catalog,
                modelManageError = null,
                modelRefreshMessage = null,
                modelRefreshFailed = false,
                availableSizes = sizes
            )
        }
    }

    private fun seedOf(definition: ProviderDefinition) = ModelSeed(
        generationModels = definition.presetGenerationModels,
        editModels = definition.presetEditModels,
        supportsImageEdit = definition.supportsImageEdit
    )

    /** 确保当前选中尺寸在可用列表中；若不在则回退到列表首个选项。 */
    private fun ensureSupportedSize(currentSize: String, sizes: List<SizeOption>): String {
        if (sizes.isEmpty()) return currentSize
        return if (sizes.any { it.value == currentSize }) currentSize else sizes.first().value
    }

    companion object {
        fun factory(prefs: PreferencesManager, registry: ProviderRegistry) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(prefs, registry) as T
                }
            }
    }
}
