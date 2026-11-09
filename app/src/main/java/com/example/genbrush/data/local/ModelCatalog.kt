package com.example.genbrush.data.local

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 模型清单中每个条目的来源。
 * - BUILTIN：应用内置的预设模型，版本升级时自动补齐；
 * - REMOTE：手动从上游 /models 拉取到的模型；
 * - CUSTOM：用户手动添加的模型。
 */
enum class ModelSource { BUILTIN, REMOTE, CUSTOM }

/** 模型可用于的场景；同一个模型可同时支持两种场景，各自有独立开关。 */
enum class ModelCapability { GENERATION, EDIT }

data class CatalogModel(
    val id: String,
    val source: ModelSource,
    val supportsGeneration: Boolean,
    val supportsEdit: Boolean,
    val enabledGeneration: Boolean = true,
    val enabledEdit: Boolean = true
) {
    fun supports(capability: ModelCapability): Boolean = when (capability) {
        ModelCapability.GENERATION -> supportsGeneration
        ModelCapability.EDIT -> supportsEdit
    }

    fun isEnabled(capability: ModelCapability): Boolean = when (capability) {
        ModelCapability.GENERATION -> enabledGeneration
        ModelCapability.EDIT -> enabledEdit
    }

    fun withEnabled(capability: ModelCapability, enabled: Boolean): CatalogModel =
        when (capability) {
            ModelCapability.GENERATION -> copy(enabledGeneration = enabled)
            ModelCapability.EDIT -> copy(enabledEdit = enabled)
        }

    fun withCapability(capability: ModelCapability): CatalogModel =
        when (capability) {
            ModelCapability.GENERATION -> copy(supportsGeneration = true, enabledGeneration = true)
            ModelCapability.EDIT -> copy(supportsEdit = true, enabledEdit = true)
        }

    /** 移除一个场景的能力；若已无其它能力则返回 null（条目应被删除）。 */
    fun withoutCapability(capability: ModelCapability): CatalogModel? =
        when (capability) {
            ModelCapability.GENERATION ->
                if (supportsEdit) copy(supportsGeneration = false, enabledGeneration = false) else null
            ModelCapability.EDIT ->
                if (supportsGeneration) copy(supportsEdit = false, enabledEdit = false) else null
        }
}

/**
 * 某个提供商的完整模型清单（持久化缓存）。
 * 这是模型列表的唯一数据源：设置页负责编辑，生成页只读「已启用」的条目。
 */
data class ModelCatalog(
    val models: List<CatalogModel> = emptyList(),
    /** 最近一次成功从上游刷新模型列表的时间戳（毫秒），0 表示从未刷新。 */
    val lastRefreshedAt: Long = 0L
) {
    fun encode(): String {
        val items = JsonArray()
        models.forEach { model ->
            items.add(JsonObject().apply {
                addProperty("id", model.id)
                addProperty("source", model.source.name)
                addProperty("supportsGeneration", model.supportsGeneration)
                addProperty("supportsEdit", model.supportsEdit)
                addProperty("enabledGeneration", model.enabledGeneration)
                addProperty("enabledEdit", model.enabledEdit)
            })
        }
        return JsonObject().apply {
            addProperty("version", VERSION)
            addProperty("lastRefreshedAt", lastRefreshedAt)
            add("models", items)
        }.toString()
    }

    companion object {
        private const val VERSION = 1

        /** 兼容损坏 / 旧格式数据：解析失败时返回空清单，由调用方重新播种，不让应用崩溃。 */
        fun decode(raw: String?): ModelCatalog {
            if (raw.isNullOrBlank()) return ModelCatalog()
            return try {
                val root = JsonParser.parseString(raw).asJsonObject
                val items = root.getAsJsonArray("models") ?: JsonArray()
                val models = ArrayList<CatalogModel>(items.size())
                val seen = HashSet<String>()
                for (element in items) {
                    val obj = element.asJsonObject
                    val id = obj.string("id").trim()
                    if (id.isEmpty() || !seen.add(id)) continue
                    val source = try {
                        ModelSource.valueOf(obj.string("source"))
                    } catch (_: Exception) {
                        ModelSource.CUSTOM
                    }
                    models.add(
                        CatalogModel(
                            id = id,
                            source = source,
                            supportsGeneration = obj.boolean("supportsGeneration", true),
                            supportsEdit = obj.boolean("supportsEdit", false),
                            enabledGeneration = obj.boolean("enabledGeneration", true),
                            enabledEdit = obj.boolean("enabledEdit", true)
                        )
                    )
                }
                ModelCatalog(models, root.longValue("lastRefreshedAt"))
            } catch (_: Exception) {
                ModelCatalog()
            }
        }

        private fun JsonObject.string(key: String): String =
            runCatching { get(key)?.asString }.getOrNull().orEmpty()

        private fun JsonObject.boolean(key: String, fallback: Boolean): Boolean =
            runCatching { get(key)?.asBoolean }.getOrNull() ?: fallback

        private fun JsonObject.longValue(key: String): Long =
            runCatching { get(key)?.asLong }.getOrNull() ?: 0L
    }
}

