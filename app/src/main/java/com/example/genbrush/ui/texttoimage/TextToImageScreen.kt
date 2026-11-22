package com.example.genbrush.ui.texttoimage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.genbrush.ui.components.AspectRatioSizeSheet
import com.example.genbrush.ui.components.AuroraPrimaryButton
import com.example.genbrush.ui.components.ErrorResultCard
import com.example.genbrush.ui.components.GeneratingCanvasPlaceholder
import com.example.genbrush.ui.components.ImageResultCard
import com.example.genbrush.ui.components.ModelPickerSheet
import com.example.genbrush.ui.components.SizeOptionParser
import com.example.genbrush.ui.components.StudioHeader
import com.example.genbrush.ui.components.StudioPill
import com.example.genbrush.ui.components.StudioPromptField
import com.example.genbrush.ui.localization.LocalStrings
import com.example.genbrush.ui.theme.studioColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextToImageScreen(
    viewModel: TextToImageViewModel,
    onOpenViewer: (String) -> Unit,
    onSendToEdit: (String) -> Unit,
    onManageModels: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val s = LocalStrings.current

    var showModelSheet by remember { mutableStateOf(false) }
    var showSizeSheet by remember { mutableStateOf(false) }
    var showNegativeField by rememberSaveable { mutableStateOf(state.negativePrompt.isNotBlank()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshModels()
    }

    LaunchedEffect(s) {
        viewModel.refreshModels()
    }

    LaunchedEffect(state.negativePrompt) {
        if (state.negativePrompt.isNotBlank()) {
            showNegativeField = true
        }
    }

    if (showModelSheet) {
        ModelPickerSheet(
            selectedModel = state.selectedModel,
            models = state.availableModels,
            providerName = state.providerName,
            onModelSelected = viewModel::selectModel,
            onManageModels = onManageModels,
            onDismiss = { showModelSheet = false }
        )
    }

    if (showSizeSheet) {
        AspectRatioSizeSheet(
            selectedSize = state.selectedSize,
            sizeOptions = state.availableSizes,
            onSizeSelected = viewModel::selectSize,
            onDismiss = { showSizeSheet = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        StudioHeader(
            title = s.txt2imgTitle,
            actions = {
                if (state.selectedModel.isNotBlank()) {
                    StudioPill(
                        text = state.selectedModel,
                        icon = Icons.Rounded.AutoAwesome,
                        trailingIcon = Icons.Rounded.KeyboardArrowDown,
                        onClick = { showModelSheet = true },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        )

        // Canvas Stage
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            when {
                state.isGenerating -> {
                    GeneratingCanvasPlaceholder(
                        isLoading = true,
                        aspectRatio = SizeOptionParser.parseAspectRatio(state.selectedSize),
                        elapsedSeconds = state.elapsedSeconds,
                        onStop = viewModel::stopGeneration
                    )
                }

                state.resultImageFile != null -> {
                    val file = state.resultImageFile!!
                    ImageResultCard(
                        imageFile = file,
                        imageUrl = null,
                        prompt = state.resultPrompt,
                        model = state.resultModel,
                        type = "text_to_image",
                        onImageClick = {
                            state.resultImageId?.let(onOpenViewer)
                        },
                        onEditClick = { onSendToEdit(file.absolutePath) }
                    )
                }

                else -> {
                    StudioCreationEmptyCanvas(
                        onApplyPrompt = viewModel::updatePrompt
                    )
                }
            }

            state.error?.let { errorMsg ->
                val isKeyError = errorMsg == s.errApiKey ||
                    errorMsg == s.errInvalidKey ||
                    errorMsg == s.errSetApiKey
                ErrorResultCard(
                    errorMessage = errorMsg,
                    onDismiss = viewModel::dismissError,
                    actionLabel = if (isKeyError) s.studioGoSettings else null,
                    onAction = if (isKeyError) onNavigateToSettings else null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Bottom Composer Dock
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.studioColors.hairline)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedVisibility(
                        visible = showNegativeField,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        StudioPromptField(
                            value = state.negativePrompt,
                            onValueChange = viewModel::updateNegativePrompt,
                            placeholder = "${s.txt2imgNegLabel} · ${s.txt2imgNegPlaceholder}",
                            singleLine = true
                        )
                    }

                    StudioPromptField(
                        value = state.prompt,
                        onValueChange = viewModel::updatePrompt,
                        placeholder = s.txt2imgPromptPlaceholder,
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StudioPill(
                                text = SizeOptionParser.compactLabel(
                                    selectedSize = state.selectedSize,
                                    options = state.availableSizes
                                ),
                                icon = Icons.Rounded.AspectRatio,
                                onClick = { showSizeSheet = true }
                            )
                            StudioPill(
                                text = s.studioNegativeShort,
                                icon = Icons.Rounded.Block,
                                selected = showNegativeField,
                                accentDot = state.negativePrompt.isNotBlank(),
                                onClick = { showNegativeField = !showNegativeField }
                            )
                        }

                        AuroraPrimaryButton(
                            text = s.navGenerate,
                            icon = Icons.Rounded.AutoAwesome,
                            enabled = !state.isGenerating && state.prompt.isNotBlank(),
                            onClick = viewModel::generate
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioCreationEmptyCanvas(
    onApplyPrompt: (String) -> Unit
) {
    val s = LocalStrings.current
    val studioColors = MaterialTheme.studioColors
    val ideas = listOf(
        s.studioPromptChip1 to s.studioPromptChip1Full,
        s.studioPromptChip2 to s.studioPromptChip2Full,
        s.studioPromptChip3 to s.studioPromptChip3Full
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, studioColors.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(studioColors.auroraBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = s.studioEmptyTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = s.studioEmptySubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ideas.forEach { (shortLabel, fullPrompt) ->
                    StudioPill(
                        text = shortLabel,
                        onClick = { onApplyPrompt(fullPrompt) }
                    )
                }
            }
        }
    }
}
