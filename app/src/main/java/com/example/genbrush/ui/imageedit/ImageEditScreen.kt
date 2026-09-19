package com.example.genbrush.ui.imageedit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.genbrush.ui.components.ErrorResultCard
import com.example.genbrush.ui.components.ImageResultCard
import com.example.genbrush.ui.components.LoadingOverlay
import com.example.genbrush.ui.components.LoraSelector
import com.example.genbrush.ui.components.ModelSelector
import com.example.genbrush.ui.components.SizeSelector
import com.example.genbrush.ui.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditScreen(viewModel: ImageEditViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val s = LocalStrings.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.setSourceImage(it) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshModels()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(s.editTitle) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (state.sourceImageUri == null) {
                OutlinedCard(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                s.editTapToSelect,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                s.editFromLibrary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box {
                        AsyncImage(
                            model = state.sourceImageUri,
                            contentDescription = s.editSourceDesc,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            contentScale = ContentScale.Fit
                        )
                        TextButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Text(s.editChange)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::updatePrompt,
                label = { Text(s.editPromptLabel) },
                placeholder = { Text(s.editPromptPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            ModelSelector(
                selectedModel = state.selectedModel,
                onModelSelected = viewModel::selectModel,
                label = s.editModel,
                models = state.availableModels
            )

            SizeSelector(
                selectedSize = state.selectedSize,
                onSizeSelected = viewModel::selectSize,
                label = s.sizeLabel,
                sizeOptions = state.availableSizes
            )

            if (state.isSdBackend) {
                LoraSelector(
                    availableLoras = state.availableLoras,
                    selectedLoras = state.selectedLoras,
                    onToggleLora = viewModel::toggleLora,
                    onWeightChange = viewModel::updateLoraWeight
                )
            }

            Button(
                onClick = { viewModel.generate(context) },
                enabled = !state.isGenerating && state.sourceImageUri != null && state.prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(s.editButton)
            }

            LoadingOverlay(
                isLoading = state.isGenerating,
                hint = if (state.isSdBackend) s.loadingHintSd else null,
                elapsedSeconds = state.elapsedSeconds,
                onStop = viewModel::stopGeneration
            )

            state.resultImageFile?.let { file ->
                ImageResultCard(
                    imageFile = file,
                    imageUrl = null,
                    prompt = state.resultPrompt,
                    model = state.resultModel,
                    type = "image_edit"
                )
            }

            state.error?.let { errorMsg ->
                ErrorResultCard(errorMessage = errorMsg)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