/** 由 ProviderDefinition 派生的内置预设，保持纯 Kotlin 便于 JVM 测试。 */
data class ModelSeed(
    val generationModels: List<String>,
    val editModels: List<String>,
    val supportsImageEdit: Boolean = true
)

data class RemoteMergeResult(val catalog: ModelCatalog, val added: Int)

data class CustomModelResult(val catalog: ModelCatalog, val added: Boolean)

/**
 * 模型清单的合并 / 开关 / 查询规则。全部为纯函数，不触碰 Android 与持久化。
 *
 * 关键约定：
 * - 首次使用时以内置预设为种子，并把旧版 disabled / custom 配置迁移进来；
 * - 版本升级只补齐新增的内置项，绝不重置用户已调过的开关；
 * - 上游刷新只新增条目，不覆盖已有条目的启用状态，也不删除上游暂时缺席的条目。
 */
object ModelCatalogRules {

    fun buildInitial(
        seed: ModelSeed,
        disabledGeneration: Set<String>,
        disabledEdit: Set<String>,
        customGeneration: List<String>,
        customEdit: List<String>
    ): ModelCatalog {
        val entries = LinkedHashMap<String, CatalogModel>()
        seed.generationModels.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { id ->
            entries[id] = CatalogModel(
                id = id,
                source = ModelSource.BUILTIN,
                supportsGeneration = true,
                supportsEdit = false,
                enabledGeneration = id !in disabledGeneration
            )
        }
        val editIds = if (seed.supportsImageEdit) seed.editModels else emptyList()
        editIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { id ->
            val existing = entries[id]
            entries[id] = existing?.copy(supportsEdit = true, enabledEdit = id !in disabledEdit)
                ?: CatalogModel(
                    id = id,
                    source = ModelSource.BUILTIN,
                    supportsGeneration = false,
                    supportsEdit = true,
                    enabledEdit = id !in disabledEdit
                )
        }
        var catalog = ModelCatalog(entries.values.toList())
        customGeneration.forEach { catalog = addCustom(catalog, it, ModelCapability.GENERATION).catalog }
        if (seed.supportsImageEdit) {
            customEdit.forEach { catalog = addCustom(catalog, it, ModelCapability.EDIT).catalog }
        }
        return catalog
    }

    /** 用当前版本的内置预设补齐清单：新增项默认启用，已有项的开关保持不变。 */
    fun ensureSeed(catalog: ModelCatalog, seed: ModelSeed): ModelCatalog {
        var result = catalog
        seed.generationModels.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { id ->
            result = ensure(result, id, ModelCapability.GENERATION)
        }
        if (seed.supportsImageEdit) {
            seed.editModels.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { id ->
                result = ensure(result, id, ModelCapability.EDIT)
            }
        }
        return result
    }

