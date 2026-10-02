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
import androidx.navigation.NavHostController
import com.revyu.app.core.navigation.RevyuDestinations
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.LoadingState
import com.revyu.app.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolCalendarImportScreen(
    navController: NavHostController
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: SchoolCalendarImportViewModel = rememberSchoolCalendarViewModel(navController)
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
            title = { Text("Import school calendar") },
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
            is SchoolCalendarImportUiState.Idle -> {
                EmptyState(
                    title = "Upload your LLCC School Calendar",
                    body = "A PDF of the official semester calendar — class suspensions, " +
                        "exams, and events like Intramurals become dated cards in your " +
                        "schedule and the home-screen widget.",
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
                        "Photo scans work too — Revyu reads them with on-device OCR, " +
                            "nothing ever leaves your phone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            is SchoolCalendarImportUiState.Reading -> {
                LoadingState("Reading your calendar…")
            }

            is SchoolCalendarImportUiState.Review -> {
                LoadingState("Preparing review…")
                LaunchedEffect(Unit) {
                    navController.navigate(RevyuDestinations.SchoolCalendarReview)
                }
            }

            is SchoolCalendarImportUiState.Saving -> {
                LoadingState("Saving your calendar…")
            }

            is SchoolCalendarImportUiState.Error -> {
                EmptyState(
                    title = s.title,
                    body = s.message,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    action = {
                        PrimaryButton(
                            text = "Try Another File",
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

/** Picker + review share one graph-scoped ViewModel, exactly like the study-load import. */
@Composable
fun rememberSchoolCalendarViewModel(navController: NavHostController): SchoolCalendarImportViewModel {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val graphEntry = androidx.compose.runtime.remember(navController) {
        navController.getBackStackEntry(RevyuDestinations.SchoolCalendarImportGraphPattern)
    }
    return viewModel(
        graphEntry,
        factory = viewModelFactory {
            SchoolCalendarImportViewModel(
                context = context,
                extractor = container.fileTextExtractor,
                ocr = container.schoolCalendarOcr,
                schoolCalendarRepository = container.schoolCalendarRepository
            )
        }
    )
}