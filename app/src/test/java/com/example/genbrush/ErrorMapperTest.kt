package com.example.genbrush

import com.example.genbrush.data.provider.ProviderException
import com.example.genbrush.data.provider.agnes.AgnesException
import com.example.genbrush.data.provider.dashscope.DashScopeException
import com.example.genbrush.ui.common.mapError
import com.example.genbrush.ui.localization.AppStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMapperTest {

    @Test
    fun `DashScopeException 应展示错误码和原始消息`() {
        val ex = DashScopeException("InvalidParameter", "prompt is too long")
        val result = mapError(ex, AppStrings.ZH)

        assertEquals("[InvalidParameter] prompt is too long", result)
    }

    @Test
    fun `DashScopeException 含 Throttling 也应展示原始 API 错误而非通用消息`() {
        val ex = DashScopeException("Throttling.RateQuota", "Requests rate limit exceeded")
        val result = mapError(ex, AppStrings.ZH)

        assertTrue("应包含错误码", result.contains("Throttling.RateQuota"))
        assertTrue("应包含原始消息", result.contains("Requests rate limit exceeded"))
    }

    @Test
    fun `DashScopeException 含 DataInspectionFailed 也应展示原始 API 错误`() {
        val ex = DashScopeException("DataInspectionFailed", "Input data may contain inappropriate content")
        val result = mapError(ex, AppStrings.ZH)

        assertTrue("应包含错误码", result.contains("DataInspectionFailed"))
        assertTrue("应包含原始消息", result.contains("inappropriate content"))
    }

    @Test
    fun `DashScopeException 含 InvalidApiKey 也应展示原始 API 错误`() {
        val ex = DashScopeException("InvalidApiKey", "The API key is invalid")
        val result = mapError(ex, AppStrings.ZH)

        assertEquals("[InvalidApiKey] The API key is invalid", result)
    }

    @Test
    fun `DashScopeException 服务器错误应展示原始信息`() {
        val ex = DashScopeException("InternalError", "An internal server error occurred")
        val result = mapError(ex, AppStrings.ZH)

        assertEquals("[InternalError] An internal server error occurred", result)
    }

    @Test
    fun `ProviderException 标记 isContentSafety 时应映射为内容安全提示`() {
        val ex = ProviderException("451", "blocked by moderation", isContentSafety = true)
        val result = mapError(ex, AppStrings.ZH)

        assertEquals(AppStrings.ZH.errContentSafety, result)
    }

    @Test
    fun `AgnesException 含内容安全关键词应映射为内容安全提示`() {
        val ex = AgnesException("content_policy_violation", "Your request was rejected as a result of our content policy.")
        val result = mapError(ex, AppStrings.ZH)

        assertEquals(AppStrings.ZH.errContentSafety, result)
    }

    @Test
    fun `普通 Exception 含网络关键词应映射为友好提示`() {
        val ex = Exception("connect timed out")
        val result = mapError(ex, AppStrings.ZH)

        assertTrue("网络错误应展示友好提示", result.contains("网络错误"))
    }

    @Test
    fun `无消息的 Throwable 应展示未知错误`() {
        val ex = Exception()
        val result = mapError(ex, AppStrings.ZH)

        assertEquals("未知错误", result)
    }
}
