package com.revyu.app.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import com.revyu.app.MainActivity
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.auth.LoginScreen
import com.revyu.app.ui.auth.OpeningScreen
import com.revyu.app.ui.create.CreateLandingScreen
import com.revyu.app.ui.create.CreateStudySetViewModel
import com.revyu.app.ui.create.CustomizePracticeScreen
import com.revyu.app.ui.create.CustomizeReviewerScreen
import com.revyu.app.ui.create.GeneratingScreen
import com.revyu.app.ui.create.UploadMaterialScreen
import com.revyu.app.ui.history.HistoryTabScreen
import com.revyu.app.ui.home.SmartCalendarScreen
import com.revyu.app.ui.import.StudyLoadImportScreen
import com.revyu.app.ui.import.StudyLoadReviewScreen
import com.revyu.app.ui.import.SchoolCalendarImportScreen
import com.revyu.app.ui.import.SchoolCalendarReviewScreen
import com.revyu.app.ui.onboarding.ApiKeySetupScreen
import com.revyu.app.ui.reviewer.ReviewerTabScreen
import com.revyu.app.ui.settings.SettingsTabScreen
import com.revyu.app.ui.studyset.ExamModeScreen
import com.revyu.app.ui.studyset.ResultsScreen
import com.revyu.app.ui.studyset.StudySetOverviewScreen
import com.revyu.app.ui.subject.SubjectDetailScreen
import com.revyu.app.ui.vault.SemesterVaultConfigureScreen
import com.revyu.app.ui.vault.SemesterVaultGeneratingScreen
import com.revyu.app.ui.vault.SemesterVaultSubjectsScreen
import com.revyu.app.ui.vault.SemesterVaultViewModel
import com.revyu.app.ui.widgets.WidgetsTabScreen
import com.revyu.app.work.WorkScheduler

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val BottomTabs = listOf(
    BottomTab(RevyuDestinations.SmartCalendar, "Home", Icons.Filled.DateRange),
    BottomTab(RevyuDestinations.Reviewer, "Reviewer", Icons.Filled.Add),
    BottomTab(RevyuDestinations.Widgets, "Widgets", Icons.Filled.Widgets),
    BottomTab(RevyuDestinations.History, "History", Icons.Filled.History),
    BottomTab(RevyuDestinations.Settings, "Settings", Icons.Filled.Settings)
)

