package com.example.genbrush

import com.example.genbrush.data.local.CatalogModel
import com.example.genbrush.data.local.ModelCapability.EDIT
import com.example.genbrush.data.local.ModelCapability.GENERATION
import com.example.genbrush.data.local.ModelCatalog
import com.example.genbrush.data.local.ModelCatalogRules
import com.example.genbrush.data.local.ModelSeed
import com.example.genbrush.data.local.ModelSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖模型清单缓存的种子、迁移、上游合并、开关与编解码规则。 */
class ModelCatalogTest {

    private val seed = ModelSeed(
        generationModels = listOf("model-a", "model-b", "shared"),
        editModels = listOf("shared", "edit-only"),
        supportsImageEdit = true
    )

    @Test
    fun `内置预设构建时同名模型合并能力且默认启用`() {
        val catalog = ModelCatalogRules.buildInitial(seed, emptySet(), emptySet(), emptyList(), emptyList())

        assertEquals(listOf("model-a", "model-b", "shared", "edit-only"), catalog.models.map { it.id })
        val shared = catalog.models.first { it.id == "shared" }
        assertTrue(shared.supportsGeneration)
        assertTrue(shared.supportsEdit)
        assertTrue(shared.enabledGeneration)
        assertTrue(shared.enabledEdit)
        assertEquals(ModelSource.BUILTIN, shared.source)
        assertEquals(0L, catalog.lastRefreshedAt)
    }

    @Test
    fun `旧版禁用与自定义配置迁移进清单`() {
        val catalog = ModelCatalogRules.buildInitial(
            seed,
            disabledGeneration = setOf("model-a"),
            disabledEdit = setOf("shared"),
            customGeneration = listOf("custom-gen"),
            customEdit = listOf("custom-edit")
        )

        assertFalse(catalog.models.first { it.id == "model-a" }.enabledGeneration)
        assertFalse(catalog.models.first { it.id == "shared" }.enabledEdit)
        assertTrue(catalog.models.first { it.id == "model-b" }.enabledGeneration)
        val customGen = catalog.models.first { it.id == "custom-gen" }
        assertEquals(ModelSource.CUSTOM, customGen.source)
        assertTrue(customGen.supportsGeneration)
        assertFalse(customGen.supportsEdit)
        assertEquals(ModelSource.CUSTOM, catalog.models.first { it.id == "custom-edit" }.source)
    }

    @Test
    fun `不支持图像编辑时忽略编辑预设与自定义编辑模型`() {
        val noEdit = seed.copy(supportsImageEdit = false)
        val catalog = ModelCatalogRules.buildInitial(
            noEdit, emptySet(), emptySet(), listOf("custom-gen"), listOf("custom-edit")
        )

        assertTrue(catalog.models.none { it.id == "edit-only" })
        assertTrue(catalog.models.none { it.id == "custom-edit" })
        assertFalse(catalog.models.first { it.id == "shared" }.supportsEdit)
    }

    @Test
    fun `版本升级补齐新增内置项但不重置用户开关`() {
        var catalog = ModelCatalogRules.buildInitial(seed, emptySet(), emptySet(), emptyList(), emptyList())
        catalog = ModelCatalogRules.setEnabled(catalog, "model-a", GENERATION, false)
        val upgraded = ModelCatalogRules.ensureSeed(
            catalog,
            seed.copy(generationModels = seed.generationModels + "model-new")
        )

        assertFalse(upgraded.models.first { it.id == "model-a" }.enabledGeneration)
        assertTrue(upgraded.models.first { it.id == "model-new" }.enabledGeneration)
        assertEquals(ModelSource.BUILTIN, upgraded.models.first { it.id == "model-new" }.source)
    }

    @Test
    fun `内置预设新增能力时保留原有启用状态`() {
        val catalog = ModelCatalogRules.buildInitial(seed, emptySet(), emptySet(), emptyList(), emptyList())
        val changed = ModelCatalogRules.setEnabled(catalog, "model-a", GENERATION, false)
        val upgraded = ModelCatalogRules.ensureSeed(changed, seed.copy(editModels = seed.editModels + "model-a"))

        val model = upgraded.models.first { it.id == "model-a" }
        assertTrue(model.supportsEdit)
        assertTrue(model.enabledEdit)
        assertFalse(model.enabledGeneration)
    }

    @Test
    fun `上游刷新只新增条目且不覆盖已有开关与来源`() {
        var catalog = ModelCatalogRules.buildInitial(seed, emptySet(), emptySet(), emptyList(), emptyList())
        catalog = ModelCatalogRules.setEnabled(catalog, "model-a", GENERATION, false)

        val result = ModelCatalogRules.mergeRemote(
            catalog,
            listOf("model-a", "remote-1", "remote-2"),
            supportsEdit = true,
            refreshedAt = 1234L
        )

        assertEquals(2, result.added)
        assertEquals(1234L, result.catalog.lastRefreshedAt)
        val existing = result.catalog.models.first { it.id == "model-a" }
        assertEquals(ModelSource.BUILTIN, existing.source)
        assertFalse(existing.enabledGeneration)
        val remote = result.catalog.models.first { it.id == "remote-1" }
        assertEquals(ModelSource.REMOTE, remote.source)
        assertTrue(remote.enabledGeneration)
        assertTrue(remote.enabledEdit)
    }

