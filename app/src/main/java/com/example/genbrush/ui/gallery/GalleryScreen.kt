package com.example.genbrush.ui.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.genbrush.ui.components.SizeOptionParser
import com.example.genbrush.ui.components.StudioEmptyState
import com.example.genbrush.ui.components.StudioHeader
import com.example.genbrush.ui.components.StudioPill
import com.example.genbrush.ui.components.shareImage
import com.example.genbrush.ui.localization.LocalStrings
import com.example.genbrush.ui.theme.FavoriteGold
import com.example.genbrush.ui.theme.studioColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onImageClick: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val s = LocalStrings.current

    var showSortMenu by remember { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }
    val searchFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = state.selectionMode) {
        viewModel.exitSelectionMode()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadImages()
    }

    LaunchedEffect(showSearch) {
        if (showSearch) {
            searchFocusRequester.requestFocus()
        }
    }

    val saveResult = state.saveResult
    LaunchedEffect(saveResult) {
        when (val r = saveResult) {
            is SaveResult.Success -> {
                Toast.makeText(context, s.commonSaved, Toast.LENGTH_SHORT).show()
                viewModel.consumeSaveResult()
            }
            is SaveResult.Error -> {
                Toast.makeText(context, s.errSaveFailed + r.message, Toast.LENGTH_SHORT).show()
                viewModel.consumeSaveResult()
            }
            null -> Unit
        }
    }

    state.pendingDelete?.let {
        val isBatch = viewModel.isBatchDeletePending()
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = s.deleteConfirmTitle,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = if (isBatch) {
                        s.galleryBatchDeleteConfirm.format(state.selectedIds.size)
                    } else {
                        s.deleteConfirmMessage
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isBatch) viewModel.confirmBatchDelete() else viewModel.confirmDelete()
                }) {
                    Text(
                        text = s.commonConfirm,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(s.commonCancel)
                }
            }
        )
    }

    val displayedImages = state.displayedImages
    val studioColors = MaterialTheme.studioColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            if (state.selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderIconCircleButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = s.commonCancel,
                        onClick = { viewModel.exitSelectionMode() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = s.gallerySelectedCount.format(state.selectedIds.size),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.selectAll() }) {
                        Text(
                            text = s.gallerySelectAll,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                StudioHeader(
                    title = s.galleryTitle,
                    countBadge = state.images.size.takeIf { it > 0 }?.toString(),
                    actions = {
                        HeaderIconCircleButton(
                            icon = Icons.Rounded.Search,
                            contentDescription = s.gallerySearchHint,
                            active = showSearch || state.searchQuery.isNotEmpty(),
                            onClick = { showSearch = !showSearch }
                        )
                        Box {
                            HeaderIconCircleButton(
                                icon = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                active = state.sortMode != SortMode.TIME_DESC,
                                onClick = { showSortMenu = true }
                            )
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortModeOptionItem(
                                    label = s.gallerySortNewest,
                                    selected = state.sortMode == SortMode.TIME_DESC,
                                    onClick = {
                                        viewModel.updateSortMode(SortMode.TIME_DESC)
                                        showSortMenu = false
                                    }
                                )
                                SortModeOptionItem(
                                    label = s.gallerySortOldest,
                                    selected = state.sortMode == SortMode.TIME_ASC,
                                    onClick = {
                                        viewModel.updateSortMode(SortMode.TIME_ASC)
                                        showSortMenu = false
                                    }
                                )
                                SortModeOptionItem(
                                    label = s.gallerySortFavFirst,
                                    selected = state.sortMode == SortMode.FAVORITE_FIRST,
                                    onClick = {
                                        viewModel.updateSortMode(SortMode.FAVORITE_FIRST)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                        if (displayedImages.isNotEmpty()) {
                            HeaderIconCircleButton(
                                icon = Icons.Rounded.Checklist,
                                contentDescription = s.gallerySelectMode,
                                onClick = { viewModel.enterSelectionMode(null) }
                            )
                        }
                    }
                )
            }

            // Collapsible Search Pill
            AnimatedVisibility(
                visible = showSearch && !state.selectionMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, studioColors.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 6.dp)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.searchQuery.isEmpty()) {
                                Text(
                                    text = s.gallerySearchHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            BasicTextField(
                                value = state.searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (state.searchQuery.isNotEmpty()) {
                                    viewModel.updateSearchQuery("")
                                } else {
                                    showSearch = false
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Single-row Filter Pills
            AnimatedVisibility(visible = !state.selectionMode) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        StudioPill(
                            text = s.galleryFilterAll,
                            selected = state.typeFilter == null,
                            onClick = { viewModel.updateTypeFilter(null) }
                        )
                    }
                    item {
                        StudioPill(
                            text = s.galleryFilterTxt2img,
                            selected = state.typeFilter == "text_to_image",
                            onClick = { viewModel.updateTypeFilter("text_to_image") }
                        )
                    }
                    item {
                        StudioPill(
                            text = s.galleryFilterEdit,
                            selected = state.typeFilter == "image_edit",
                            onClick = { viewModel.updateTypeFilter("image_edit") }
                        )
                    }
                    item {
                        StudioPill(
                            text = s.galleryFavoritesChip,
                            icon = if (state.showFavoritesOnly) Icons.Rounded.Star else Icons.Outlined.Star,
                            selected = state.showFavoritesOnly,
                            onClick = { viewModel.toggleFavoriteFilter() }
                        )
                    }
                }
            }

            // Content Area
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                state.images.isEmpty() -> {
                    StudioEmptyState(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = s.galleryEmpty,
                        subtitle = s.galleryEmptyHint
                    )
                }

                displayedImages.isEmpty() -> {
                    StudioEmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = s.galleryEmptyFiltered,
                        subtitle = s.galleryEmptyFilteredHint,
                        actionLabel = s.galleryClearFilter,
                        onAction = {
                            viewModel.updateSearchQuery("")
                            viewModel.updateTypeFilter(null)
                            if (state.showFavoritesOnly) viewModel.toggleFavoriteFilter()
                            showSearch = false
                        }
                    )
                }

                else -> {
                    val gridState = rememberLazyStaggeredGridState()
                    LazyVerticalStaggeredGrid(
                        state = gridState,
                        columns = StaggeredGridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 6.dp,
                            bottom = if (state.selectionMode) 96.dp else 20.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedImages, key = { it.id }) { entry ->
                            var showMenu by remember { mutableStateOf(false) }
                            val file = remember(entry) { viewModel.getLocalImagePath(entry) }
                            val isSelected = entry.id in state.selectedIds
                            val aspectRatio = remember(entry.size) {
                                SizeOptionParser.parseAspectRatio(
                                    size = entry.size,
                                    minRatio = 0.62f,
                                    maxRatio = 1.55f
                                )
                            }

                            Box {
                                val cardShape = RoundedCornerShape(14.dp)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(aspectRatio)
                                        .clip(cardShape)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, cardShape)
                                            } else {
                                                Modifier.border(1.dp, studioColors.hairline, cardShape)
                                            }
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (state.selectionMode) {
                                                    viewModel.toggleSelection(entry.id)
                                                } else {
                                                    onImageClick(entry.id)
                                                }
                                            },
                                            onLongClick = {
                                                if (!state.selectionMode) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showMenu = true
                                                }
                                            }
                                        ),
                                    shape = cardShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (file.exists()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(file)
                                                    .crossfade(280)
                                                    .build(),
                                                contentDescription = entry.prompt,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = s.galleryImageNotFound,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (state.selectionMode) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        if (isSelected) {
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                                        } else {
                                                            Color.Black.copy(alpha = 0.1f)
                                                        }
                                                    )
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(8.dp)
                                                    .size(24.dp),
                                                tint = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color.White.copy(alpha = 0.78f)
                                                }
                                            )
                                        }

                                        if (entry.isFavorite && !state.selectionMode) {
                                            Surface(
                                                shape = CircleShape,
                                                color = studioColors.frostedDark,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Star,
                                                        contentDescription = null,
                                                        tint = FavoriteGold,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!state.selectionMode) {
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (entry.isFavorite) s.galleryUnfavorite else s.galleryFavorite) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (entry.isFavorite) Icons.Rounded.Star else Icons.Outlined.Star,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showMenu = false
                                                viewModel.toggleFavorite(entry)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(s.gallerySave) },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                showMenu = false
                                                viewModel.saveToDeviceGallery(entry, context)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(s.galleryShare) },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                showMenu = false
                                                if (file.exists()) {
                                                    shareImage(context, file, s.galleryShareTitle)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(s.galleryCopyPrompt) },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                showMenu = false
                                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                                clipboard.setPrimaryClip(ClipData.newPlainText("prompt", entry.prompt))
                                                Toast.makeText(context, s.galleryPromptCopied, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = s.galleryDelete,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showMenu = false
                                                viewModel.requestDelete(entry)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom Batch Selection Dock
        AnimatedVisibility(
            visible = state.selectionMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, studioColors.hairline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasSelection = state.selectedIds.isNotEmpty()
                    TextButton(
                        onClick = { viewModel.batchFavorite() },
                        enabled = hasSelection
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.galleryBatchFavorite)
                    }
                    TextButton(
                        onClick = { viewModel.requestBatchDelete() },
                        enabled = hasSelection
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = if (hasSelection) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = s.galleryBatchDelete,
                            color = if (hasSelection) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderIconCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    active: Boolean = false
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    } else {
        MaterialTheme.studioColors.hairline
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SortModeOptionItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        },
        onClick = onClick
    )
}
