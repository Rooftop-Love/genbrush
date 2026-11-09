package com.example.genbrush.data.provider

import com.example.genbrush.data.network.executeCancellable
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** OpenAI 兼容的 GET /models 响应：{"data":[{"id":"..."}],"error":{...}} */
data class OpenAIModelListResponse(
    val data: List<OpenAIModelEntry>? = null,
    val error: OpenAIModelListError? = null
)

data class OpenAIModelEntry(val id: String? = null)

data class OpenAIModelListError(
    val message: String? = null,
    val code: String? = null,
    val type: String? = null
)

/** 拉取上游模型列表失败，供上层 ErrorMapper 统一映射为本地化提示。 */
class ModelListException(code: String, message: String) : ProviderException(code, message)

/**
 * 两家提供商都提供 OpenAI 兼容的 GET /models，这里统一处理请求与响应解析。
 * 仅由设置页「手动刷新模型」触发；生成流程不依赖网络模型列表。
 */
class OpenAIModelListApi(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val endpoint: String
) {
    suspend fun listModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            val response = client.executeCancellable(request)
            val body = response.body?.string()
                ?: return@withContext Result.failure(ModelListException("EMPTY_RESPONSE", "响应内容为空"))
            val parsed = try {
                gson.fromJson(body, OpenAIModelListResponse::class.java)
            } catch (_: Exception) {
                null
            }
            if (!response.isSuccessful) {
                val message = parsed?.error?.message
                    ?: body.takeIf { it.isNotBlank() }?.take(300)
                    ?: "HTTP ${response.code}"
                val code = parsed?.error?.code ?: parsed?.error?.type ?: "HTTP_${response.code}"
                return@withContext Result.failure(ModelListException(code, message))
            }
            val ids = parsed?.data.orEmpty()
                .mapNotNull { it.id?.trim()?.takeIf { id -> id.isNotEmpty() } }
                .distinct()
            if (ids.isEmpty()) {
                return@withContext Result.failure(
                    ModelListException("EMPTY_MODEL_LIST", "上游未返回任何模型")
                )
            }
            Result.success(ids)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
