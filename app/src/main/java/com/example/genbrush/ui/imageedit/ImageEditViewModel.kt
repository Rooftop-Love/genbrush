package com.example.genbrush.ui.imageedit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.remote.StableDiffusionApi
import com.example.genbrush.data.remote.model.SdLoraInfo
import com.example.genbrush.ui.common.mapError
import com.example.genbrush.data.repository.GenerationRepository
import com.example.genbrush.ui.components.SizeOption
import com.example.genbrush.ui.components.getConfiguredEditModels
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

data class ImageEditState(
    val sourceImageUri: Uri? = null,
    val prompt: String = "",
    val selectedModel: String = "",
    val selectedSize: String = PreferencesManager.DEFAULT_SIZE,
    val isGenerating: Boolean = false,
    val resultImageFile: File? = null,
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

class ImageEditViewModel(
    private val repository: GenerationRepository,
    private val prefs: PreferencesManager,
    private val sdApi: StableDiffusionApi
) : ViewModel() {

    private val _state = MutableStateFlow(ImageEditState())
    val state: StateFlow<ImageEditState> = _state.asStateFlow()
    private var generationJob: Job? = null
    private var timerJob: Job? = null

    init {
        val isSd = prefs.backend == PreferencesManager.BACKEND_SD_WEBUI
        val models = if (!isSd) getConfiguredEditModels(prefs) else emptyList()
        val selectedModel = models.firstOrNull() ?: ""
        val sizes = getSupportedSizes(selectedModel, isSd)
        val safeSize = ensureSupportedSize(prefs.defaultSize, sizes)
        _state.update {
            it.copy(
                isSdBackend = isSd,
                availableModels = models,
                selectedModel = selectedModel,
                selectedSize = safeSize,
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
            val models = getConfiguredEditModels(prefs)
            val selectedModel = models.firstOrNull() ?: ""
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

    fun setSourceImage(uri: Uri?) {
        _state.update { it.copy(sourceImageUri = uri) }
    }

    fun updatePrompt(value: String) {
        _state.update { it.copy(prompt = value) }
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

    fun generate(context: Context) {
        val currentState = _state.value
        if (currentState.isGenerating) return
        val s = resolveAppStrings(prefs.language)
        if (currentState.sourceImageUri == null) {
            _state.update { it.copy(error = s.errSelectImage) }
            return
        }
        if (currentState.prompt.isBlank()) {
            _state.update { it.copy(error = s.errEnterEditPrompt) }
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
                val result = repository.generateImageEdit(
                    imageUri = currentState.sourceImageUri,
                    prompt = finalPrompt,
                    model = currentState.selectedModel,
                    size = currentState.selectedSize,
                    context = context
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
        _state.update { it.copy(resultImageFile = null) }
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
                    return ImageEditViewModel(repository, prefs, sdApi) as T
                }
            }
    }
}