    private fun ensure(catalog: ModelCatalog, id: String, capability: ModelCapability): ModelCatalog {
        val existing = catalog.models.firstOrNull { it.id == id }
        if (existing == null) {
            val entry = CatalogModel(
                id = id,
                source = ModelSource.BUILTIN,
                supportsGeneration = capability == ModelCapability.GENERATION,
                supportsEdit = capability == ModelCapability.EDIT
            )
            return catalog.copy(models = catalog.models + entry)
        }
        if (existing.supports(capability)) return catalog
        return catalog.copy(
            models = catalog.models.map { if (it.id == id) it.withCapability(capability) else it }
        )
    }

    /** 合并上游模型列表：只新增缺失的 id，保留已有条目的来源与开关。 */
    fun mergeRemote(
        catalog: ModelCatalog,
        remoteIds: List<String>,
        supportsEdit: Boolean,
        refreshedAt: Long
    ): RemoteMergeResult {
        val entries = LinkedHashMap<String, CatalogModel>()
        catalog.models.forEach { entries[it.id] = it }
        var added = 0
        remoteIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { id ->
            if (!entries.containsKey(id)) {
                entries[id] = CatalogModel(
                    id = id,
                    source = ModelSource.REMOTE,
                    supportsGeneration = true,
                    supportsEdit = supportsEdit,
                    enabledGeneration = true,
                    enabledEdit = supportsEdit
                )
                added++
            }
        }
        return RemoteMergeResult(ModelCatalog(entries.values.toList(), refreshedAt), added)
    }

    fun setEnabled(
        catalog: ModelCatalog,
        id: String,
        capability: ModelCapability,
        enabled: Boolean
    ): ModelCatalog = catalog.copy(
        models = catalog.models.map { model ->
            if (model.id == id && model.supports(capability)) model.withEnabled(capability, enabled) else model
        }
    )

    /** 添加自定义模型；同名条目已具备该场景能力时视为重复。同名可分别加入生成与编辑两组。 */
    fun addCustom(
        catalog: ModelCatalog,
        name: String,
        capability: ModelCapability
    ): CustomModelResult {
        val id = name.trim()
        if (id.isEmpty()) return CustomModelResult(catalog, false)
        val existing = catalog.models.firstOrNull { it.id == id }
        if (existing != null) {
            if (existing.supports(capability)) return CustomModelResult(catalog, false)
            return CustomModelResult(
                catalog.copy(
                    models = catalog.models.map { if (it.id == id) it.withCapability(capability) else it }
                ),
                true
            )
        }
        val isGeneration = capability == ModelCapability.GENERATION
        val entry = CatalogModel(
            id = id,
            source = ModelSource.CUSTOM,
            supportsGeneration = isGeneration,
            supportsEdit = !isGeneration,
            enabledGeneration = isGeneration,
            enabledEdit = !isGeneration
        )
        return CustomModelResult(catalog.copy(models = catalog.models + entry), true)
    }

    /** 删除自定义模型的某个场景；内置 / 上游条目不受影响。 */
    fun removeCustom(
        catalog: ModelCatalog,
        id: String,
        capability: ModelCapability
    ): ModelCatalog = catalog.copy(
        models = catalog.models.mapNotNull { model ->
            if (model.id == id && model.source == ModelSource.CUSTOM) {
                model.withoutCapability(capability)
            } else {
                model
            }
        }
    )

    fun entriesFor(catalog: ModelCatalog, capability: ModelCapability): List<CatalogModel> =
        catalog.models.filter { it.supports(capability) }

    fun visibleIds(catalog: ModelCatalog, capability: ModelCapability): List<String> =
        catalog.models.filter { it.supports(capability) && it.isEnabled(capability) }.map { it.id }
}
