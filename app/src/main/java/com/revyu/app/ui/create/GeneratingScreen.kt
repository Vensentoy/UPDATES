package com.revyu.app.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.ui.components.ErrorBanner
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton

@Composable
fun GeneratingScreen(
    viewModel: CreateStudySetViewModel,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    apiKeySaved: Boolean,
    onApiKeySavedHandled: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (viewModel.generationState is GenerationUiState.Idle) {
            viewModel.startGeneration()
        }
    }

    LaunchedEffect(apiKeySaved) {
        if (apiKeySaved) {
            viewModel.retryGeneration()
            onApiKeySavedHandled()
        }
    }

    GeneratingContent(
        generationState = viewModel.generationState,
        currentStep = viewModel.generationStep,
        onRetry = { viewModel.retryGeneration() },
        onDone = onDone,
        onBack = onBack,
        onOpenSettings = onOpenSettings
    )
}

@Composable
fun GeneratingContent(
    generationState: GenerationUiState,
    onRetry: () -> Unit,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    currentStep: String = "Reading your file"
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (generationState) {
            is GenerationUiState.Idle, is GenerationUiState.InProgress -> {
                LoadingState("$currentStep. This can take a moment on the free tier…")
            }
            is GenerationUiState.Success -> {
                LaunchedEffect(generationState.studySetId) { onDone(generationState.studySetId) }
                LoadingState("Almost done…")
            }
            is GenerationUiState.Failed -> {
                ErrorBanner(generationState.message, isRateLimit = generationState.isRateLimit)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = "Try again",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                )
                if (generationState.isMissingApiKey) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        text = "Open Settings",
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GeneratingScreenInProgressPreview() {
    RevyuTheme {
        GeneratingContent(
            generationState = GenerationUiState.InProgress,
            onRetry = {},
            onDone = {},
            onBack = {},
            onOpenSettings = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GeneratingScreenFailedPreview() {
    RevyuTheme {
        GeneratingContent(
            generationState = GenerationUiState.Failed(
                message = "API rate limit reached. Please wait or check your settings.",
                isRateLimit = true,
                isMissingApiKey = true
            ),
            onRetry = {},
            onDone = {},
            onBack = {},
            onOpenSettings = {}
        )
    }
}
