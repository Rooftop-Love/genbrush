package com.example.genbrush

import com.example.genbrush.data.provider.dashscope.GenerationRequest
import com.example.genbrush.data.provider.dashscope.Input
import com.example.genbrush.data.provider.dashscope.Message
import com.example.genbrush.data.provider.dashscope.Parameters
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenImageApiModelsTest {

    @Test
    fun qwen30RequestUsesMultimodalConversationShape() {
        val request = GenerationRequest(
            model = "qwen-image-3.0-pro",
            input = Input(
                messages = listOf(
                    Message(
                        role = "user",
                        content = listOf(
                            com.example.genbrush.data.provider.dashscope.ContentItem(
                                text = "a test prompt"
                            )
                        )
                    )
                )
            ),
            parameters = Parameters(
                size = "2048*2048",
                prompt_extend_mode = "direct"
            )
        )

        val json = Gson().toJson(request)

        assertTrue(json.contains("\"model\":\"qwen-image-3.0-pro\""))
        assertTrue(json.contains("\"messages\""))
        assertTrue(json.contains("\"role\":\"user\""))
        assertTrue(json.contains("\"prompt_extend_mode\":\"direct\""))
        assertFalse(json.contains("\"prompt\":"))
    }

    @Test
    fun qwen30ResponseShapeParsesImageUrlAndUsage() {
        val json = """
            {
              "output": {
                "choices": [{
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": [{"image": "https://example.com/result.png"}]
                  }
                }]
              },
              "usage": {
                "output_width": 1024,
                "output_height": 1024,
                "output_image_count": 1
              },
              "request_id": "request-1"
            }
        """.trimIndent()

        val response = Gson().fromJson(
            json,
            com.example.genbrush.data.provider.dashscope.GenerationResponse::class.java
        )

        assertEquals(
            "https://example.com/result.png",
            response.output?.choices?.firstOrNull()?.message?.content?.firstOrNull()?.image
        )
        assertEquals(1024, response.usage?.output_width)
        assertEquals("request-1", response.request_id)
    }
}
