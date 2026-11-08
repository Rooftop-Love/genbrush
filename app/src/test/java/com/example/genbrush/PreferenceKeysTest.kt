package com.example.genbrush

import com.example.genbrush.data.local.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖 v1.1 全局键 → v1.2 提供商命名空间的迁移，以及 ZIP 备份的导入合并规则。 */
class PreferenceKeysTest {

    @Test
    fun `旧版备份只有顶层键时映射到当前提供商命名空间`() {
        val settings = mapOf(
            "api_key" to "sk-old",
            "provider_id" to "dashscope",
            "default_model" to "qwen-image-max",
            "default_size" to "1024*1024",
            "language" to "zh",
            "custom_gen_models" to "a|||b"
        )

        val resolved = PreferenceKeys.resolveImported(settings, "dashscope")

        assertEquals("dashscope", resolved["provider_id"])
        assertEquals("sk-old", resolved["api_key_dashscope"])
        assertEquals("qwen-image-max", resolved["default_model_dashscope"])
        assertEquals("1024*1024", resolved["default_size_dashscope"])
        assertEquals("zh", resolved["language"])
        assertEquals("a|||b", resolved["custom_gen_models_dashscope"])
        assertEquals(6, resolved.size)
    }

    @Test
    fun `命名空间键覆盖顶层键派生出的值`() {
        val settings = mapOf(
            "api_key" to "old-key",
            "api_key_dashscope" to "dash-key",
            "api_key_agnes" to "agnes-key"
        )

        val resolved = PreferenceKeys.resolveImported(settings, "dashscope")

        assertEquals("dash-key", resolved["api_key_dashscope"])
        assertEquals("agnes-key", resolved["api_key_agnes"])
    }

    @Test
    fun `备份指定 provider_id 时顶层键落到该提供商`() {
        val settings = mapOf("provider_id" to "agnes", "api_key" to "k", "default_model" to "m")

        val resolved = PreferenceKeys.resolveImported(settings, "dashscope")

        assertEquals("agnes", resolved["provider_id"])
        assertEquals("k", resolved["api_key_agnes"])
        assertEquals("m", resolved["default_model_agnes"])
        assertFalse(resolved.containsKey("api_key_dashscope"))
    }

    @Test
    fun `空字符串与未知键不会写入偏好`() {
        val settings = mapOf(
            "api_key" to "",
            "language" to "",
            "unknown_key" to "x",
            "default_model" to "m"
        )

        val resolved = PreferenceKeys.resolveImported(settings, "dashscope")

        assertEquals("m", resolved["default_model_dashscope"])
        assertFalse(resolved.containsKey("api_key_dashscope"))
        assertFalse(resolved.containsKey("language"))
        assertFalse(resolved.containsKey("unknown_key"))
    }

    @Test
    fun `模型列表中的空段与多余分隔符会被规范化`() {
        val settings = mapOf(
            "disabled_gen_models" to "a||||||b|||",
            "custom_edit_models" to "   "
        )

        val resolved = PreferenceKeys.resolveImported(settings, "dashscope")

        assertEquals("a|||b", resolved["disabled_gen_models_dashscope"])
        assertEquals("", resolved["custom_edit_models_dashscope"])
    }

    @Test
    fun `provider_id 为空时保留当前提供商`() {
        val settings = mapOf("provider_id" to "", "api_key" to "k")

        val resolved = PreferenceKeys.resolveImported(settings, "dashscope")

        assertFalse(resolved.containsKey("provider_id"))
        assertEquals("k", resolved["api_key_dashscope"])
    }

    @Test
    fun `导出格式回灌后各提供商配置保持不变`() {
        val exported = mapOf(
            "api_key" to "k",
            "provider_id" to "agnes",
            "default_model" to "m",
            "default_size" to "s",
            "language" to "zh",
            "disabled_gen_models" to "",
            "disabled_edit_models" to "",
            "custom_gen_models" to "x",
            "custom_edit_models" to "",
            "api_key_dashscope" to "dash-k",
            "api_key_agnes" to "agnes-k"
        )

        val resolved = PreferenceKeys.resolveImported(exported, "dashscope")

        assertEquals("agnes", resolved["provider_id"])
        assertEquals("dash-k", resolved["api_key_dashscope"])
        assertEquals("agnes-k", resolved["api_key_agnes"])
        assertEquals("m", resolved["default_model_agnes"])
        assertEquals("x", resolved["custom_gen_models_agnes"])
    }

    @Test
    fun `命名空间键优先于旧全局键`() {
        assertEquals(
            "scoped",
            PreferenceKeys.resolveRead("scoped", "legacy", "dashscope")
        )
        assertEquals(
            "scoped",
            PreferenceKeys.resolveRead("scoped", "legacy", "agnes")
        )
    }

    @Test
    fun `只有默认提供商才回退到旧全局键`() {
        assertEquals("legacy", PreferenceKeys.resolveRead(null, "legacy", "dashscope"))
        assertNull(PreferenceKeys.resolveRead(null, "legacy", "agnes"))
        assertNull(PreferenceKeys.resolveRead(null, null, "dashscope"))
    }

    @Test
    fun `空白视为未配置时继续回退`() {
        assertEquals(
            "legacy",
            PreferenceKeys.resolveRead("   ", "legacy", "dashscope", blankIsMissing = true)
        )
        assertEquals(
            "   ",
            PreferenceKeys.resolveRead("   ", "legacy", "dashscope")
        )
        assertNull(
            PreferenceKeys.resolveRead("  ", "   ", "dashscope", blankIsMissing = true)
        )
    }

    @Test
    fun `模型列表解析与拼接可逆且过滤空段`() {
        val raw = "a|||b|||c"
        assertEquals(listOf("a", "b", "c"), PreferenceKeys.parseModels(raw))
        assertEquals(raw, PreferenceKeys.joinModels(PreferenceKeys.parseModels(raw)))
        assertEquals(emptyList<String>(), PreferenceKeys.parseModels(""))
        assertEquals(emptyList<String>(), PreferenceKeys.parseModels(null))
        assertEquals("x", PreferenceKeys.joinModels(listOf("", "  ", "x")))
    }

    @Test
    fun `只把七类前缀识别为提供商命名空间键`() {
        assertTrue(PreferenceKeys.isProviderScoped("api_key_dashscope"))
        assertTrue(PreferenceKeys.isProviderScoped("custom_edit_models_agnes"))
        assertFalse(PreferenceKeys.isProviderScoped("api_key"))
        assertFalse(PreferenceKeys.isProviderScoped("language"))
        assertFalse(PreferenceKeys.isProviderScoped("backend_type"))
    }
}
