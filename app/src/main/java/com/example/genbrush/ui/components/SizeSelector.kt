package com.example.genbrush.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.genbrush.data.remote.model.isQwenImage3Model
import com.example.genbrush.data.remote.model.isWanModel

data class SizeOption(val value: String, val label: String)

/**
 * SD WebUI 通用尺寸列表（SD WebUI 允许任意尺寸，这里给出常用档位）
 */
val SD_SIZE_OPTIONS = listOf(
    SizeOption("1024*1024", "1024x1024 (1:1)"),
    SizeOption("1280*720", "1280x720 (16:9)"),
    SizeOption("720*1280", "720x1280 (9:16)"),
    SizeOption("1024*768", "1024x768 (4:3)"),
    SizeOption("768*1024", "768x1024 (3:4)")
)

/**
 * qwen-image-2.0 系列（qwen-image-2.0-pro / qwen-image-2.0）
 * 总像素需在 512*512 ~ 2048*2048 之间，以下为官方推荐分辨率
 */
val QWEN_IMAGE_2_0_SIZES = listOf(
    SizeOption("2048*2048", "2048x2048 (1:1)"),
    SizeOption("2688*1536", "2688x1536 (16:9)"),
    SizeOption("1536*2688", "1536x2688 (9:16)"),
    SizeOption("2368*1728", "2368x1728 (4:3)"),
    SizeOption("1728*2368", "1728x2368 (3:4)")
)

/**
 * qwen-image-3.0-pro supports arbitrary sizes within the documented pixel-area
 * and aspect-ratio limits. These common presets also satisfy those limits.
 */
val QWEN_IMAGE_3_0_SIZES = listOf(
    SizeOption("2048*2048", "2048x2048 (1:1)"),
    SizeOption("2688*1536", "2688x1536 (16:9)"),
    SizeOption("1536*2688", "1536x2688 (9:16)"),
    SizeOption("2368*1728", "2368x1728 (4:3)"),
    SizeOption("1728*2368", "1728x2368 (3:4)")
)

/**
 * qwen-image-max / qwen-image-plus 系列（含 edit 变体）
 * 仅支持以下固定档位
 */
val QWEN_IMAGE_MAX_SIZES = listOf(
    SizeOption("1328*1328", "1328x1328 (1:1)"),
    SizeOption("1664*928", "1664x928 (16:9)"),
    SizeOption("928*1664", "928x1664 (9:16)"),
    SizeOption("1472*1104", "1472x1104 (4:3)"),
    SizeOption("1104*1472", "1104x1472 (3:4)")
)

/**
 * wan2.7-image 系列
 * 文生图支持 4K，图像编辑/组图最高 2K；以下为官方常见比例推荐分辨率
 */
val WAN27_IMAGE_SIZES = listOf(
    SizeOption("1280*1280", "1280x1280 (1:1)"),
    SizeOption("1280*720", "1280x720 (16:9)"),
    SizeOption("720*1280", "720x1280 (9:16)"),
    SizeOption("1280*960", "1280x960 (4:3)"),
    SizeOption("960*1280", "960x1280 (3:4)")
)

/**
 * 默认尺寸列表（向后兼容，用于未知/自定义模型）
 */
val SIZE_OPTIONS = QWEN_IMAGE_2_0_SIZES

/**
 * 根据模型名称判断所属家族，返回该模型支持的尺寸列表。
 *
 * - SD WebUI 后端：返回 [SD_SIZE_OPTIONS]
 * - qwen-image-2.0 系列：返回 [QWEN_IMAGE_2_0_SIZES]
 * - qwen-image-max / plus 系列（含 edit 变体）：返回 [QWEN_IMAGE_MAX_SIZES]
 * - wan2.7-image 系列：返回 [WAN27_IMAGE_SIZES]
 * - 未知/自定义模型：回退到 [QWEN_IMAGE_2_0_SIZES]
 *
 * @param model 模型名称，为空时回退到默认列表
 * @param isSdBackend 是否为 SD WebUI 后端
 */
fun getSupportedSizes(model: String?, isSdBackend: Boolean): List<SizeOption> {
    if (isSdBackend) return SD_SIZE_OPTIONS
    if (model.isNullOrBlank()) return QWEN_IMAGE_2_0_SIZES

    return when {
        isQwenImage3Model(model) -> QWEN_IMAGE_3_0_SIZES
        isQwenImage2Model(model) -> QWEN_IMAGE_2_0_SIZES
        isQwenImageMaxOrPlusModel(model) -> QWEN_IMAGE_MAX_SIZES
        isWanModel(model) -> WAN27_IMAGE_SIZES
        else -> QWEN_IMAGE_2_0_SIZES
    }
}

/** 判断是否为 qwen-image-2.0 系列模型 */
private fun isQwenImage2Model(model: String): Boolean =
    model.startsWith("qwen-image-2") || model.startsWith("qwen-image-edit-2")

/** 判断是否为 qwen-image-max / qwen-image-plus 系列（含 edit 变体） */
private fun isQwenImageMaxOrPlusModel(model: String): Boolean =
    model.startsWith("qwen-image-max") ||
        model.startsWith("qwen-image-edit-max") ||
        model.startsWith("qwen-image-plus") ||
        model.startsWith("qwen-image-edit-plus") ||
        model == "qwen-image" ||
        model.startsWith("qwen-image-edit@")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeSelector(
    selectedSize: String,
    onSizeSelected: (String) -> Unit,
    label: String = "尺寸",
    sizeOptions: List<SizeOption> = SIZE_OPTIONS,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = sizeOptions.find { it.value == selectedSize }?.label ?: selectedSize

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            sizeOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSizeSelected(option.value)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
