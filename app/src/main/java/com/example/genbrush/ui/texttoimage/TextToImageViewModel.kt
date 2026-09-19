package com.example.genbrush.ui.texttoimage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.provider.ImageProvider
import com.example.genbrush.data.provider.ProviderRegistry
import com.example.genbrush.data.provider.SizeOption
import com.example.genbrush.ui.common.mapError
import com.example.genbrush.data.repository.GenerationRepository
import com.example.genbrush.ui.components.buildAvailableModels
import com.example.genbrush.ui.localization.resolveAppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        refreshModels()
    }

    fun refreshModels() {
        val provider = registry.resolve(prefs.providerId)
        val models = buildAvailableModels(
            provider.definition.presetGenerationModels,
            prefs.disabledGenerationModels,
            prefs.customGenerationModels
        )
        val selectedModel = if (prefs.defaultModel in models) prefs.defaultModel
        else models.firstOrNull() ?: prefs.defaultModel
        val sizes = provider.supportedSizes(selectedModel)
        val safeSize = ensureSupportedSize(prefs.defaultSize, sizes)
        _state.update {
            it.copy(
                availableModels = models,
                selectedModel = selectedModel,
                availableSizes = sizes,
                selectedSize = safeSize
            )
        }
        maybeFetchRemoteModels(provider)
    }

    /** 支持在线模型列表的提供商（fetchModels 非 null）用远端列表覆盖预设列表 */
    private fun maybeFetchRemoteModels(provider: ImageProvider) {
        viewModelScope.launch {
            val remote = try {
                provider.fetchModels()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return@launch
            if (remote.isEmpty()) return@launch
            _state.update { it.copy(availableModels = remote) }
            if (_state.value.selectedModel !in remote) {
                selectModel(remote.first())
            }
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
