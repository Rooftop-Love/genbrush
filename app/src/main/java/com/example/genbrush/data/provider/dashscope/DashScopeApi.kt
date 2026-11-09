package com.example.genbrush.data.provider.dashscope

import com.example.genbrush.data.network.executeCancellable
import com.example.genbrush.data.provider.OpenAIModelListApi
import com.example.genbrush.data.provider.ProviderException
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DashScopeApi(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val modelListApi = OpenAIModelListApi(client, gson, MODELS_ENDPOINT)

    /** 手动刷新模型清单时调用；生成流程不触发。 */
    suspend fun listModels(apiKey: String): Result<List<String>> = modelListApi.listModels(apiKey)

    suspend fun generateImage(
        apiKey: String,
        request: GenerationRequest
    ): Result<GenerationResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(request)
            val httpRequest = Request.Builder()
                .url(SYNC_ENDPOINT)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.executeCancellable(httpRequest)
            val body = response.body?.string() ?: return@withContext Result.failure(
                Exception("响应内容为空")
            )

            if (!response.isSuccessful) {
                val errorResponse = try {
                    gson.fromJson(body, GenerationResponse::class.java)
                } catch (_: Exception) { null }
                val errorMsg = errorResponse?.message
                    ?: body.takeIf { it.isNotBlank() }?.take(500)
                    ?: "HTTP ${response.code}"
                val errorCode = errorResponse?.code ?: "HTTP_${response.code}"
                return@withContext Result.failure(
                    DashScopeException(errorCode, errorMsg)
                )
            }

            val generationResponse = gson.fromJson(body, GenerationResponse::class.java)
            if (generationResponse.code != null && generationResponse.code!!.isNotEmpty()) {
                return@withContext Result.failure(
                    DashScopeException(
                        generationResponse.code!!,
                        generationResponse.message ?: "未知 API 错误"
                    )
                )
            }

            if (generationResponse.output?.choices.isNullOrEmpty()) {
                return@withContext Result.failure(
                    Exception("未生成图片，提示词可能被内容安全过滤器拦截。")
                )
            }

            Result.success(generationResponse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitAsyncTask(
        apiKey: String,
        request: TaskSubmitRequest
    ): Result<TaskSubmitResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(request)
            val httpRequest = Request.Builder()
                .url(ASYNC_ENDPOINT)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-DashScope-Async", "enable")
                .build()

            val response = client.executeCancellable(httpRequest)
            val body = response.body?.string() ?: return@withContext Result.failure(
                Exception("响应内容为空")
            )

            if (!response.isSuccessful) {
                val errorResponse = try {
                    gson.fromJson(body, TaskSubmitResponse::class.java)
                } catch (_: Exception) { null }
                return@withContext Result.failure(
                    DashScopeException(
                        errorResponse?.code ?: "HTTP_${response.code}",
                        errorResponse?.message ?: "HTTP ${response.code}"
                    )
                )
            }

            val submitResponse = gson.fromJson(body, TaskSubmitResponse::class.java)
            if (submitResponse.code != null && submitResponse.code!!.isNotEmpty()) {
                return@withContext Result.failure(
                    DashScopeException(
                        submitResponse.code!!,
                        submitResponse.message ?: "未知 API 错误"
                    )
                )
            }

            if (submitResponse.output?.task_id.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("未获取到任务 ID"))
            }

            Result.success(submitResponse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollTaskStatus(
        apiKey: String,
        taskId: String
    ): Result<TaskStatusResponse> = withContext(Dispatchers.IO) {
        try {
            var attempts = 0
            val maxAttempts = 120 // max 10 minutes at 5s intervals

            while (attempts < maxAttempts) {
                delay(5000)
                attempts++

                val httpRequest = Request.Builder()
                    .url("$TASK_ENDPOINT/$taskId")
                    .get()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                val response = client.executeCancellable(httpRequest)
                val body = response.body?.string() ?: continue

                if (!response.isSuccessful) continue

                val statusResponse = gson.fromJson(body, TaskStatusResponse::class.java)

                when (statusResponse.output?.task_status) {
                    "SUCCEEDED" -> return@withContext Result.success(statusResponse)
                    "FAILED" -> return@withContext Result.failure(
                        DashScopeException(
                            statusResponse.code ?: "TASK_FAILED",
                            statusResponse.message ?: statusResponse.output?.task_status ?: "任务失败"
                        )
                    )
                    "CANCELED" -> return@withContext Result.failure(
                        Exception("任务已取消")
                    )
                    // PENDING, RUNNING -> continue polling
                }
            }

            Result.failure(Exception("任务超时，请稍后重试"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val SYNC_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
        const val ASYNC_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation"
        const val TASK_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/tasks"
        /** OpenAI 兼容模式的模型列表端点 */
        const val MODELS_ENDPOINT =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/models"
    }
}

class DashScopeException(code: String, message: String) : ProviderException(code, message)
