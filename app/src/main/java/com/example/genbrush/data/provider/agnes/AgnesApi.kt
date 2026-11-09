package com.example.genbrush.data.provider.agnes

import com.example.genbrush.data.network.executeCancellable
import com.example.genbrush.data.provider.OpenAIModelListApi
import com.example.genbrush.data.provider.ProviderException
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Agnes AI API（https://apihub.agnes-ai.com）。
 * OpenAI images 兼容：文生图与图生图（extra_body.image 传 Data URI Base64 数组）共用一个端点。
 * 官方建议客户端读超时 60s 以上，长档位（3K/4K）生成耗时明显更长。
 */
class AgnesApi(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val modelListApi = OpenAIModelListApi(client, gson, MODELS_ENDPOINT)

    /** 手动刷新模型清单时调用；生成流程不触发。 */
    suspend fun listModels(apiKey: String): Result<List<String>> = modelListApi.listModels(apiKey)

    suspend fun generateImage(
        apiKey: String,
        request: GenerationRequest
    ): Result<ImageApiResponse> = withContext(Dispatchers.IO) {
        try {
            val httpRequest = Request.Builder()
                .url(GENERATIONS_ENDPOINT)
                .post(gson.toJson(request).toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val response = client.executeCancellable(httpRequest)
            val text = response.body?.string()
                ?: return@withContext Result.failure(Exception("响应内容为空"))
            parseResponse(response.code, text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseResponse(httpCode: Int, body: String): Result<ImageApiResponse> {
        val parsed = try {
            gson.fromJson(body, ImageApiResponse::class.java)
        } catch (_: Exception) {
            null
        }

        if (httpCode !in 200..299) {
            val error = parsed?.error
            val message = error?.message
                ?: body.takeIf { it.isNotBlank() }?.take(500)
                ?: "HTTP $httpCode"
            val code = error?.code ?: error?.type ?: "HTTP_$httpCode"
            return Result.failure(AgnesException(code, message))
        }

        val error = parsed?.error
        if (error != null) {
            return Result.failure(
                AgnesException(
                    error.code ?: error.type ?: "API_ERROR",
                    error.message ?: "未知 API 错误"
                )
            )
        }

        if (parsed?.data.isNullOrEmpty()) {
            return Result.failure(Exception("未生成图片，提示词可能被内容安全过滤器拦截。"))
        }

        return Result.success(parsed)
    }

    companion object {
        const val BASE_URL = "https://apihub.agnes-ai.com/v1"
        const val GENERATIONS_ENDPOINT = "$BASE_URL/images/generations"
        /** OpenAI 兼容的模型列表端点 */
        const val MODELS_ENDPOINT = "$BASE_URL/models"
    }
}

class AgnesException(
    code: String,
    message: String
) : ProviderException(
    code = code,
    message = message,
    isContentSafety = message.contains("content policy", ignoreCase = true) ||
        message.contains("content_policy", ignoreCase = true) ||
        message.contains("moderation", ignoreCase = true)
)
