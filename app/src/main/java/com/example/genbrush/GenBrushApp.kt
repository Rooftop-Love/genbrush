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
import com.example.genbrush.data.provider.ProviderRegistry
import com.example.genbrush.data.provider.agnes.AgnesApi
import com.example.genbrush.data.provider.agnes.AgnesProvider
import com.example.genbrush.data.provider.dashscope.DashScopeApi
import com.example.genbrush.data.provider.dashscope.DashScopeProvider
import com.example.genbrush.data.repository.GenerationRepository
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class GenBrushApp : Application() {

    lateinit var repository: GenerationRepository
        private set
    lateinit var prefs: PreferencesManager
        private set
    lateinit var providerRegistry: ProviderRegistry
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
            // 同步图像生成（如 DashScope 或 Agnes 4K 档位）可能超过一分钟，避免客户端提前判定超时。
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .callTimeout(360, TimeUnit.SECONDS)
            .addInterceptor(NetworkLoggingInterceptor())
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
        val imageStore = ImageStore(this)

        // 图像生成提供商注册表：新增提供商时在这里构造并注册
        providerRegistry = ProviderRegistry(
            listOf(
                DashScopeProvider(DashScopeApi(okHttpClient, gson)),
                AgnesProvider(AgnesApi(okHttpClient, gson))
            )
        )

        repository = GenerationRepository(providerRegistry, imageStore, prefs)

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
