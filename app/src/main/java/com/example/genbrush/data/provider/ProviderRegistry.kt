package com.example.genbrush.data.provider

/**
 * 已注册的图像生成提供商集合。应用启动时在 GenBrushApp 中装配；
 * 运行期按 prefs 中保存的 provider id 解析当前提供商，未知 id 回退到第一个注册项。
 */
class ProviderRegistry(providers: List<ImageProvider>) {

    private val providerList: List<ImageProvider> = providers.toList()

    fun all(): List<ImageProvider> = providerList

    fun find(id: String): ImageProvider? = providerList.firstOrNull { it.definition.id == id }

    fun resolve(id: String): ImageProvider = find(id) ?: providerList.first()
}
