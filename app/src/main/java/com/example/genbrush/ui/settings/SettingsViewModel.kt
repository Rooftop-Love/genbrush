package com.example.genbrush.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.remote.StableDiffusionApi
import com.example.genbrush.ui.components.PRESET_EDIT_MODELS
import com.example.genbrush.ui.components.PRESET_GENERATION_MODELS
import com.example.genbrush.ui.components.SizeOption
import com.example.genbrush.ui.components.getSupportedSizes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 可折叠面板的标识
const val SECTION_API_CONFIG = "api_config"
const val SECTION_SD_SERVER = "sd_server"
const val SECTION_DEFAULTS = "defaults"
const val SECTION_LANGUAGE = "language"
const val SECTION_ABOUT = "about"
const val SECTION_MIGRATION = "migration"

private val ALL_SECTIONS = setOf(
    SECTION_API_CONFIG, SECTION_SD_SERVER,
    SECTION_DEFAULTS, SECTION_LANGUAGE, SECTION_MIGRATION, SECTION_ABOUT
)

data class SettingsState(
    val apiKey: String = "",
    val isApiKeyVisible: Boolean = false,
    val defaultModel: String = PreferencesManager.DEFAULT_MODEL,
    val defaultSize: String = PreferencesManager.DEFAULT_SIZE,
    val language: String = PreferencesManager.LANGUAGE_ZH,
    val backend: String = PreferencesManager.BACKEND_DASHSCOPE,
    val sdServerUrl: String = "",
    val sdConnectionStatus: String? = null,
    val isTestingConnection: Boolean = false,
    val saveSuccess: Boolean = false,

    // 模型管理
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
    private val sdApi: StableDiffusionApi
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
        val sizes = getSupportedSizes(model, _state.value.backend == PreferencesManager.BACKEND_SD_WEBUI)
        val safeSize = ensureSupportedSize(_state.value.defaultSize, sizes)
        _state.update { it.copy(defaultModel = model, availableSizes = sizes, defaultSize = safeSize, saveSuccess = false) }
    }

    fun selectSize(size: String) {
        _state.update { it.copy(defaultSize = size, saveSuccess = false) }
    }

    fun selectLanguage(lang: String) {
        _state.update { it.copy(language = lang) }
    }

    fun selectBackend(backend: String) {
        val isSd = backend == PreferencesManager.BACKEND_SD_WEBUI
        val sizes = getSupportedSizes(_state.value.defaultModel, isSd)
        val safeSize = ensureSupportedSize(_state.value.defaultSize, sizes)
        _state.update { it.copy(backend = backend, availableSizes = sizes, defaultSize = safeSize, saveSuccess = false) }
    }

    fun updateSdServerUrl(url: String) {
        _state.update { it.copy(sdServerUrl = url, sdConnectionStatus = null, saveSuccess = false) }
    }

    fun testSdConnection() {
        val url = _state.value.sdServerUrl.trim()
        if (url.isBlank()) {
            _state.update { it.copy(sdConnectionStatus = "fail:请输入服务器地址") }
            return
        }

        _state.update { it.copy(isTestingConnection = true, sdConnectionStatus = null) }

        viewModelScope.launch {
            val result = sdApi.testConnection(url)
            result.fold(
                onSuccess = { success ->
                    _state.update {
                        it.copy(
                            isTestingConnection = false,
                            sdConnectionStatus = if (success) "ok" else "fail:连接失败"
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isTestingConnection = false,
                            sdConnectionStatus = "fail:${e.message ?: "连接失败"}"
                        )
                    }
                }
            )
        }
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
            state.copy(disabledGenerationModels = disabled, saveSuccess = false)
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
        val allModels = PRESET_GENERATION_MODELS + _state.value.customGenerationModels
        if (name in allModels) {
            _state.update { it.copy(modelManageError = "模型已存在") }
            return
        }
        _state.update {
            it.copy(
                customGenerationModels = it.customGenerationModels + name,
                customGenModelInput = "",
                saveSuccess = false,
                modelManageError = null
            )
        }
    }

    fun addCustomEditModel() {
        val name = _state.value.customEditModelInput.trim()
        if (name.isBlank()) {
            _state.update { it.copy(modelManageError = "模型名称不能为空") }
            return
        }
        val allModels = PRESET_EDIT_MODELS + _state.value.customEditModels
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
            it.copy(
                customGenerationModels = it.customGenerationModels - model,
                saveSuccess = false
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
        prefs.apiKey = _state.value.apiKey
        prefs.defaultModel = _state.value.defaultModel
        prefs.defaultSize = _state.value.defaultSize
        prefs.backend = _state.value.backend
        prefs.sdServerUrl = _state.value.sdServerUrl.trim()
        prefs.disabledGenerationModels = _state.value.disabledGenerationModels
        prefs.disabledEditModels = _state.value.disabledEditModels
        prefs.customGenerationModels = _state.value.customGenerationModels
        prefs.customEditModels = _state.value.customEditModels
        _state.update { it.copy(saveSuccess = true) }
    }

    private fun load() {
        val isSd = prefs.backend == PreferencesManager.BACKEND_SD_WEBUI
        val sizes = getSupportedSizes(prefs.defaultModel, isSd)
        val safeSize = ensureSupportedSize(prefs.defaultSize, sizes)
        _state.update {
            it.copy(
                apiKey = prefs.apiKey,
                defaultModel = prefs.defaultModel,
                defaultSize = safeSize,
                language = prefs.language,
                backend = prefs.backend,
                sdServerUrl = prefs.sdServerUrl,
                disabledGenerationModels = prefs.disabledGenerationModels,
                disabledEditModels = prefs.disabledEditModels,
                customGenerationModels = prefs.customGenerationModels,
                customEditModels = prefs.customEditModels,
                availableSizes = sizes
            )
        }
    }

    /**
     * 确保当前选中尺寸在可用列表中；若不在则回退到列表首个选项。
     */
    private fun ensureSupportedSize(currentSize: String, sizes: List<SizeOption>): String {
        if (sizes.isEmpty()) return currentSize
        return if (sizes.any { it.value == currentSize }) currentSize else sizes.first().value
    }

    companion object {
        fun factory(prefs: PreferencesManager, sdApi: StableDiffusionApi) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(prefs, sdApi) as T
                }
            }
    }
}
