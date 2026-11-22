package com.example.genbrush.ui.gallery

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.genbrush.data.local.ImageEntry
import com.example.genbrush.data.local.ImageSaver
import com.example.genbrush.data.repository.GenerationRepository
import com.example.genbrush.ui.components.AuroraPrimaryButton
import com.example.genbrush.ui.components.StudioPill
import com.example.genbrush.ui.components.shareImage
import com.example.genbrush.ui.localization.LocalStrings
import com.example.genbrush.ui.theme.FavoriteGold
import com.example.genbrush.ui.theme.studioColors
import com.example.genbrush.ui.theme.tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullImageViewerScreen(
    imageId: String,
    repository: GenerationRepository,
    onBack: () -> Unit,
    onRegenerate: (ImageEntry) -> Unit = {},
    onEditImage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val s = LocalStrings.current

    // Enforce light icons over pure black cinema canvas while viewer is active
    DisposableEffect(view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatusLight = controller?.isAppearanceLightStatusBars
        val prevNavLight = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (prevStatusLight != null) controller.isAppearanceLightStatusBars = prevStatusLight
            if (prevNavLight != null) controller.isAppearanceLightNavigationBars = prevNavLight
        }
    }

    var allEntries by remember { mutableStateOf<List<ImageEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        allEntries = repository.getGalleryImages()
    }

    val initialIndex = remember(allEntries) {
        allEntries.indexOfFirst { it.id == imageId }.coerceAtLeast(0)
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { allEntries.size }
    )

    LaunchedEffect(initialIndex, allEntries.size) {
        if (allEntries.isNotEmpty() && initialIndex in allEntries.indices) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    val currentEntry = allEntries.getOrNull(pagerState.currentPage)
    val currentFile = currentEntry?.let { repository.getLocalImagePath(it) }

    if (showDeleteDialog && currentEntry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(s.deleteConfirmTitle, style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = s.deleteConfirmMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    coroutineScope.launch(Dispatchers.IO) {
                        repository.deleteImage(currentEntry)
                        val refreshed = repository.getGalleryImages()
                        withContext(Dispatchers.Main) {
                            allEntries = refreshed
                            if (refreshed.isEmpty()) onBack()
                        }
                    }
                }) {
                    Text(
                        text = s.commonConfirm,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(s.commonCancel)
                }
            }
        )
    }

    if (showInfoSheet && currentEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = s.viewerInfoTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Prompt card with quick copy
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.studioColors.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = s.viewerPrompt,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            StudioPill(
                                text = s.viewerCopyPrompt,
                                icon = Icons.Rounded.ContentCopy,
                                onClick = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    clipboard.setPrimaryClip(ClipData.newPlainText("prompt", currentEntry.prompt))
                                    Toast.makeText(context, s.galleryPromptCopied, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        Text(
                            text = currentEntry.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                currentEntry.negativePrompt?.takeIf { it.isNotBlank() }?.let { negPrompt ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.studioColors.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = s.viewerNegativePrompt,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = negPrompt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                val fileSizeText = currentFile?.takeIf { it.exists() }?.let { file ->
                    val sizeKb = file.length() / 1024
                    if (sizeKb >= 1024) String.format(Locale.US, "%.1f MB", sizeKb / 1024f) else "$sizeKb KB"
                }
                val formattedTime = remember(currentEntry.timestamp) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(currentEntry.timestamp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetadataTile(
                            label = s.viewerModel,
                            value = currentEntry.model,
                            modifier = Modifier.weight(1f)
                        )
                        MetadataTile(
                            label = s.viewerType,
                            value = if (currentEntry.type == "text_to_image") s.galleryFilterTxt2img else s.galleryFilterEdit,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetadataTile(
                            label = s.viewerSize,
                            value = currentEntry.size?.replace("*", "×") ?: "—",
                            tabular = true,
                            modifier = Modifier.weight(1f)
                        )
                        MetadataTile(
                            label = s.viewerFileSize,
                            value = fileSizeText ?: "—",
                            tabular = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    MetadataTile(
                        label = s.viewerTime,
                        value = formattedTime,
                        tabular = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AuroraPrimaryButton(
                        text = s.viewerRegenerate,
                        icon = Icons.Rounded.AutoAwesome,
                        onClick = {
                            showInfoSheet = false
                            onRegenerate(currentEntry)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            showInfoSheet = false
                            currentFile?.let { onEditImage(it.absolutePath) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text(
                            text = s.viewerEditImage,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (allEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.viewerNotFound,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        } else {
            var isZoomed by remember { mutableStateOf(false) }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = !isZoomed
            ) { page ->
                val entry = allEntries[page]
                val file = repository.getLocalImagePath(entry)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (file.exists()) {
                        ZoomableImage(
                            file = file,
                            contentDescription = s.viewerDesc,
                            isCurrentPage = page == pagerState.currentPage,
                            onSingleTap = { chromeVisible = !chromeVisible },
                            onZoomChanged = { zoomed -> isZoomed = zoomed }
                        )
                    } else {
                        Text(
                            text = s.viewerNotFound,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        // Top Cinema Overlay
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.74f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CinemaCircleButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = s.viewerBack,
                        onClick = onBack
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (allEntries.isNotEmpty()) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${allEntries.size}",
                                style = MaterialTheme.typography.labelLarge.tabular(),
                                color = Color.White
                            )
                        }
                        currentEntry?.let { entry ->
                            Text(
                                text = entry.model,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (currentEntry != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CinemaCircleButton(
                                icon = if (currentEntry.isFavorite) Icons.Rounded.Star else Icons.Outlined.Star,
                                contentDescription = if (currentEntry.isFavorite) s.galleryUnfavorite else s.galleryFavorite,
                                iconTint = if (currentEntry.isFavorite) FavoriteGold else Color.White,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        repository.setFavorite(currentEntry.id, !currentEntry.isFavorite)
                                        allEntries = repository.getGalleryImages()
                                    }
                                }
                            )
                            CinemaCircleButton(
                                icon = Icons.Rounded.Info,
                                contentDescription = s.viewerInfo,
                                onClick = { showInfoSheet = true }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }
                }
            }
        }

        // Bottom Cinema Overlay
        AnimatedVisibility(
            visible = chromeVisible && currentEntry != null && currentFile != null && currentFile.exists(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (currentEntry != null && currentFile != null && currentFile.exists()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.86f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = currentEntry.prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showInfoSheet = true }
                                .padding(horizontal = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CinemaActionItem(
                                icon = Icons.Rounded.AutoAwesome,
                                label = s.viewerRegenerateShort,
                                onClick = { onRegenerate(currentEntry) }
                            )
                            CinemaActionItem(
                                icon = Icons.Rounded.AutoFixHigh,
                                label = s.viewerEditShort,
                                onClick = { onEditImage(currentFile.absolutePath) }
                            )
                            CinemaActionItem(
                                icon = Icons.Rounded.Download,
                                label = s.viewerSave,
                                onClick = {
                                    val latestEntry = allEntries.getOrNull(pagerState.currentPage) ?: return@CinemaActionItem
                                    val latestFile = repository.getLocalImagePath(latestEntry)
                                    if (!latestFile.exists()) return@CinemaActionItem
                                    coroutineScope.launch {
                                        val result = ImageSaver.saveToGallery(
                                            context,
                                            latestFile,
                                            "Generated by GenBrush: ${latestEntry.prompt}"
                                        )
                                        if (result.isSuccess) {
                                            Toast.makeText(context, s.commonSaved, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                s.errSaveFailed + (result.exceptionOrNull()?.message ?: ""),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                            CinemaActionItem(
                                icon = Icons.Rounded.Share,
                                label = s.viewerShare,
                                onClick = {
                                    val latestEntry = allEntries.getOrNull(pagerState.currentPage) ?: return@CinemaActionItem
                                    val latestFile = repository.getLocalImagePath(latestEntry)
                                    if (latestFile.exists()) {
                                        shareImage(context, latestFile, s.viewerShareTitle)
                                    }
                                }
                            )
                            CinemaActionItem(
                                icon = Icons.Rounded.DeleteOutline,
                                label = s.viewerDelete,
                                tint = Color(0xFFFF7A7A),
                                onClick = { showDeleteDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    file: File,
    contentDescription: String,
    isCurrentPage: Boolean,
    onSingleTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scaleAnim.snapTo(1f)
            offsetXAnim.snapTo(0f)
            offsetYAnim.snapTo(0f)
            onZoomChanged(false)
        }
    }

    val isZoomed = scaleAnim.value > 1.02f

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(file)
            .crossfade(220)
            .build(),
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scaleAnim.value,
                scaleY = scaleAnim.value,
                translationX = offsetXAnim.value,
                translationY = offsetYAnim.value
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        scope.launch {
                            if (scaleAnim.value > 1.05f) {
                                onZoomChanged(false)
                                launch { scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            } else {
                                onZoomChanged(true)
                                scaleAnim.animateTo(2.5f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    }
                )
            }
            .then(
                if (isZoomed) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scope.launch {
                                val nextScale = (scaleAnim.value * zoom).coerceIn(1f, 5f)
                                scaleAnim.snapTo(nextScale)
                                if (nextScale <= 1.02f) {
                                    scaleAnim.snapTo(1f)
                                    offsetXAnim.snapTo(0f)
                                    offsetYAnim.snapTo(0f)
                                    onZoomChanged(false)
                                } else {
                                    offsetXAnim.snapTo(offsetXAnim.value + pan.x)
                                    offsetYAnim.snapTo(offsetYAnim.value + pan.y)
                                    onZoomChanged(true)
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun CinemaCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    iconTint: Color = Color.White
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CinemaActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun MetadataTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tabular: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.studioColors.hairline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = if (tabular) MaterialTheme.typography.bodyMedium.tabular() else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
