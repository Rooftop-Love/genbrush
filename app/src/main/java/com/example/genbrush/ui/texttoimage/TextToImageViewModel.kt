package com.example.genbrush.ui.texttoimage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.genbrush.data.local.ModelCapability
import com.example.genbrush.data.local.ModelCatalogRules
import com.example.genbrush.data.local.ModelSeed
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.provider.ProviderRegistry
import com.example.genbrush.data.provider.SizeOption
import com.example.genbrush.ui.common.mapError
import com.example.genbrush.data.repository.GenerationRepository
import com.example.genbrush.ui.localization.resolveAppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TextToImageState(
    val prompt: String = "",
    val negativePrompt: String = "",
    val selectedModel: String = PreferencesManager.DEFAULT_MODEL,
    val selectedSize: String = PreferencesManager.DEFAULT_SIZE,
    val isGenerating: Boolean = false,
    val resultImageFile: File? = null,
    val resultImageUrl: String? = null,
    val resultPrompt: String = "",
    val resultModel: String = "",
    val error: String? = null,
    val availableModels: List<String> = emptyList(),
    val availableSizes: List<SizeOption> = emptyList(),
    val elapsedSeconds: Long = 0L
)

class TextToImageViewModel(
    private val repository: GenerationRepository,
    private val prefs: PreferencesManager,
    private val registry: ProviderRegistry
) : ViewModel() {

    private val _state = MutableStateFlow(TextToImageState())
    val state: StateFlow<TextToImageState> = _state.asStateFlow()
    private var generationJob: Job? = null
    private var timerJob: Job? = null

    init {
        refreshModels(applyDefaults = true)
        // 配置变化后刷新模型清单，但保留本页已选中的模型（除非它已被从清单中关闭）
        viewModelScope.launch {
            prefs.configVersion.drop(1).collect { refreshModels(applyDefaults = false) }
        }
    }

    fun refreshModels(applyDefaults: Boolean = false) {
        val provider = registry.resolve(prefs.providerId)
        val definition = provider.definition
        val providerId = definition.id
        val catalog = prefs.modelCatalog(
            providerId,
            ModelSeed(
                generationModels = definition.presetGenerationModels,
                editModels = definition.presetEditModels,
                supportsImageEdit = definition.supportsImageEdit
            )
        )
        val models = ModelCatalogRules.visibleIds(catalog, ModelCapability.GENERATION)
        val previousModel = _state.value.selectedModel
        val defaultModel = prefs.defaultModel(providerId, definition.defaultModel)
        val selectedModel = when {
            !applyDefaults && previousModel in models -> previousModel
            defaultModel in models -> defaultModel
            previousModel in models -> previousModel
            definition.defaultModel in models -> definition.defaultModel
            else -> models.firstOrNull() ?: previousModel
        }
        val sizes = provider.supportedSizes(selectedModel)
        val safeSize = if (!applyDefaults && _state.value.selectedSize in sizes.map { it.value }) {
            _state.value.selectedSize
        } else {
            ensureSupportedSize(prefs.defaultSize(providerId), sizes)
        }
        _state.update {
            it.copy(
                availableModels = models,
                selectedModel = selectedModel,
                availableSizes = sizes,
                selectedSize = safeSize
            )
        }
    }

    fun updatePrompt(value: String) {
        _state.update { it.copy(prompt = value) }
    }

    fun updateNegativePrompt(value: String) {
        _state.update { it.copy(negativePrompt = value) }
    }

    fun selectModel(model: String) {
        val provider = registry.resolve(prefs.providerId)
        val sizes = provider.supportedSizes(model)
        val safeSize = ensureSupportedSize(_state.value.selectedSize, sizes)
        _state.update { it.copy(selectedModel = model, availableSizes = sizes, selectedSize = safeSize) }
    }

    fun selectSize(size: String) {
        _state.update { it.copy(selectedSize = size) }
    }

    /**
     * 确保当前选中尺寸在可用列表中；若不在则回退到列表首个选项。
     */
    private fun ensureSupportedSize(currentSize: String, sizes: List<SizeOption>): String {
        if (sizes.isEmpty()) return currentSize
        return if (sizes.any { it.value == currentSize }) currentSize else sizes.first().value
    }

    fun generate() {
        val currentState = _state.value
        if (currentState.isGenerating) return
        val s = resolveAppStrings(prefs.language)
        if (currentState.prompt.isBlank()) {
            _state.update { it.copy(error = s.errEnterPrompt) }
            return
        }

        _state.update {
            it.copy(
                isGenerating = true,
                error = null,
                resultImageFile = null,
                elapsedSeconds = 0L
            )
        }
        startTimer()

        generationJob = viewModelScope.launch {
            try {
                val result = repository.generateTextToImage(
                    prompt = currentState.prompt,
                    negativePrompt = currentState.negativePrompt,
                    model = currentState.selectedModel,
                    size = currentState.selectedSize
                )
                result.fold(
                    onSuccess = { entry ->
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                resultImageFile = repository.getLocalImagePath(entry),
                                resultPrompt = entry.prompt,
                                resultModel = entry.model
                            )
                        }
                    },
                    onFailure = { e ->
                        if (e is CancellationException) throw e
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                error = mapError(e, s)
                            )
                        }
                    }
                )
            } catch (_: CancellationException) {
                // User stopped the generation.
            } finally {
                finishGeneration()
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        finishGeneration()
    }

    fun clearResult() {
        _state.update { it.copy(resultImageFile = null, resultImageUrl = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        generationJob?.cancel()
        timerJob?.cancel()
        super.onCleared()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { state ->
                    if (state.isGenerating) {
                        state.copy(elapsedSeconds = state.elapsedSeconds + 1)
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun finishGeneration() {
        timerJob?.cancel()
        timerJob = null
        generationJob = null
        _state.update { state ->
            if (state.isGenerating) state.copy(isGenerating = false) else state
        }
    }

    companion object {
        fun factory(repository: GenerationRepository, prefs: PreferencesManager, registry: ProviderRegistry) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TextToImageViewModel(repository, prefs, registry) as T
                }
            }
    }
}
