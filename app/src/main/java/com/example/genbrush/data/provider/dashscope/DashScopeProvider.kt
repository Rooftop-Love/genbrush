package com.example.genbrush.data.provider.dashscope

import com.example.genbrush.data.provider.ImageEditRequest
import com.example.genbrush.data.provider.ImageProvider
import com.example.genbrush.data.provider.ProviderCredentials
import com.example.genbrush.data.provider.ProviderImage
import com.example.genbrush.data.provider.SizeOption
import com.example.genbrush.data.provider.TextToImageRequest

/**
 * 阿里云百炼（DashScope）提供商实现。
 * qwen-image 系列走同步多模态接口，wan 系列走异步任务提交 + 轮询。
 */
class DashScopeProvider(private val api: DashScopeApi) : ImageProvider {

    override val definition = DashScopeDefinition.definition

    override suspend fun generateTextToImage(
        credentials: ProviderCredentials,
        request: TextToImageRequest
    ): Result<ProviderImage> {
        return if (DashScopeDefinition.isWanModel(request.model)) {
            generateAsync(credentials.apiKey, request)
        } else {
            generateSync(credentials.apiKey, prompt = request.prompt, negativePrompt = request.negativePrompt, model = request.model, size = request.size)
        }
    }

    override suspend fun generateImageEdit(
        credentials: ProviderCredentials,
        request: ImageEditRequest
    ): Result<ProviderImage> {
        // wan 系列的编辑走异步任务接口；其余走同步接口（与文生图同端点，携带输入图片）
        return if (DashScopeDefinition.isWanModel(request.model)) {
            generateEditAsync(credentials.apiKey, request)
        } else {
            generateSync(credentials.apiKey, prompt = request.prompt, negativePrompt = null, model = request.model, size = request.size, inputImage = request.imageDataUri)
        }
    }

    override fun supportedSizes(model: String?): List<SizeOption> =
        DashScopeDefinition.supportedSizes(model)

    override suspend fun fetchModels(credentials: ProviderCredentials): Result<List<String>> =
        api.listModels(credentials.apiKey).map { ids ->
            ids.filter { DashScopeDefinition.isImageModelId(it) }
        }

    // ==================== 同步接口（qwen-image 系列） ====================

    private suspend fun generateSync(
        apiKey: String,
        prompt: String,
        negativePrompt: String?,
        model: String,
        size: String,
        inputImage: String? = null
    ): Result<ProviderImage> {
        val content = buildList {
            if (inputImage != null) add(ContentItem(image = inputImage))
            add(ContentItem(text = prompt))
        }
        val request = GenerationRequest(
            model = model,
            input = Input(messages = listOf(Message(role = "user", content = content))),
            parameters = Parameters(
                size = size,
                n = 1,
                negative_prompt = negativePrompt?.ifBlank { null },
                prompt_extend = true,
                prompt_extend_mode = if (DashScopeDefinition.isQwenImage3Model(model)) "direct" else null,
                watermark = false
            )
        )

        return api.generateImage(apiKey, request).map { response ->
            val imageUrl = response.output?.choices?.firstOrNull()?.message?.content?.firstOrNull()?.image
                ?: throw Exception("响应中未包含图片")
            ProviderImage.Remote(imageUrl)
        }
    }

    // ==================== 异步任务接口（wan 系列） ====================

    private suspend fun generateAsync(credentialsApiKey: String, request: TextToImageRequest): Result<ProviderImage> {
        val submitRequest = TaskSubmitRequest(
            model = request.model,
            input = TaskInput(
                messages = listOf(
                    Message(
                        role = "user",
                        content = listOf(ContentItem(text = request.prompt))
                    )
                )
            ),
            parameters = TaskParameters(
                size = request.size,
                n = 1,
                watermark = false
            )
        )
        return submitAndPoll(credentialsApiKey, submitRequest)
    }

    private suspend fun generateEditAsync(apiKey: String, request: ImageEditRequest): Result<ProviderImage> {
        val submitRequest = TaskSubmitRequest(
            model = request.model,
            input = TaskInput(
                messages = listOf(
                    Message(
                        role = "user",
                        content = listOf(
                            ContentItem(image = request.imageDataUri),
                            ContentItem(text = request.prompt)
                        )
                    )
                )
            ),
            parameters = TaskParameters(
                size = request.size,
                n = 1,
                watermark = false
            )
        )
        return submitAndPoll(apiKey, submitRequest)
    }

    private suspend fun submitAndPoll(apiKey: String, submitRequest: TaskSubmitRequest): Result<ProviderImage> {
        val submitResult = api.submitAsyncTask(apiKey, submitRequest)
        val taskId = submitResult.fold(
            onSuccess = { it.output?.task_id ?: return Result.failure(Exception("未获取到任务 ID")) },
            onFailure = { return Result.failure(it) }
        )

        return api.pollTaskStatus(apiKey, taskId).map { statusResponse ->
            val imageUrl = statusResponse.output?.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.firstOrNull()
                ?.image
                ?: statusResponse.output?.results?.firstOrNull()?.url
                ?: throw Exception("响应中未包含图片")
            ProviderImage.Remote(imageUrl)
        }
    }
}
