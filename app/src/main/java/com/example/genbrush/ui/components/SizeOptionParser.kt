package com.example.genbrush.ui.components

import com.example.genbrush.data.provider.SizeOption
import kotlin.math.abs

data class ParsedSizeOption(
    val value: String,
    val label: String,
    val width: Int,
    val height: Int,
    val aspectRatio: Float,
    val ratioLabel: String,
    val tierLabel: String?,
    val resolutionText: String
)

object SizeOptionParser {

    private val TIER_REGEX = Regex("""\b([1-4]K)\b""", RegexOption.IGNORE_CASE)
    private val RATIO_REGEX = Regex("""(\d+:\d+)""")
    private val DIMS_REGEX = Regex("""(\d+)\s*[*xX×]\s*(\d+)""")

    fun parse(option: SizeOption): ParsedSizeOption {
        val (w, h) = parseDimensions(option.value)
        val tier = TIER_REGEX.find(option.label)?.groupValues?.getOrNull(1)?.uppercase()
        val ratio = RATIO_REGEX.find(option.label)?.groupValues?.getOrNull(1)
            ?: simplifiedRatio(w, h)
        val aspect = if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 1f
        val resText = if (w > 0 && h > 0) "${w}×${h}" else option.value
        return ParsedSizeOption(
            value = option.value,
            label = option.label,
            width = w,
            height = h,
            aspectRatio = aspect,
            ratioLabel = ratio,
            tierLabel = tier,
            resolutionText = resText
        )
    }

    fun parseAll(options: List<SizeOption>): List<ParsedSizeOption> = options.map(::parse)

    fun availableTiers(options: List<SizeOption>): List<String> =
        options.mapNotNull { parse(it).tierLabel }.distinct()

    fun parseAspectRatio(
        size: String?,
        minRatio: Float = 0.56f,
        maxRatio: Float = 1.78f
    ): Float {
        if (size.isNullOrBlank()) return 1f
        val (w, h) = parseDimensions(size)
        if (w <= 0 || h <= 0) return 1f
        return (w.toFloat() / h.toFloat()).coerceIn(minRatio, maxRatio)
    }

    fun compactLabel(selectedSize: String, options: List<SizeOption>): String {
        val matched = options.firstOrNull { it.value == selectedSize }
        if (matched != null) {
            val parsed = parse(matched)
            return if (parsed.tierLabel != null) {
                "${parsed.ratioLabel} · ${parsed.tierLabel}"
            } else {
                parsed.ratioLabel
            }
        }
        val (w, h) = parseDimensions(selectedSize)
        return if (w > 0 && h > 0) simplifiedRatio(w, h) else selectedSize
    }

    fun parseDimensions(raw: String): Pair<Int, Int> {
        val match = DIMS_REGEX.find(raw) ?: return 0 to 0
        val w = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
        val h = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        return w to h
    }

    private fun simplifiedRatio(w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return "1:1"
        val ratio = w.toFloat() / h.toFloat()
        val common = listOf(
            1f to "1:1",
            16f / 9f to "16:9",
            9f / 16f to "9:16",
            4f / 3f to "4:3",
            3f / 4f to "3:4",
            3f / 2f to "3:2",
            2f / 3f to "2:3"
        )
        val best = common.minByOrNull { abs(it.first - ratio) }
        if (best != null && abs(best.first - ratio) < 0.04f) {
            return best.second
        }
        val divisor = gcd(w, h).coerceAtLeast(1)
        return "${w / divisor}:${h / divisor}"
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }
}
