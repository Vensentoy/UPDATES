package com.revyu.app.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revyu.app.ui.components.ErrorBanner
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton

@Composable
fun SemesterVaultGeneratingScreen(
    viewModel: SemesterVaultViewModel,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    apiKeySaved: Boolean,
    onApiKeySavedHandled: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (viewModel.generationState is VaultGenerationUiState.Idle) {
            viewModel.startGeneration()
        }
    }

    LaunchedEffect(apiKeySaved) {
        if (apiKeySaved) {
            viewModel.retryGeneration()
            onApiKeySavedHandled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = viewModel.generationState) {
            is VaultGenerationUiState.Idle, is VaultGenerationUiState.InProgress -> {
                LoadingState("Synthesizing your ${viewModel.examType.titlePrefix} Study Set from ${viewModel.selectedStudySetIds.size} Study Set${if (viewModel.selectedStudySetIds.size == 1) "" else "s"}. This can take a little longer than a regular generation…")
            }
            is VaultGenerationUiState.Success -> {
                LaunchedEffect(state.studySetId) { onDone(state.studySetId) }
                LoadingState("Almost done…")
            }
            is VaultGenerationUiState.Failed -> {
                ErrorBanner(state.message, isRateLimit = state.isRateLimit)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = "Try again",
                    onClick = { viewModel.retryGeneration() },
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.isMissingApiKey) {
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
