package com.example.genbrush

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import okio.Path.Companion.toPath
import com.example.genbrush.data.local.AppLogStore
import com.example.genbrush.data.local.ImageStore
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.network.NetworkLoggingInterceptor
import com.example.genbrush.data.remote.DashScopeApi
import com.example.genbrush.data.remote.StableDiffusionApi
import com.example.genbrush.data.repository.GenerationRepository
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class GenBrushApp : Application() {

    lateinit var repository: GenerationRepository
        private set
    lateinit var prefs: PreferencesManager
        private set
    lateinit var sdApi: StableDiffusionApi
        private set

    private val _language = MutableStateFlow("")
    val language: StateFlow<String> = _language.asStateFlow()

    fun setLanguage(lang: String) {
        prefs.language = lang
        _language.value = lang
    }

    override fun onCreate() {
        super.onCreate()

        AppLogStore.initialize(this)
        prefs = PreferencesManager(this)

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            // DashScope 的同步图像生成可能超过一分钟，避免被客户端提前判定为网络异常。
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .callTimeout(360, TimeUnit.SECONDS)
            .addInterceptor(NetworkLoggingInterceptor("DashScope"))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        redactHeader("Authorization")
                        redactHeader("X-API-Key")
                    })
                }
            }
            .build()

        // SD WebUI 专用客户端，超时 300 秒（Flux 模型生成较慢）
        // 信任所有证书，适配 FRP 内网穿透的自签名证书
        val trustAllCerts = arrayOf<X509TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        val sdOkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0])
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(NetworkLoggingInterceptor("SD WebUI"))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        redactHeader("Authorization")
                        redactHeader("X-API-Key")
                    })
                }
            }
            .build()

        val gson = GsonBuilder().create()
        val dashScopeApi = DashScopeApi(okHttpClient, gson)
        sdApi = StableDiffusionApi(sdOkHttpClient, gson)
        val imageStore = ImageStore(this)

        repository = GenerationRepository(dashScopeApi, sdApi, imageStore, prefs)

        _language.value = prefs.language

        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory())
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, 0.25)
                        .build()
                }
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                        .maxSizePercent(0.02)
                        .build()
                }
                .build()
        }
    }
}