    @Test
    fun `上游刷新去重去空白且无新增时也更新时间戳`() {
        val catalog = ModelCatalogRules.buildInitial(seed, emptySet(), emptySet(), emptyList(), emptyList())
        val result = ModelCatalogRules.mergeRemote(
            catalog,
            listOf("  ", "model-a", "model-a", " remote-1 ", "remote-1"),
            supportsEdit = false,
            refreshedAt = 99L
        )

        assertEquals(1, result.added)
        assertEquals(5, result.catalog.models.size)
        assertFalse(result.catalog.models.first { it.id == "remote-1" }.supportsEdit)
        assertEquals(100L, ModelCatalogRules.mergeRemote(result.catalog, listOf("model-a"), false, 100L).catalog.lastRefreshedAt)
    }

    @Test
    fun `生成与编辑开关彼此独立`() {
        val catalog = ModelCatalogRules.buildInitial(seed, emptySet(), emptySet(), emptyList(), emptyList())
        val changed = ModelCatalogRules.setEnabled(catalog, "shared", GENERATION, false)

        assertFalse(changed.models.first { it.id == "shared" }.enabledGeneration)
        assertTrue(changed.models.first { it.id == "shared" }.enabledEdit)
        assertEquals(
            listOf("model-a", "model-b"),
            ModelCatalogRules.visibleIds(changed, GENERATION)
        )
        assertEquals(
            listOf("shared", "edit-only"),
            ModelCatalogRules.visibleIds(changed, EDIT)
        )
    }

    @Test
    fun `自定义模型同名可分别加入两组但重复添加被拒绝`() {
        val catalog = ModelCatalogRules.buildInitial(seed, emptySet(), emptySet(), emptyList(), emptyList())
        val first = ModelCatalogRules.addCustom(catalog, "  my-model ", GENERATION)
        assertTrue(first.added)
        val duplicate = ModelCatalogRules.addCustom(first.catalog, "my-model", GENERATION)
        assertFalse(duplicate.added)
        val second = ModelCatalogRules.addCustom(first.catalog, "my-model", EDIT)

        assertTrue(second.added)
        val model = second.catalog.models.first { it.id == "my-model" }
        assertTrue(model.supportsGeneration)
        assertTrue(model.supportsEdit)
        assertEquals(5, second.catalog.models.size)
        assertFalse(ModelCatalogRules.addCustom(catalog, "   ", GENERATION).added)
    }

    @Test
    fun `删除自定义模型只移除对应场景且不影响内置条目`() {
        var catalog = ModelCatalogRules.buildInitial(seed, emptySet(), emptySet(), emptyList(), emptyList())
        catalog = ModelCatalogRules.addCustom(catalog, "my-model", GENERATION).catalog
        catalog = ModelCatalogRules.addCustom(catalog, "my-model", EDIT).catalog

        val afterGenRemoval = ModelCatalogRules.removeCustom(catalog, "my-model", GENERATION)
        val model = afterGenRemoval.models.first { it.id == "my-model" }
        assertFalse(model.supportsGeneration)
        assertTrue(model.supportsEdit)

        val fullyRemoved = ModelCatalogRules.removeCustom(afterGenRemoval, "my-model", EDIT)
        assertTrue(fullyRemoved.models.none { it.id == "my-model" })
        assertTrue(ModelCatalogRules.removeCustom(catalog, "model-a", GENERATION).models.any { it.id == "model-a" })
    }

    @Test
    fun `可见列表顺序与清单顺序一致`() {
        var catalog = ModelCatalogRules.buildInitial(seed, emptySet(), emptySet(), emptyList(), emptyList())
        catalog = ModelCatalogRules.addCustom(catalog, "zzz", GENERATION).catalog
        catalog = ModelCatalogRules.setEnabled(catalog, "model-b", GENERATION, false)

        assertEquals(listOf("model-a", "shared", "zzz"), ModelCatalogRules.visibleIds(catalog, GENERATION))
        assertEquals(4, ModelCatalogRules.entriesFor(catalog, GENERATION).size)
    }

    @Test
    fun `编解码往返保留全部字段`() {
        var catalog = ModelCatalogRules.buildInitial(seed, setOf("model-a"), emptySet(), listOf("custom"), emptyList())
        catalog = ModelCatalogRules.mergeRemote(catalog, listOf("remote-1"), true, 888L).catalog

        val restored = ModelCatalog.decode(catalog.encode())

        assertEquals(catalog.models, restored.models)
        assertEquals(888L, restored.lastRefreshedAt)
    }

    @Test
    fun `空值损坏 JSON 与缺字段都能安全解码`() {
        assertEquals(emptyList<CatalogModel>(), ModelCatalog.decode(null).models)
        assertEquals(emptyList<CatalogModel>(), ModelCatalog.decode("").models)
        assertEquals(emptyList<CatalogModel>(), ModelCatalog.decode("{broken").models)

        val partial = ModelCatalog.decode(
            """{"version":1,"models":[{"id":"only-id","source":"REMOTE"}]}"""
        )
        assertEquals(1, partial.models.size)
        val model = partial.models.first()
        assertEquals(ModelSource.REMOTE, model.source)
        assertTrue(model.supportsGeneration)
        assertFalse(model.supportsEdit)
        assertTrue(model.enabledGeneration)

        val duplicated = ModelCatalog.decode(
            """{"models":[{"id":"x"},{"id":"x"},{"id":"  "}]}"""
        )
        assertEquals(1, duplicated.models.size)
        assertEquals(0L, duplicated.lastRefreshedAt)
    }
}
