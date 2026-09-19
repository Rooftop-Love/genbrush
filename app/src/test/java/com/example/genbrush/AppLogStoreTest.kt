package com.example.genbrush

import com.example.genbrush.data.local.AppLogStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogStoreTest {

    @Test
    fun `sanitize redacts authorization bearer tokens`() {
        val sanitized = AppLogStore.sanitizeForTest(
            "Authorization: Bearer sk-secret-token GET https://example.com"
        )

        assertEquals(
            "Authorization: Bearer [REDACTED] GET https://example.com",
            sanitized
        )
    }

    @Test
    fun `sanitize redacts api and oauth tokens`() {
        val sanitized = AppLogStore.sanitizeForTest(
            """{"api_key":"dashscope-secret","access_token":"oauth-secret"}"""
        )

        assertTrue(sanitized.contains(""""api_key":"[REDACTED]""""))
        assertTrue(sanitized.contains(""""access_token":"[REDACTED]""""))
        assertFalse(sanitized.contains("dashscope-secret"))
        assertFalse(sanitized.contains("oauth-secret"))
    }
}
