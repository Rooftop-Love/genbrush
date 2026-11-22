package com.example.genbrush

import com.example.genbrush.data.provider.SizeOption
import com.example.genbrush.ui.components.SizeOptionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SizeOptionParserTest {

    @Test
    fun `parses dashscope option without tier`() {
        val option = SizeOption("2688*1536", "2688x1536 (16:9)")
        val parsed = SizeOptionParser.parse(option)

        assertEquals(2688, parsed.width)
        assertEquals(1536, parsed.height)
        assertEquals("16:9", parsed.ratioLabel)
        assertNull(parsed.tierLabel)
        assertEquals("2688×1536", parsed.resolutionText)
        assertEquals(1.75f, parsed.aspectRatio, 0.001f)
    }

    @Test
    fun `parses agnes 2_5 option with tier and ratio`() {
        val option = SizeOption("2624*1472", "2624x1472 (2K · 16:9)")
        val parsed = SizeOptionParser.parse(option)

        assertEquals(2624, parsed.width)
        assertEquals(1472, parsed.height)
        assertEquals("16:9", parsed.ratioLabel)
        assertEquals("2K", parsed.tierLabel)
        assertEquals("2624×1472", parsed.resolutionText)
    }

    @Test
    fun `availableTiers preserves distinct order`() {
        val options = listOf(
            SizeOption("1024*1024", "1024x1024 (1K · 1:1)"),
            SizeOption("1312*736", "1312x736 (1K · 16:9)"),
            SizeOption("2048*2048", "2048x2048 (2K · 1:1)"),
            SizeOption("4096*4096", "4096x4096 (4K · 1:1)")
        )

        assertEquals(listOf("1K", "2K", "4K"), SizeOptionParser.availableTiers(options))
    }

    @Test
    fun `compactLabel formats with and without tier`() {
        val tiered = listOf(
            SizeOption("2048*2048", "2048x2048 (2K · 1:1)")
        )
        val untiered = listOf(
            SizeOption("1728*2368", "1728x2368 (3:4)")
        )

        assertEquals("1:1 · 2K", SizeOptionParser.compactLabel("2048*2048", tiered))
        assertEquals("3:4", SizeOptionParser.compactLabel("1728*2368", untiered))
        assertEquals("16:9", SizeOptionParser.compactLabel("1920*1080", emptyList()))
    }

    @Test
    fun `parseAspectRatio handles malformed inputs and clamps bounds`() {
        assertEquals(1f, SizeOptionParser.parseAspectRatio(null), 0.001f)
        assertEquals(1f, SizeOptionParser.parseAspectRatio(""), 0.001f)
        assertEquals(1f, SizeOptionParser.parseAspectRatio("invalid"), 0.001f)
        assertEquals(1.78f, SizeOptionParser.parseAspectRatio("4000*1000"), 0.001f)
        assertEquals(0.56f, SizeOptionParser.parseAspectRatio("1000*4000"), 0.001f)
        assertEquals(0.75f, SizeOptionParser.parseAspectRatio("768x1024"), 0.001f)
    }
}
