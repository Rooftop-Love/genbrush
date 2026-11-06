package com.example.genbrush.data.network

import com.example.genbrush.data.local.AppLogStore
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class NetworkLoggingInterceptor(
    private val source: String? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val label = source ?: request.url.host
        val safeUrl = request.url.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
        val startedAt = System.nanoTime()

        AppLogStore.http("$label -> ${request.method} $safeUrl")

        return try {
            val response = chain.proceed(request)
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            AppLogStore.http(
                "$label <- HTTP ${response.code} ${request.method} $safeUrl (${elapsedMs}ms)"
            )
            response
        } catch (e: IOException) {
            logFailure(label, request.method, safeUrl, startedAt, e)
            throw e
        } catch (e: RuntimeException) {
            logFailure(label, request.method, safeUrl, startedAt, e)
            throw e
        }
    }

    private fun logFailure(
        label: String,
        method: String,
        safeUrl: String,
        startedAt: Long,
        throwable: Throwable
    ) {
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        AppLogStore.error(
            "$label !! ${throwable.javaClass.simpleName} $method $safeUrl (${elapsedMs}ms): ${throwable.message}",
            throwable
        )
    }
}
