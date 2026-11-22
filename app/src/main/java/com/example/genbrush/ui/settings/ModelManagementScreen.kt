package com.example.genbrush.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.genbrush.data.local.ModelCapability
import com.example.genbrush.data.local.ModelCatalogRules
import com.example.genbrush.data.local.ModelSource
import com.example.genbrush.ui.components.StudioHeader
import com.example.genbrush.ui.components.StudioPromptField
import com.example.genbrush.ui.localization.AppStrings
import com.example.genbrush.ui.localization.LocalStrings
import com.example.genbrush.ui.theme.studioColors
import com.example.genbrush.ui.theme.tabular
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ModelManagementScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val s = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    val studioColors = MaterialTheme.studioColors

    var selectedCapability by rememberSaveable { mutableStateOf(ModelCapability.GENERATION) }
    val activeCapability = if (state.supportsImageEdit) {
        selectedCapability
    } else {
        ModelCapability.GENERATION
    }

    val currentProviderName = state.providers
        .firstOrNull { it.id == state.providerId }
        ?.displayName(state.language)

    val genEntries = ModelCatalogRules.entriesFor(state.modelCatalog, ModelCapability.GENERATION)
    val editEntries = ModelCatalogRules.entriesFor(state.modelCatalog, ModelCapability.EDIT)
    val entries = if (activeCapability == ModelCapability.GENERATION) genEntries else editEntries

    val customInput = if (activeCapability == ModelCapability.GENERATION) {
        state.customGenModelInput
    } else {
        state.customEditModelInput
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        StudioHeader(
            title = s.settingsModelManagement,
            subtitle = currentProviderName,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Upstream Sync Card
            if (state.supportsModelListing) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, studioColors.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = s.modelRefresh,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = lastRefreshShort(state.modelCatalog.lastRefreshedAt, s),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = s.modelRefreshHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FilledTonalButton(
                            onClick = viewModel::refreshRemoteModels,
                            enabled = !state.isRefreshingModels,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isRefreshingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(s.modelRefreshing, style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(s.modelRefresh, style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        state.modelRefreshMessage?.let { message ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (state.modelRefreshFailed) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                                } else {
                                    studioColors.successContainer.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.modelRefreshFailed) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        studioColors.success
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Capability Segmented Tabs
            if (state.supportsImageEdit) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val genEnabled = genEntries.count { it.enabledGeneration }
                    val editEnabled = editEntries.count { it.enabledEdit }

                    CapabilityTab(
                        label = "${s.sectionGenModels} ($genEnabled/${genEntries.size})",
                        selected = activeCapability == ModelCapability.GENERATION,
                        onClick = { selectedCapability = ModelCapability.GENERATION },
                        modifier = Modifier.weight(1f)
                    )
                    CapabilityTab(
                        label = "${s.sectionEditModels} ($editEnabled/${editEntries.size})",
                        selected = activeCapability == ModelCapability.EDIT,
                        onClick = { selectedCapability = ModelCapability.EDIT },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Models Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, studioColors.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    entries.forEachIndexed { index, model ->
                        val enabled = model.isEnabled(activeCapability)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = model.id,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = if (enabled) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                SourceBadge(source = model.source, s = s)
                            }

                            if (model.source == ModelSource.CUSTOM) {
                                IconButton(
                                    onClick = {
                                        if (activeCapability == ModelCapability.GENERATION) {
                                            viewModel.removeCustomGenerationModel(model.id)
                                        } else {
                                            viewModel.removeCustomEditModel(model.id)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = s.settingsRemoveModel,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (activeCapability == ModelCapability.GENERATION) {
                                        viewModel.toggleGenerationModel(model.id)
                                    } else {
                                        viewModel.toggleEditModel(model.id)
                                    }
                                }
                            )
                        }
                        if (index < entries.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = studioColors.hairline,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            // Custom Model Add Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, studioColors.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = s.settingsAddCustomModel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudioPromptField(
                            value = customInput,
                            onValueChange = { value ->
                                if (activeCapability == ModelCapability.GENERATION) {
                                    viewModel.updateCustomGenModelInput(value)
                                } else {
                                    viewModel.updateCustomEditModelInput(value)
                                }
                            },
                            placeholder = s.settingsCustomModelHint,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (activeCapability == ModelCapability.GENERATION) {
                                    viewModel.addCustomGenerationModel()
                                } else {
                                    viewModel.addCustomEditModel()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(s.settingsAddButton, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    state.modelManageError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CapabilityTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.studioColors.hairline) else null,
        modifier = modifier.height(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ).tabular(),
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SourceBadge(
    source: ModelSource,
    s: AppStrings
) {
    val studioColors = MaterialTheme.studioColors
    val (bg, fg, label) = when (source) {
        ModelSource.BUILTIN -> Triple(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.onSurfaceVariant,
            s.modelSourceBuiltin
        )
        ModelSource.REMOTE -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.primary,
            s.modelSourceRemote
        )
        ModelSource.CUSTOM -> Triple(
            studioColors.successContainer.copy(alpha = 0.75f),
            studioColors.success,
            s.modelSourceCustom
        )
    }

    Surface(
        shape = CircleShape,
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private fun lastRefreshShort(timestamp: Long, s: AppStrings): String {
    if (timestamp <= 0L) return s.modelNeverRefreshed
    val formatted = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    return s.modelLastRefresh.format(formatted)
}
