package com.example.genbrush.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.genbrush.data.provider.SizeOption
import com.example.genbrush.ui.localization.LocalStrings
import com.example.genbrush.ui.theme.studioColors
import com.example.genbrush.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectRatioSizeSheet(
    selectedSize: String,
    sizeOptions: List<SizeOption>,
    onSizeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    val parsedOptions = remember(sizeOptions) { SizeOptionParser.parseAll(sizeOptions) }
    val tiers = remember(parsedOptions) {
        parsedOptions.mapNotNull { it.tierLabel }.distinct()
    }
    val initialTier = remember(selectedSize, parsedOptions) {
        parsedOptions.firstOrNull { it.value == selectedSize }?.tierLabel ?: tiers.firstOrNull()
    }
    var activeTier by remember(selectedSize, sizeOptions) { mutableStateOf(initialTier) }

    val visibleOptions = remember(parsedOptions, tiers, activeTier) {
        if (tiers.size <= 1 || activeTier == null) {
            parsedOptions
        } else {
            parsedOptions.filter { it.tierLabel == activeTier }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = s.studioSelectSize,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (tiers.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = s.studioResolutionTier,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tiers.forEach { tier ->
                            val isSelected = tier == activeTier
                            Surface(
                                onClick = {
                                    activeTier = tier
                                    val currentRatio = parsedOptions
                                        .firstOrNull { it.value == selectedSize }
                                        ?.ratioLabel
                                    val matching = parsedOptions.firstOrNull {
                                        it.tierLabel == tier && it.ratioLabel == currentRatio
                                    }
                                    if (matching != null) {
                                        onSizeSelected(matching.value)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.studioColors.hairline) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tier,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                        ).tabular(),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = s.studioAspectRatio,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val rows = remember(visibleOptions) { visibleOptions.chunked(3) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { item ->
                                val isSelected = item.value == selectedSize
                                AspectRatioOptionCard(
                                    option = item,
                                    selected = isSelected,
                                    onClick = {
                                        onSizeSelected(item.value)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AspectRatioOptionCard(
    option: ParsedSizeOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.studioColors.hairline)
    }
    val frameColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = border
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                val ratio = option.aspectRatio.coerceIn(0.45f, 2.2f)
                val (boxW, boxH) = if (ratio >= 1f) {
                    24.dp to (24f / ratio).dp.coerceAtLeast(11.dp)
                } else {
                    (24f * ratio).dp.coerceAtLeast(11.dp) to 24.dp
                }
                Box(
                    modifier = Modifier
                        .size(width = boxW, height = boxH)
                        .border(
                            width = 1.6.dp,
                            color = frameColor,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
            Text(
                text = option.ratioLabel,
                style = MaterialTheme.typography.titleSmall.tabular(),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = option.resolutionText,
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
