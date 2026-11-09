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

// 可折叠面板的标识
const val SECTION_API_CONFIG = "api_config"
const val SECTION_DEFAULTS = "defaults"
const val SECTION_LANGUAGE = "language"
const val SECTION_ABOUT = "about"
const val SECTION_MIGRATION = "migration"

private val ALL_SECTIONS = setOf(
    SECTION_API_CONFIG,
    SECTION_DEFAULTS, SECTION_LANGUAGE, SECTION_MIGRATION, SECTION_ABOUT
)

data class SettingsState(
    val apiKey: String = "",
    val isApiKeyVisible: Boolean = false,
    val defaultModel: String = PreferencesManager.DEFAULT_MODEL,
    val defaultSize: String = PreferencesManager.DEFAULT_SIZE,
    val language: String = PreferencesManager.LANGUAGE_ZH,
    val providerId: String = PreferencesManager.DEFAULT_PROVIDER_ID,
    // 提供商选择器数据（来自 ProviderRegistry）
    val providers: List<ProviderDefinition> = emptyList(),
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

    // 模型管理子页面
    val showModelManagement: Boolean = false,

    // 折叠状态（默认全部展开）
    val expandedSections: Set<String> = ALL_SECTIONS,

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
        _state.update { it.copy(apiKey = value, saveSuccess = false) }
    }

    fun toggleApiKeyVisibility() {
        _state.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
    }

    fun toggleSection(section: String) {
        _state.update { state ->
            val expanded = state.expandedSections.toMutableSet()
            if (section in expanded) expanded.remove(section) else expanded.add(section)
            state.copy(expandedSections = expanded)
        }
    }

    fun isSectionExpanded(section: String): Boolean =
        _state.value.expandedSections.contains(section)

    fun selectModel(model: String) {
        val sizes = registry.resolve(_state.value.providerId).supportedSizes(model)
        val safeSize = ensureSupportedSize(_state.value.defaultSize, sizes)
        _state.update { it.copy(defaultModel = model, availableSizes = sizes, defaultSize = safeSize, saveSuccess = false) }
    }

    fun selectSize(size: String) {
        _state.update { it.copy(defaultSize = size, saveSuccess = false) }
    }

    fun selectLanguage(lang: String) {
        _state.update { it.copy(language = lang) }
    }

    fun selectProvider(providerId: String) {
        if (providerId == _state.value.providerId) return
        applyProviderState(providerId, markUnsaved = true)
    }

    /** 导入备份后重新加载（模型清单可能被备份覆盖）。 */
    fun reload() {
        applyProviderState(_state.value.providerId, markUnsaved = false)
    }

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

    fun openModelManagement() {
        _state.update { it.copy(showModelManagement = true) }
    }

    fun closeModelManagement() {
        _state.update { it.copy(showModelManagement = false) }
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

    fun save() {
        val state = _state.value
        prefs.saveProviderSettings(
            providerId = state.providerId,
            apiKey = state.apiKey,
            defaultModel = state.defaultModel,
            defaultSize = state.defaultSize,
            language = state.language
        )
        _state.update { it.copy(saveSuccess = true) }
    }

    private fun load() {
        applyProviderState(prefs.providerId, markUnsaved = false)
    }

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
        prefs.setModelCatalog(_state.value.providerId, catalog)
        _state.update { state ->
            val visible = ModelCatalogRules.visibleIds(catalog, ModelCapability.GENERATION)
            if (visible.isEmpty() || state.defaultModel in visible) {
                state.copy(modelCatalog = catalog, saveSuccess = false)
            } else {
                val fallback = visible.first()
                val sizes = registry.resolve(state.providerId).supportedSizes(fallback)
                state.copy(
                    modelCatalog = catalog,
                    defaultModel = fallback,
                    availableSizes = sizes,
                    defaultSize = ensureSupportedSize(state.defaultSize, sizes),
                    saveSuccess = false
                )
            }
        }
    }

    /** 按指定 providerId 加载该提供商专属的密钥、模型清单与默认模型/尺寸。 */
    private fun applyProviderState(requestedProviderId: String, markUnsaved: Boolean) {
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
                defaultModel = effectiveDefaultModel,
                defaultSize = safeSize,
                language = prefs.language,
                providerId = resolvedId,
                providers = registry.all().map { p -> p.definition },
                supportsImageEdit = definition.supportsImageEdit,
                supportsModelListing = definition.supportsModelListing,
                modelCatalog = catalog,
                modelManageError = null,
                modelRefreshMessage = null,
                modelRefreshFailed = false,
                availableSizes = sizes,
                saveSuccess = if (markUnsaved) false else it.saveSuccess
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
