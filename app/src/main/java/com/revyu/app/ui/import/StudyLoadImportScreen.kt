package com.revyu.app.ui.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyLoadImportScreen(
    navController: NavHostController
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: StudyLoadImportViewModel = rememberImportViewModel(navController)
    val state by viewModel.state.collectAsState()

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onFilePicked(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        CenterAlignedTopAppBar(
            title = { Text("Import study load") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        when (val s = state) {
            is StudyLoadImportUiState.Idle -> {
                EmptyState(
                    title = "Upload your LLCC Study Load",
                    body = "A PDF from your school portal — usually pages listing your subjects (code, units, room) and their weekly schedule.",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    action = {
                        PrimaryButton(
                            text = "Pick PDF",
                            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom
                ) {
                    Text(
                        "Revyu reads it locally and never uploads your study load.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            is StudyLoadImportUiState.Parsing -> {
                LoadingState("Reading your PDF…")
            }

            is StudyLoadImportUiState.Review -> {
                LoadingState("Preparing review…")
                LaunchedEffect(Unit) {
                    navController.navigate(com.revyu.app.core.navigation.RevyuDestinations.StudyLoadReview)
                }
            }

            is StudyLoadImportUiState.Saving -> {
                LoadingState("Saving your schedule…")
            }

            is StudyLoadImportUiState.Duplicate -> {
                val message = "This Study Load (${s.fileName}) has already been imported!"
                LaunchedEffect(s.fileName) {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    navController.navigate(com.revyu.app.core.navigation.RevyuDestinations.Reviewer) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                    }
                    viewModel.dismissError()
                }
                LoadingState("Redirecting to Reviewer…")
            }

            is StudyLoadImportUiState.Error -> {
                EmptyState(
                    title = s.title,
                    body = s.message,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    action = {
                        PrimaryButton(
                            text = s.title,
                            onClick = {
                                viewModel.dismissError()
                                pdfPicker.launch(arrayOf("application/pdf"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            }
        }
    }
}

/** Picker + review share one graph-scoped ViewModel, exactly like the Create wizard. */
@Composable
fun rememberImportViewModel(navController: NavHostController): StudyLoadImportViewModel {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val graphEntry = androidx.compose.runtime.remember(navController) {
        navController.getBackStackEntry(com.revyu.app.core.navigation.RevyuDestinations.StudyLoadImportGraphPattern)
    }
    return viewModel(
        graphEntry,
        factory = viewModelFactory {
            StudyLoadImportViewModel(
                context = context,
                extractor = container.fileTextExtractor,
                studyLoadRepository = container.studyLoadRepository,
                appSettingsStore = container.appSettingsStore
            )
        }
    )
}