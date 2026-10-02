package com.revyu.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.revyu.app.core.navigation.RevyuNavHost
import com.revyu.app.core.preferences.AppSettings
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.di.LocalAppContainer

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_SUBJECT_ID = "open_subject_id"
        const val EXTRA_OPEN_STUDY_SET_ID = "open_study_set_id"
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_SMART_CALENDAR = "smart_calendar"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as RevyuApplication

        setContent {
            val appSettings by app.container.appSettingsStore.settings.collectAsState(initial = AppSettings())
            RevyuTheme(
                themeMode = appSettings.themeMode,
                accentColor = appSettings.accentColor
            ) {
                CompositionLocalProvider(LocalAppContainer provides app.container) {
                    val navController = rememberNavController()

                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { /* no-op — Smart Calendar reminders just won't show if denied */ }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    val pendingSubjectId = remember { intent?.getStringExtra(EXTRA_OPEN_SUBJECT_ID) }
                    val pendingStudySetId = remember { intent?.getStringExtra(EXTRA_OPEN_STUDY_SET_ID) }
                    val pendingOpenTab = remember { intent?.getStringExtra(EXTRA_OPEN_TAB) }

                    RevyuNavHost(
                        navController = navController,
                        pendingDeepLinkSubjectId = pendingSubjectId,
                        pendingDeepLinkStudySetId = pendingStudySetId,
                        pendingOpenTab = pendingOpenTab
                    )
                }
            }
        }
    }
}
