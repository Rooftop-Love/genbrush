package com.example.genbrush.data.provider.agnes

import android.util.Base64
import com.example.genbrush.data.provider.ImageEditRequest
import com.example.genbrush.data.provider.ImageProvider
import com.example.genbrush.data.provider.ProviderCredentials
import com.example.genbrush.data.provider.ProviderImage
import com.example.genbrush.data.provider.SizeOption
import com.example.genbrush.data.provider.TextToImageRequest

/**
 * Agnes AI 提供商实现。
 * 三个 flash 图像模型均支持文生图与图生图：编辑通过 extra_body.image 传 Data URI Base64 数组，
 * 输出格式通过 extra_body.response_format 指定（顶层 response_format 不生效）。
 * 文档未提供负面提示词参数，negativePrompt 不发送。
 */
class AgnesProvider(private val api: AgnesApi) : ImageProvider {

    override val definition = AgnesDefinition.definition

    override suspend fun generateTextToImage(
        credentials: ProviderCredentials,
        request: TextToImageRequest
    ): Result<ProviderImage> {
        val sizeParams = AgnesDefinition.apiSizeParams(request.model, request.size)
        val apiRequest = GenerationRequest(
            model = request.model,
            prompt = request.prompt,
            size = sizeParams.size,
            ratio = sizeParams.ratio,
            extra_body = ExtraBody(response_format = GenerationRequest.RESPONSE_FORMAT_URL)
        )
        return api.generateImage(credentials.apiKey, apiRequest).map { it.toProviderImage() }
    }

    override suspend fun generateImageEdit(
        credentials: ProviderCredentials,
        request: ImageEditRequest
    ): Result<ProviderImage> {
        val sizeParams = AgnesDefinition.apiSizeParams(request.model, request.size)
        val apiRequest = GenerationRequest(
            model = request.model,
            prompt = request.prompt,
            size = sizeParams.size,
            ratio = sizeParams.ratio,
            extra_body = ExtraBody(
                image = listOf(request.imageDataUri),
                response_format = GenerationRequest.RESPONSE_FORMAT_URL
            )
        )
        return api.generateImage(credentials.apiKey, apiRequest).map { it.toProviderImage() }
    }

    override fun supportedSizes(model: String?): List<SizeOption> =
        AgnesDefinition.supportedSizes(model)

    override suspend fun fetchModels(credentials: ProviderCredentials): Result<List<String>> =
        api.listModels(credentials.apiKey).map { ids ->
            ids.filter { AgnesDefinition.isImageModelId(it) }
        }
}

private fun ImageApiResponse.toProviderImage(): ProviderImage {
    val item = data?.firstOrNull()
        ?: throw Exception("未生成图片，提示词可能被内容安全过滤器拦截。")
    item.url?.let { return ProviderImage.Remote(it) }
    item.b64_json?.let { return ProviderImage.Raw(Base64.decode(it, Base64.DEFAULT)) }
    throw Exception("响应中未包含图片")
}