@Composable
fun RevyuNavHost(
    navController: NavHostController = rememberNavController(),
    pendingDeepLinkSubjectId: String? = null,
    pendingDeepLinkStudySetId: String? = null,
    pendingOpenTab: String? = null
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current

    val isLoggedIn by container.settingsRepository.isLoggedIn.collectAsState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in RevyuDestinations.TabRoots ||
        currentRoute == RevyuDestinations.StudyLoadPicker ||
        currentRoute == RevyuDestinations.StudyLoadReview ||
        currentRoute == RevyuDestinations.SchoolCalendarPicker ||
        currentRoute == RevyuDestinations.SchoolCalendarReview

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                RevyuBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn) RevyuDestinations.SmartCalendar else RevyuDestinations.Opening,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(RevyuDestinations.Opening) {
                OpeningScreen(
                    onNavigateToLogin = {
                        navController.navigate(RevyuDestinations.Login)
                    }
                )
            }

            composable(RevyuDestinations.Login) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(RevyuDestinations.SmartCalendar) {
                            popUpTo(RevyuDestinations.Opening) { inclusive = true }
                        }
                    }
                )
            }
            val navigateToSettings: () -> Unit = {
                navController.navigate(RevyuDestinations.Settings) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            composable(RevyuDestinations.SmartCalendar) {
                SmartCalendarScreen(
                    onImportStudyLoad = {
                        navController.navigate(RevyuDestinations.StudyLoadImportGraphPattern)
                    },
                    onOpenSubject = { subjectId ->
                        navController.navigate(RevyuDestinations.subjectDetail(subjectId))
                    },
                    onOpenStudySet = { studySetId ->
                        navController.navigate(RevyuDestinations.studySetOverview(studySetId))
                    },
                    onStartExamMode = { studySetId ->
                        navController.navigate(RevyuDestinations.examMode(studySetId))
                    },
                    onOpenSettings = navigateToSettings
                )
            }

            composable(RevyuDestinations.Reviewer) {
                ReviewerTabScreen(
                    onImportStudyLoad = {
                        navController.navigate(RevyuDestinations.StudyLoadImportGraphPattern)
                    },
                    onImportSchoolCalendar = {
                        navController.navigate(RevyuDestinations.SchoolCalendarImportGraphPattern)
                    },
                    onOpenSubject = { subjectId ->
                        navController.navigate(RevyuDestinations.subjectDetail(subjectId))
                    },
                    onCreateStudySet = { subjectId ->
                        navController.navigate(RevyuDestinations.createGraph(subjectId))
                    },
                    onStartExamMode = { studySetId ->
                        navController.navigate(RevyuDestinations.examMode(studySetId))
                    },
                    onOpenSettings = navigateToSettings
                )
            }

            // Legacy "Create" tab target — still referenced by the import flow's popBackStack
            // against a stale destination; kept wired so old saved states don't dangle.
            composable(RevyuDestinations.CreateLanding) {
                CreateLandingScreen(
                    onImportStudyLoad = {
                        navController.navigate(RevyuDestinations.StudyLoadImportGraphPattern)
                    },
                    onImportSchoolCalendar = {
                        navController.navigate(RevyuDestinations.SchoolCalendarImportGraphPattern)
                    },
                    onOpenSubject = { subjectId ->
                        navController.navigate(RevyuDestinations.subjectDetail(subjectId))
                    },
                    onTakeExamMode = { studySetId ->
                        navController.navigate(RevyuDestinations.examMode(studySetId))
                    }
                )
            }

            composable(RevyuDestinations.Widgets) {
                WidgetsTabScreen(
                    onOpenStudySet = { studySetId ->
                        navController.navigate(RevyuDestinations.studySetOverview(studySetId))
                    },
                    onOpenExam = { studySetId ->
                        navController.navigate(RevyuDestinations.examMode(studySetId))
                    },
                    onOpenSettings = navigateToSettings
                )
            }

            composable(RevyuDestinations.History) {
                HistoryTabScreen(
                    onOpenSettings = navigateToSettings,
                    onOpenResults = { studySetId, attemptId ->
                        navController.navigate(RevyuDestinations.results(studySetId, attemptId))
                    },
                    onOpenStudySet = { studySetId ->
                        navController.navigate(RevyuDestinations.studySetOverview(studySetId))
                    }
                )
            }

            composable(RevyuDestinations.Settings) {
                SettingsTabScreen(
                    onOpenApiKey = { navController.navigate(RevyuDestinations.ApiKeySetup) }
                )
            }

            // Study-load import flow — graph-scoped ViewModel survives picker -> review.
            navigation(
                route = RevyuDestinations.StudyLoadImportGraphPattern,
                startDestination = RevyuDestinations.StudyLoadPicker
            ) {
                composable(RevyuDestinations.StudyLoadPicker) {
                    StudyLoadImportScreen(navController = navController)
                }
                composable(RevyuDestinations.StudyLoadReview) {
                    val importScope = rememberCoroutineScope()
                    StudyLoadReviewScreen(
                        navController = navController,
                        onImported = {
                            importScope.launch {
                                container.settingsRepository.markStudyLoadImported()
                            }
                            WorkScheduler.refreshWidgetsNow(context)
                            navController.navigate(RevyuDestinations.Reviewer) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }

            // School-calendar import flow — graph-scoped ViewModel survives picker -> review.
            navigation(
                route = RevyuDestinations.SchoolCalendarImportGraphPattern,
                startDestination = RevyuDestinations.SchoolCalendarPicker
            ) {
                composable(RevyuDestinations.SchoolCalendarPicker) {
                    SchoolCalendarImportScreen(navController = navController)
                }
                composable(RevyuDestinations.SchoolCalendarReview) {
                    SchoolCalendarReviewScreen(
                        navController = navController,
                        onImported = {
                            WorkScheduler.refreshWidgetsNow(context)
                            navController.navigate(RevyuDestinations.SmartCalendar) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }

            composable(RevyuDestinations.ApiKeySetup) {
                val canGoBack = navController.previousBackStackEntry != null
                ApiKeySetupScreen(
                    onKeySaved = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(RevyuDestinations.ApiKeySavedResultKey, true)
                        if (canGoBack) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(RevyuDestinations.SmartCalendar) {
                                popUpTo(RevyuDestinations.ApiKeySetup) { inclusive = true }
                            }
                        }
                    },
                    onBack = if (canGoBack) ({ navController.popBackStack() }) else null
                )
            }

            composable(RevyuDestinations.SubjectDetailPattern) { backStackEntry ->
                val subjectId = backStackEntry.arguments?.getString(RevyuDestinations.ArgSubjectId).orEmpty()
                SubjectDetailScreen(
                    subjectId = subjectId,
                    onBack = { navController.popBackStack() },
                    onCreateStudySet = { navController.navigate(RevyuDestinations.createGraph(subjectId)) },
                    onOpenStudySet = { studySetId -> navController.navigate(RevyuDestinations.studySetOverview(studySetId)) }
                )
            }

            // Create Study Set wizard — nested graph so one CreateStudySetViewModel instance,
            // scoped to the GRAPH's backstack entry (not any one screen's), backs all four steps.
            navigation(
                route = RevyuDestinations.CreateGraphPattern,
                startDestination = RevyuDestinations.UploadMaterial
            ) {
                composable(RevyuDestinations.UploadMaterial) { backStackEntry ->
                    val wizardViewModel = rememberWizardViewModel(navController, backStackEntry, context)
                    UploadMaterialScreen(
                        viewModel = wizardViewModel,
                        onNext = { navController.navigate(RevyuDestinations.CustomizeReviewer) },
                        onOpenExistingStudySet = { studySetId ->
                            navController.navigate(RevyuDestinations.studySetOverview(studySetId)) {
                                popUpTo(RevyuDestinations.SmartCalendar)
                            }
                        }
                    )
                }

                composable(RevyuDestinations.CustomizeReviewer) { backStackEntry ->
                    val wizardViewModel = rememberWizardViewModel(navController, backStackEntry, context)
                    CustomizeReviewerScreen(
                        viewModel = wizardViewModel,
                        onNext = { navController.navigate(RevyuDestinations.CustomizePractice) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(RevyuDestinations.CustomizePractice) { backStackEntry ->
                    val wizardViewModel = rememberWizardViewModel(navController, backStackEntry, context)
                    CustomizePracticeScreen(
                        viewModel = wizardViewModel,
                        onGenerate = { navController.navigate(RevyuDestinations.Generating) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(RevyuDestinations.Generating) { backStackEntry ->
                    val wizardViewModel = rememberWizardViewModel(navController, backStackEntry, context)
                    val apiKeySaved by backStackEntry.savedStateHandle
                        .getStateFlow(RevyuDestinations.ApiKeySavedResultKey, false)
                        .collectAsState()
                    GeneratingScreen(
                        viewModel = wizardViewModel,
                        onDone = { studySetId ->
                            navController.navigate(RevyuDestinations.studySetOverview(studySetId)) {
                                popUpTo(RevyuDestinations.SmartCalendar)
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onOpenSettings = {
                            navController.navigate(RevyuDestinations.ApiKeySetup) { launchSingleTop = true }
                        },
                        apiKeySaved = apiKeySaved,
                        onApiKeySavedHandled = {
                            backStackEntry.savedStateHandle[RevyuDestinations.ApiKeySavedResultKey] = false
                        }
                    )
                }
            }

            composable(RevyuDestinations.StudySetOverviewPattern) { backStackEntry ->
                val studySetId = backStackEntry.arguments?.getString(RevyuDestinations.ArgStudySetId).orEmpty()
                StudySetOverviewScreen(
                    studySetId = studySetId,
                    onBack = { navController.popBackStack() },
                    onStartExamMode = { navController.navigate(RevyuDestinations.examMode(studySetId)) }
                )
            }

            composable(RevyuDestinations.ExamModePattern) { backStackEntry ->
                val studySetId = backStackEntry.arguments?.getString(RevyuDestinations.ArgStudySetId).orEmpty()
                ExamModeScreen(
                    studySetId = studySetId,
                    onSubmitted = { attemptId ->
                        navController.navigate(RevyuDestinations.results(studySetId, attemptId)) {
                            popUpTo(RevyuDestinations.SmartCalendar)
                        }
                    },
                    onExit = { navController.popBackStack() }
                )
            }

            composable(RevyuDestinations.ResultsPattern) { backStackEntry ->
                val studySetId = backStackEntry.arguments?.getString(RevyuDestinations.ArgStudySetId).orEmpty()
                val attemptId = backStackEntry.arguments?.getString(RevyuDestinations.ArgAttemptId).orEmpty()
                ResultsScreen(
                    studySetId = studySetId,
                    attemptId = attemptId,
                    onDone = {
                        navController.navigate(RevyuDestinations.SmartCalendar) {
                            popUpTo(RevyuDestinations.SmartCalendar) { inclusive = true }
                        }
                    }
                )
            }

            composable(RevyuDestinations.SemesterVaultSubjects) {
                SemesterVaultSubjectsScreen(
                    onBack = { navController.popBackStack() },
                    onSubjectSelected = { subjectId -> navController.navigate(RevyuDestinations.vaultGraph(subjectId)) }
                )
            }

            // Semester Vault flow — same nested-graph-scoped-ViewModel pattern as the Create wizard.
            navigation(
                route = RevyuDestinations.VaultGraphPattern,
                startDestination = RevyuDestinations.VaultConfigure
            ) {
                composable(RevyuDestinations.VaultConfigure) { backStackEntry ->
                    val vaultViewModel = rememberVaultViewModel(navController, backStackEntry, container)
                    SemesterVaultConfigureScreen(
                        viewModel = vaultViewModel,
                        onGenerate = { navController.navigate(RevyuDestinations.VaultGenerating) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(RevyuDestinations.VaultGenerating) { backStackEntry ->
                    val vaultViewModel = rememberVaultViewModel(navController, backStackEntry, container)
                    val apiKeySaved by backStackEntry.savedStateHandle
                        .getStateFlow(RevyuDestinations.ApiKeySavedResultKey, false)
                        .collectAsState()
                    SemesterVaultGeneratingScreen(
                        viewModel = vaultViewModel,
                        onDone = { studySetId ->
                            navController.navigate(RevyuDestinations.studySetOverview(studySetId)) {
                                popUpTo(RevyuDestinations.SmartCalendar)
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onOpenSettings = {
                            navController.navigate(RevyuDestinations.ApiKeySetup) { launchSingleTop = true }
                        },
                        apiKeySaved = apiKeySaved,
                        onApiKeySavedHandled = {
                            backStackEntry.savedStateHandle[RevyuDestinations.ApiKeySavedResultKey] = false
                        }
                    )
                }
            }
        }
    }

    // Runs after the NavHost above has committed its graph, so navigating here is safe.
    LaunchedEffect(pendingDeepLinkSubjectId, pendingDeepLinkStudySetId, pendingOpenTab) {
        when {
            pendingOpenTab != null && pendingOpenTab == MainActivity.TAB_SMART_CALENDAR ->
                navController.navigate(RevyuDestinations.SmartCalendar) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            pendingDeepLinkStudySetId != null ->
                navController.navigate(RevyuDestinations.examMode(pendingDeepLinkStudySetId))
            pendingDeepLinkSubjectId != null ->
                navController.navigate(RevyuDestinations.subjectDetail(pendingDeepLinkSubjectId))
        }
    }
}

@Composable
private fun RevyuBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    NavigationBar {
        BottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.route) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

/**
 * Resolves the Create Study Set wizard's ViewModel scoped to the nested graph's own
 * backstack entry (not the individual screen's), so it survives navigation between the
 * wizard's four steps. subjectId is read off that same graph entry, since "create/{subjectId}"
 * is the graph's own route pattern and therefore holds the real argument value.
 */
@Composable
private fun rememberWizardViewModel(
    navController: NavHostController,
    screenBackStackEntry: androidx.navigation.NavBackStackEntry,
    context: android.content.Context
): CreateStudySetViewModel {
    val container = LocalAppContainer.current
    val parentEntry = remember(screenBackStackEntry) {
        navController.getBackStackEntry(RevyuDestinations.CreateGraphPattern)
    }
    val subjectId = remember(parentEntry) {
        parentEntry.arguments?.getString(RevyuDestinations.ArgSubjectId).orEmpty()
    }
    return viewModel(
        parentEntry,
        factory = viewModelFactory {
            CreateStudySetViewModel(
                subjectId = subjectId,
                context = context,
                subjectRepository = container.subjectRepository,
                studyMaterialRepository = container.studyMaterialRepository,
                studySetRepository = container.studySetRepository,
                generationRepository = container.generationRepository,
                networkObserver = container.networkObserver
            )
        }
    )
}

/** Same pattern as [rememberWizardViewModel], for the Semester Vault's configure/generate steps. */
@Composable
private fun rememberVaultViewModel(
    navController: NavHostController,
    screenBackStackEntry: androidx.navigation.NavBackStackEntry,
    container: com.revyu.app.di.AppContainer
): SemesterVaultViewModel {
    val parentEntry = remember(screenBackStackEntry) {
        navController.getBackStackEntry(RevyuDestinations.VaultGraphPattern)
    }
    val subjectId = remember(parentEntry) {
        parentEntry.arguments?.getString(RevyuDestinations.ArgSubjectId).orEmpty()
    }
    return viewModel(
        parentEntry,
        factory = viewModelFactory {
            SemesterVaultViewModel(
                subjectId = subjectId,
                subjectRepository = container.subjectRepository,
                studySetRepository = container.studySetRepository,
                generationRepository = container.generationRepository
            )
        }
    )
}