package com.example.genbrush.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.provider.ProviderDefinition
import com.example.genbrush.data.provider.ProviderRegistry
import com.example.genbrush.data.provider.SizeOption
import com.example.genbrush.ui.components.buildAvailableModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val saveSuccess: Boolean = false,

    // 模型管理（预置列表来自当前提供商 definition）
    val presetGenerationModels: List<String> = emptyList(),
    val presetEditModels: List<String> = emptyList(),
    val disabledGenerationModels: Set<String> = emptySet(),
    val disabledEditModels: Set<String> = emptySet(),
    val customGenerationModels: List<String> = emptyList(),
    val customEditModels: List<String> = emptyList(),
    val customGenModelInput: String = "",
    val customEditModelInput: String = "",
    val modelManageError: String? = null,

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

    fun updateCustomGenModelInput(value: String) {
        _state.update { it.copy(customGenModelInput = value, modelManageError = null) }
    }

    fun updateCustomEditModelInput(value: String) {
        _state.update { it.copy(customEditModelInput = value, modelManageError = null) }
    }

    fun toggleGenerationModel(model: String) {
        _state.update { state ->
            val disabled = state.disabledGenerationModels.toMutableSet()
            if (model in disabled) disabled.remove(model) else disabled.add(model)
            syncDefaultModelWithAvailable(state.copy(disabledGenerationModels = disabled, saveSuccess = false))
        }
    }

    fun toggleEditModel(model: String) {
        _state.update { state ->
            val disabled = state.disabledEditModels.toMutableSet()
            if (model in disabled) disabled.remove(model) else disabled.add(model)
            state.copy(disabledEditModels = disabled, saveSuccess = false)
        }
    }

    fun addCustomGenerationModel() {
        val name = _state.value.customGenModelInput.trim()
        if (name.isBlank()) {
            _state.update { it.copy(modelManageError = "模型名称不能为空") }
            return
        }
        val allModels = _state.value.presetGenerationModels + _state.value.customGenerationModels
        if (name in allModels) {
            _state.update { it.copy(modelManageError = "模型已存在") }
            return
        }
        _state.update {
            syncDefaultModelWithAvailable(
                it.copy(
                    customGenerationModels = it.customGenerationModels + name,
                    customGenModelInput = "",
                    saveSuccess = false,
                    modelManageError = null
                )
            )
        }
    }

    fun addCustomEditModel() {
        val name = _state.value.customEditModelInput.trim()
        if (name.isBlank()) {
            _state.update { it.copy(modelManageError = "模型名称不能为空") }
            return
        }
        val allModels = _state.value.presetEditModels + _state.value.customEditModels
        if (name in allModels) {
            _state.update { it.copy(modelManageError = "模型已存在") }
            return
        }
        _state.update {
            it.copy(
                customEditModels = it.customEditModels + name,
                customEditModelInput = "",
                saveSuccess = false,
                modelManageError = null
            )
        }
    }

    fun removeCustomGenerationModel(model: String) {
        _state.update {
            syncDefaultModelWithAvailable(
                it.copy(
                    customGenerationModels = it.customGenerationModels - model,
                    saveSuccess = false
                )
            )
        }
    }

    fun removeCustomEditModel(model: String) {
        _state.update {
            it.copy(
                customEditModels = it.customEditModels - model,
                saveSuccess = false
            )
        }
    }

    fun dismissModelManageError() {
        _state.update { it.copy(modelManageError = null) }
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
        val providerId = state.providerId
        prefs.providerId = providerId
        prefs.setProviderApiKey(providerId, state.apiKey)
        prefs.setDefaultModel(providerId, state.defaultModel)
        prefs.setDefaultSize(providerId, state.defaultSize)
        prefs.setDisabledGenerationModels(providerId, state.disabledGenerationModels)
        prefs.setDisabledEditModels(providerId, state.disabledEditModels)
        prefs.setCustomGenerationModels(providerId, state.customGenerationModels)
        prefs.setCustomEditModels(providerId, state.customEditModels)
        _state.update { it.copy(saveSuccess = true) }
    }

    private fun load() {
        applyProviderState(prefs.providerId, markUnsaved = false)
    }

    /**
     * 按指定 providerId 加载该提供商专属的密钥、模型开关、自定义模型及默认模型/尺寸，
     * 并以新解析出的默认模型计算可用尺寸档位。
     */
    private fun applyProviderState(requestedProviderId: String, markUnsaved: Boolean) {
        val provider = registry.resolve(requestedProviderId)
        val definition = provider.definition
        val resolvedId = definition.id
        val disabledGen = prefs.disabledGenerationModels(resolvedId)
        val disabledEdit = prefs.disabledEditModels(resolvedId)
        val customGen = prefs.customGenerationModels(resolvedId)
        val customEdit = prefs.customEditModels(resolvedId)
        val configuredGenModels = buildAvailableModels(
            definition.presetGenerationModels,
            disabledGen,
            customGen
        )
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
                presetGenerationModels = definition.presetGenerationModels,
                presetEditModels = definition.presetEditModels,
                disabledGenerationModels = disabledGen,
                disabledEditModels = disabledEdit,
                customGenerationModels = customGen,
                customEditModels = customEdit,
                availableSizes = sizes,
                saveSuccess = if (markUnsaved) false else it.saveSuccess
            )
        }
    }

    private fun syncDefaultModelWithAvailable(state: SettingsState): SettingsState {
        val provider = registry.resolve(state.providerId)
        val configuredModels = buildAvailableModels(
            state.presetGenerationModels,
            state.disabledGenerationModels,
            state.customGenerationModels
        )
        if (configuredModels.isEmpty() || state.defaultModel in configuredModels) {
            return state
        }
        val fallbackModel = provider.definition.defaultModel.takeIf { it in configuredModels }
            ?: configuredModels.first()
        val sizes = provider.supportedSizes(fallbackModel)
        val safeSize = ensureSupportedSize(state.defaultSize, sizes)
        return state.copy(defaultModel = fallbackModel, availableSizes = sizes, defaultSize = safeSize)
    }

    /**
     * 确保当前选中尺寸在可用列表中；若不在则回退到列表首个选项。
     */
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
