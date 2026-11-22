package com.example.genbrush.ui.imageedit

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.genbrush.data.local.ImageSaver
import com.example.genbrush.ui.components.AspectRatioSizeSheet
import com.example.genbrush.ui.components.AuroraPrimaryButton
import com.example.genbrush.ui.components.ErrorResultCard
import com.example.genbrush.ui.components.GeneratingCanvasPlaceholder
import com.example.genbrush.ui.components.ModelPickerSheet
import com.example.genbrush.ui.components.SizeOptionParser
import com.example.genbrush.ui.components.StudioHeader
import com.example.genbrush.ui.components.StudioPill
import com.example.genbrush.ui.components.StudioPromptField
import com.example.genbrush.ui.components.dashedBorder
import com.example.genbrush.ui.components.shareImage
import com.example.genbrush.ui.localization.LocalStrings
import com.example.genbrush.ui.theme.studioColors
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ImageEditScreen(
    viewModel: ImageEditViewModel,
    onOpenViewer: (String) -> Unit,
    onManageModels: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()

    var showModelSheet by remember { mutableStateOf(false) }
    var showSizeSheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.setSourceImage(it) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshModels()
    }

    LaunchedEffect(s) {
        viewModel.refreshModels()
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

    val openPhotoPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        StudioHeader(
            title = s.editTitle,
            actions = {
                if (state.supportsImageEdit && state.selectedModel.isNotBlank()) {
                    StudioPill(
                        text = state.selectedModel,
                        icon = Icons.Rounded.AutoFixHigh,
                        trailingIcon = Icons.Rounded.KeyboardArrowDown,
                        onClick = { showModelSheet = true },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            if (!state.supportsImageEdit) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.studioColors.warningContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.studioColors.warning.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.studioColors.warning
                        )
                        Text(
                            text = s.studioEditNotSupported,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onNavigateToSettings) {
                            Text(s.studioGoSettings)
                        }
                    }
                }
            }

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
                    EditResultStage(
                        resultFile = state.resultImageFile!!,
                        sourceImageUri = state.sourceImageUri,
                        prompt = state.resultPrompt,
                        model = state.resultModel,
                        onImageTap = {
                            state.resultImageId?.let(onOpenViewer)
                        },
                        onContinueEditing = {
                            viewModel.setSourceImageFromFile(state.resultImageFile!!)
                        },
                        onSave = {
                            val file = state.resultImageFile ?: return@EditResultStage
                            scope.launch {
                                val res = ImageSaver.saveToGallery(context, file, s.resultSavedBy)
                                if (res.isSuccess) {
                                    Toast.makeText(context, s.commonSaved, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        s.errSaveFailed + (res.exceptionOrNull()?.message ?: ""),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onShare = {
                            state.resultImageFile?.let { shareImage(context, it, s.resultShareTitle) }
                        }
                    )
                }

                state.sourceImageUri != null -> {
                    SourceImageStage(
                        sourceImageUri = state.sourceImageUri!!,
                        onChangeImage = openPhotoPicker
                    )
                }

                else -> {
                    EmptySourcePickerCard(onClick = openPhotoPicker)
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
                    StudioPromptField(
                        value = state.prompt,
                        onValueChange = viewModel::updatePrompt,
                        placeholder = s.editPromptPlaceholder,
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StudioPill(
                            text = SizeOptionParser.compactLabel(
                                selectedSize = state.selectedSize,
                                options = state.availableSizes
                            ),
                            icon = Icons.Rounded.AspectRatio,
                            onClick = { showSizeSheet = true }
                        )

                        AuroraPrimaryButton(
                            text = s.navEdit,
                            icon = Icons.Rounded.AutoFixHigh,
                            enabled = state.supportsImageEdit &&
                                !state.isGenerating &&
                                state.sourceImageUri != null &&
                                state.prompt.isNotBlank(),
                            onClick = { viewModel.generate(context) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySourcePickerCard(onClick: () -> Unit) {
    val s = LocalStrings.current
    val studioColors = MaterialTheme.studioColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f))
            .dashedBorder(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                cornerRadius = 20.dp
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, studioColors.hairline),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = s.editTapToSelect,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = s.editFromLibrary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceImageStage(
    sourceImageUri: Uri,
    onChangeImage: () -> Unit
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val studioColors = MaterialTheme.studioColors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, studioColors.hairline)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(sourceImageUri)
                    .crossfade(280)
                    .build(),
                contentDescription = s.editSourceDesc,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 340.dp),
                contentScale = ContentScale.Fit
            )

            Surface(
                onClick = onChangeImage,
                shape = CircleShape,
                color = studioColors.frostedDark,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = studioColors.frostedDarkContent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = s.editChange,
                        style = MaterialTheme.typography.labelMedium,
                        color = studioColors.frostedDarkContent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditResultStage(
    resultFile: File,
    sourceImageUri: Uri?,
    prompt: String,
    model: String,
    onImageTap: () -> Unit,
    onContinueEditing: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val s = LocalStrings.current
    val studioColors = MaterialTheme.studioColors
    var isComparingOriginal by remember(resultFile) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, studioColors.hairline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(resultFile, sourceImageUri) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val timeoutMs = viewConfiguration.longPressTimeoutMillis
                            var cancelled = false
                            val timedOut = withTimeoutOrNull(timeoutMs) {
                                val up = waitForUpOrCancellation()
                                cancelled = (up == null)
                                false
                            } == null
                            if (!timedOut && !cancelled) {
                                onImageTap()
                            } else if (timedOut && !cancelled && sourceImageUri != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isComparingOriginal = true
                                try {
                                    waitForUpOrCancellation()
                                } finally {
                                    isComparingOriginal = false
                                }
                            }
                        }
                    }
            ) {
                val displayedData: Any = if (isComparingOriginal && sourceImageUri != null) {
                    sourceImageUri
                } else {
                    resultFile
                }

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(displayedData)
                        .crossfade(if (isComparingOriginal) 0 else 280)
                        .build(),
                    contentDescription = s.resultDesc,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    contentScale = ContentScale.Fit
                )

                if (sourceImageUri != null) {
                    Surface(
                        shape = CircleShape,
                        color = studioColors.frostedDark,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Compare,
                                contentDescription = null,
                                tint = studioColors.frostedDarkContent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isComparingOriginal) s.studioOriginalBadge else s.studioEditedBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = studioColors.frostedDarkContent
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = studioColors.frostedDark,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = s.studioCompareHold,
                            style = MaterialTheme.typography.labelSmall,
                            color = studioColors.frostedDarkContent.copy(alpha = 0.88f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$model · ${s.resultEdit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioPill(
                    text = s.studioContinueEdit,
                    icon = Icons.Rounded.AutoFixHigh,
                    onClick = onContinueEditing
                )
                StudioPill(
                    text = s.resultSave,
                    icon = Icons.Rounded.Download,
                    onClick = onSave
                )
                Surface(
                    onClick = onShare,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = s.resultShare,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }
    }
}
