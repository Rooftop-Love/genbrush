package com.example.genbrush.ui.texttoimage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.remote.StableDiffusionApi
import com.example.genbrush.data.remote.model.SdLoraInfo
import com.example.genbrush.ui.common.mapError
import com.example.genbrush.data.repository.GenerationRepository
import com.example.genbrush.ui.components.SizeOption
import com.example.genbrush.ui.components.getConfiguredGenerationModels
import com.example.genbrush.ui.components.getSupportedSizes
import com.example.genbrush.ui.localization.AppStrings
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
    val isSdBackend: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val availableLoras: List<SdLoraInfo> = emptyList(),
    val selectedLoras: Map<String, Float> = emptyMap(),
    val availableSizes: List<SizeOption> = emptyList(),
    val elapsedSeconds: Long = 0L
)

class TextToImageViewModel(
    private val repository: GenerationRepository,
    private val prefs: PreferencesManager,
    private val sdApi: StableDiffusionApi
) : ViewModel() {

    private val _state = MutableStateFlow(TextToImageState())
    val state: StateFlow<TextToImageState> = _state.asStateFlow()
    private var generationJob: Job? = null
    private var timerJob: Job? = null

    init {
        val isSd = prefs.backend == PreferencesManager.BACKEND_SD_WEBUI
        val models = if (!isSd) getConfiguredGenerationModels(prefs) else emptyList()
        val defaultModel = if (models.isNotEmpty() && prefs.defaultModel !in models) models.first() else prefs.defaultModel
        val sizes = getSupportedSizes(defaultModel, isSd)
        val safeSize = ensureSupportedSize(prefs.defaultSize, sizes)
        _state.update {
            it.copy(
                selectedModel = defaultModel,
                selectedSize = safeSize,
                isSdBackend = isSd,
                availableModels = models,
                availableSizes = sizes
            )
        }

        if (isSd) {
            loadSdModels()
            loadSdLoras()
        }
    }

    private fun loadSdModels() {
        val serverUrl = prefs.sdServerUrl
        if (serverUrl.isBlank()) return

        viewModelScope.launch {
            sdApi.getModels(serverUrl).onSuccess { models ->
                val modelNames = models.map { it.model_name }
                if (modelNames.isNotEmpty()) {
                    val sizes = getSupportedSizes(modelNames.first(), isSdBackend = true)
                    val safeSize = ensureSupportedSize(_state.value.selectedSize, sizes)
                    _state.update {
                        it.copy(
                            availableModels = modelNames,
                            selectedModel = modelNames.first(),
                            availableSizes = sizes,
                            selectedSize = safeSize
                        )
                    }
                }
            }
        }
    }

    private fun loadSdLoras() {
        val serverUrl = prefs.sdServerUrl
        if (serverUrl.isBlank()) return

        viewModelScope.launch {
            sdApi.getLoras(serverUrl).onSuccess { loras ->
                _state.update { it.copy(availableLoras = loras) }
            }
        }
    }

    fun refreshModels() {
        val isSd = prefs.backend == PreferencesManager.BACKEND_SD_WEBUI
        _state.update { it.copy(isSdBackend = isSd) }

        if (isSd) {
            loadSdModels()
            loadSdLoras()
        } else {
            val models = getConfiguredGenerationModels(prefs)
            val selectedModel = if (prefs.defaultModel in models) prefs.defaultModel else models.firstOrNull() ?: ""
            val sizes = getSupportedSizes(selectedModel, isSd)
            val safeSize = ensureSupportedSize(_state.value.selectedSize, sizes)
            _state.update {
                it.copy(
                    availableModels = models,
                    selectedModel = selectedModel,
                    availableLoras = emptyList(),
                    selectedLoras = emptyMap(),
                    availableSizes = sizes,
                    selectedSize = safeSize
                )
            }
        }
    }

    fun toggleLora(name: String) {
        _state.update { state ->
            val newMap = if (state.selectedLoras.containsKey(name)) {
                state.selectedLoras - name
            } else {
                state.selectedLoras + (name to 1.0f)
            }
            state.copy(selectedLoras = newMap)
        }
    }

    fun updateLoraWeight(name: String, weight: Float) {
        _state.update { state ->
            state.copy(selectedLoras = state.selectedLoras + (name to weight))
        }
    }

    fun updatePrompt(value: String) {
        _state.update { it.copy(prompt = value) }
    }

    fun updateNegativePrompt(value: String) {
        _state.update { it.copy(negativePrompt = value) }
    }

    fun selectModel(model: String) {
        val sizes = getSupportedSizes(model, _state.value.isSdBackend)
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

        // 将已选 LoRA 拼接到 prompt 末尾
        val loraSuffix = currentState.selectedLoras.entries.joinToString(" ") { (name, weight) ->
            "<lora:$name:${"%.1f".format(weight)}>"
        }
        val finalPrompt = if (loraSuffix.isNotBlank()) {
            "${currentState.prompt} $loraSuffix"
        } else {
            currentState.prompt
        }

        generationJob = viewModelScope.launch {
            try {
                val result = repository.generateTextToImage(
                    prompt = finalPrompt,
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
        fun factory(repository: GenerationRepository, prefs: PreferencesManager, sdApi: StableDiffusionApi) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TextToImageViewModel(repository, prefs, sdApi) as T
                }
            }
    }
}
