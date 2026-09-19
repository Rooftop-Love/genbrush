package com.example.genbrush.ui.texttoimage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.unit.dp
import com.example.genbrush.ui.components.ErrorResultCard
import com.example.genbrush.ui.components.ImageResultCard
import com.example.genbrush.ui.components.LoadingOverlay
import com.example.genbrush.ui.components.ModelSelector
import com.example.genbrush.ui.components.SizeSelector
import com.example.genbrush.ui.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToImageScreen(viewModel: TextToImageViewModel) {
    val state by viewModel.state.collectAsState()
    val s = LocalStrings.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshModels()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(s.txt2imgTitle) },
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

            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::updatePrompt,
                label = { Text(s.txt2imgPromptLabel) },
                placeholder = { Text(s.txt2imgPromptPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            OutlinedTextField(
                value = state.negativePrompt,
                onValueChange = viewModel::updateNegativePrompt,
                label = { Text(s.txt2imgNegLabel) },
                placeholder = { Text(s.txt2imgNegPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            ModelSelector(
                selectedModel = state.selectedModel,
                onModelSelected = viewModel::selectModel,
                label = s.txt2imgModel,
                models = state.availableModels
            )

            SizeSelector(
                selectedSize = state.selectedSize,
                onSizeSelected = viewModel::selectSize,
                label = s.txt2imgSize,
                sizeOptions = state.availableSizes
            )

            Button(
                onClick = viewModel::generate,
                enabled = !state.isGenerating && state.prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(s.txt2imgButton)
            }

            LoadingOverlay(
                isLoading = state.isGenerating,
                elapsedSeconds = state.elapsedSeconds,
                onStop = viewModel::stopGeneration
            )

            state.resultImageFile?.let { file ->
                ImageResultCard(
                    imageFile = file,
                    imageUrl = null,
                    prompt = state.resultPrompt,
                    model = state.resultModel,
                    type = "text_to_image"
                )
            }

            state.error?.let { errorMsg ->
                ErrorResultCard(errorMessage = errorMsg)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
