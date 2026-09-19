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
import com.example.genbrush.data.local.PreferencesManager

/** 所有预置的生成模型（完整列表，供设置页勾选使用） */
val PRESET_GENERATION_MODELS = listOf(
    "qwen-image-3.0-pro",
    "qwen-image-2.0-pro-2026-06-22",
    "qwen-image-2.0-pro-2026-04-22",
    "qwen-image-2.0-pro",
    "qwen-image-2.0",
    "qwen-image-max",
    "qwen-image-plus",
    "wan2.7-image-pro",
    "wan2.7-image"
)

/** 所有预置的编辑模型（完整列表，供设置页勾选使用） */
val PRESET_EDIT_MODELS = listOf(
    "qwen-image-3.0-pro",
    "qwen-image-2.0-pro-2026-06-22",
    "qwen-image-2.0-pro-2026-04-22",
    "qwen-image-edit-max",
    "qwen-image-edit-plus",
    "qwen-image-2.0-pro",
    "qwen-image-2.0",
    "wan2.7-image-pro",
    "wan2.7-image"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    label: String = "模型",
    models: List<String> = PRESET_GENERATION_MODELS,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedModel,
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
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/**
 * 根据用户配置计算可用的模型列表
 * = 启用的预置模型 + 自定义模型
 */
fun buildAvailableModels(
    presetModels: List<String>,
    disabledModels: Set<String>,
    customModels: List<String>
): List<String> {
    val enabled = presetModels.filter { it !in disabledModels }
    return (enabled + customModels).distinct()
}

/** 获取用户配置的生成模型列表 */
fun getConfiguredGenerationModels(prefs: PreferencesManager): List<String> {
    return buildAvailableModels(
        PRESET_GENERATION_MODELS,
        prefs.disabledGenerationModels,
        prefs.customGenerationModels
    )
}

/** 获取用户配置的编辑模型列表 */
fun getConfiguredEditModels(prefs: PreferencesManager): List<String> {
    return buildAvailableModels(
        PRESET_EDIT_MODELS,
        prefs.disabledEditModels,
        prefs.customEditModels
    )
}
