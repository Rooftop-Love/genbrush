package com.example.genbrush

import com.example.genbrush.data.provider.agnes.AgnesDefinition
import com.example.genbrush.data.provider.agnes.ApiError
import com.example.genbrush.data.provider.agnes.ExtraBody
import com.example.genbrush.data.provider.agnes.GenerationRequest
import com.example.genbrush.data.provider.agnes.ImageApiResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgnesApiModelsTest {

    @Test
    fun textToImageRequestUsesTierSizeAndRatio() {
        val request = GenerationRequest(
            model = "agnes-image-2.5-flash",
            prompt = "a test prompt",
            size = "2K",
            ratio = "16:9",
            extra_body = ExtraBody(response_format = GenerationRequest.RESPONSE_FORMAT_URL)
        )

        val json = Gson().toJson(request)

        assertTrue(json.contains("\"model\":\"agnes-image-2.5-flash\""))
        assertTrue(json.contains("\"prompt\":\"a test prompt\""))
        assertTrue(json.contains("\"size\":\"2K\""))
        assertTrue(json.contains("\"ratio\":\"16:9\""))
        // response_format 必须位于 extra_body 内（顶层不生效）
        assertTrue(json.contains("\"extra_body\":{\"response_format\":\"url\"}"))
        assertFalse(json.contains("\"response_format\":\"url\",\""))
        // 表外不发送的参数不应序列化
        assertFalse(json.contains("\"image\""))
        assertFalse(json.contains("\"negative_prompt\""))
        assertFalse(json.contains("\"n\":"))
    }

    @Test
    fun exactSizeRequestOmitsRatio() {
        val request = GenerationRequest(
            model = "agnes-image-2.0-flash",
            prompt = "a test prompt",
            size = "1024x768",
            extra_body = ExtraBody(response_format = GenerationRequest.RESPONSE_FORMAT_URL)
        )

        val json = Gson().toJson(request)

        assertTrue(json.contains("\"size\":\"1024x768\""))
        assertFalse(json.contains("\"ratio\""))
    }

    @Test
    fun imageEditRequestPutsDataUriInExtraBody() {
        val request = GenerationRequest(
            model = "agnes-image-2.5-flash",
            prompt = "make it sunset",
            size = "1K",
            ratio = "1:1",
            extra_body = ExtraBody(
                image = listOf("data:image/jpeg;base64,AAAAIGZ0eXBpc29t"),
                response_format = GenerationRequest.RESPONSE_FORMAT_URL
            )
        )

        val json = Gson().toJson(request)

        assertTrue(
            json.contains(
                "\"extra_body\":{\"image\":[\"data:image/jpeg;base64,AAAAIGZ0eXBpc29t\"]," +
                    "\"response_format\":\"url\"}"
            )
        )
    }

    @Test
    fun apiSizeParamsMapTiersFor25And21() {
        // 2.5/2.1：内部 "宽*高" 反查为官方推荐的档位值+比例
        assertEquals("2K", AgnesDefinition.apiSizeParams("agnes-image-2.5-flash", "2624*1472").size)
        assertEquals(
            "16:9",
            AgnesDefinition.apiSizeParams("agnes-image-2.5-flash", "2624*1472").ratio
        )
        assertEquals("1K", AgnesDefinition.apiSizeParams("agnes-image-2.1-flash", "736*1312").size)
        assertEquals(
            "9:16",
            AgnesDefinition.apiSizeParams("agnes-image-2.1-flash", "736*1312").ratio
        )
        assertEquals(
            "4K",
            AgnesDefinition.apiSizeParams("agnes-image-2.5-flash", "4096*4096").size
        )
    }

    @Test
    fun apiSizeParamsFallBackToExactDimensions() {
        // 表外尺寸回退为精确 WxH 写法（API 兼容），不带 ratio
        val fallback = AgnesDefinition.apiSizeParams("agnes-image-2.5-flash", "1920*1080")
        assertEquals("1920x1080", fallback.size)
        assertNull(fallback.ratio)
        // 2.0 无档位表，始终精确尺寸
        val exact = AgnesDefinition.apiSizeParams("agnes-image-2.0-flash", "768*1024")
        assertEquals("768x1024", exact.size)
        assertNull(exact.ratio)
        // 非法值回退到默认档位
        assertEquals("1024x1024", AgnesDefinition.apiSizeParams("agnes-image-2.5-flash", "unknown").size)
    }

    @Test
    fun responseShapeParsesUrlAndB64Variants() {
        val urlJson = """
            {
              "created": 1723595647,
              "data": [
                {
                  "url": "https://storage.googleapis.com/agnes-image/xxxx.png",
                  "revised_prompt": "a revised prompt"
                }
              ]
            }
        """.trimIndent()

        val urlResponse = Gson().fromJson(urlJson, ImageApiResponse::class.java)

        assertEquals(
            "https://storage.googleapis.com/agnes-image/xxxx.png",
            urlResponse.data?.firstOrNull()?.url
        )
        assertEquals("a revised prompt", urlResponse.data?.firstOrNull()?.revised_prompt)
        assertNull(urlResponse.error)

        val b64Json = """
            {
              "created": 1723595647,
              "data": [
                { "b64_json": "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1" }
              ]
            }
        """.trimIndent()

        val b64Response = Gson().fromJson(b64Json, ImageApiResponse::class.java)

        assertEquals(
            "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1",
            b64Response.data?.firstOrNull()?.b64_json
        )
    }

    @Test
    fun errorBodyParsesOpenAiStyleError() {
        val json = """
            {
              "error": {
                "message": "Invalid API key provided",
                "type": "invalid_request_error",
                "code": "invalid_api_key"
              }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, ImageApiResponse::class.java)

        val error: ApiError? = response.error
        assertEquals("invalid_api_key", error?.code)
        assertEquals("invalid_request_error", error?.type)
        assertEquals("Invalid API key provided", error?.message)
    }

    @Test
    fun supportedSizesCoverModelFamilies() {
        // 2.5：1K+2K 各 5 个常用比例 + 4K 3 个 = 13
        assertEquals(13, AgnesDefinition.supportedSizes("agnes-image-2.5-flash").size)
        // 2.1：与 2.5 同表但无 4K = 10
        assertEquals(10, AgnesDefinition.supportedSizes("agnes-image-2.1-flash").size)
        // 2.0：官方示例精确尺寸 = 3
        assertEquals(3, AgnesDefinition.supportedSizes("agnes-image-2.0-flash").size)
        // 未知/空模型回退到 2.5 档位
        assertEquals(
            AgnesDefinition.supportedSizes("agnes-image-2.5-flash"),
            AgnesDefinition.supportedSizes(null)
        )
        assertEquals(
            AgnesDefinition.supportedSizes("agnes-image-2.5-flash"),
            AgnesDefinition.supportedSizes("custom-model")
        )
    }
}
