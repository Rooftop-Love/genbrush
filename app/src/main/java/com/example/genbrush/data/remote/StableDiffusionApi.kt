package com.example.genbrush.data.remote

import com.example.genbrush.data.network.executeCancellable
import com.example.genbrush.data.remote.model.SdImageResponse
import com.example.genbrush.data.remote.model.SdImg2ImgRequest
import com.example.genbrush.data.remote.model.SdLoraInfo
import com.example.genbrush.data.remote.model.SdModelInfo
import com.example.genbrush.data.remote.model.SdProgressResponse
import com.example.genbrush.data.remote.model.SdTxt2ImgRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class StableDiffusionApi(
    private val client: OkHttpClient,
    private val gson: Gson
) {

    suspend fun txt2img(
        serverUrl: String,
        request: SdTxt2ImgRequest
    ): Result<SdImageResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(request)
            val httpRequest = Request.Builder()
                .url("$serverUrl/sdapi/v1/txt2img")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.executeCancellable(httpRequest)
            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("响应内容为空"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("SD API 错误: HTTP ${response.code} - ${body.take(200)}")
                )
            }

            val result = gson.fromJson(body, SdImageResponse::class.java)
            if (result.images.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("SD 未返回图片"))
            }

            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun img2img(
        serverUrl: String,
        request: SdImg2ImgRequest
    ): Result<SdImageResponse> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(request)
            val httpRequest = Request.Builder()
                .url("$serverUrl/sdapi/v1/img2img")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.executeCancellable(httpRequest)
            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("响应内容为空"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("SD API 错误: HTTP ${response.code} - ${body.take(200)}")
                )
            }

            val result = gson.fromJson(body, SdImageResponse::class.java)
            if (result.images.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("SD 未返回图片"))
            }

            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModels(
        serverUrl: String
    ): Result<List<SdModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/sdapi/v1/sd-models")
                .get()
                .build()

            val response = client.executeCancellable(request)
            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("响应内容为空"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val type = object : TypeToken<List<SdModelInfo>>() {}.type
            val models: List<SdModelInfo> = gson.fromJson(body, type)
            Result.success(models)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLoras(
        serverUrl: String
    ): Result<List<SdLoraInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/sdapi/v1/loras")
                .get()
                .build()

            val response = client.executeCancellable(request)
            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("响应内容为空"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val type = object : TypeToken<List<SdLoraInfo>>() {}.type
            val loras: List<SdLoraInfo> = gson.fromJson(body, type)
            Result.success(loras)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProgress(
        serverUrl: String
    ): Result<SdProgressResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/sdapi/v1/progress")
                .get()
                .build()

            val response = client.executeCancellable(request)
            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("响应内容为空"))

            val result = gson.fromJson(body, SdProgressResponse::class.java)
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(serverUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl/sdapi/v1/sd-models")
                .get()
                .build()

            val response = client.executeCancellable(request)
            Result.success(response.isSuccessful)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
