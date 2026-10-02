package com.revyu.app.ui.widgets

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revyu.app.R
import com.revyu.app.data.local.entities.pinnableWidgetKeys
import com.revyu.app.data.local.entities.practiceWidgetKeys
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.widget.RevyuWidgetProvider

@Composable
fun WidgetsTabScreen(
    onOpenStudySet: (String) -> Unit = {},
    onOpenExam: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.appSettingsStore.settings.collectAsState(initial = com.revyu.app.core.preferences.AppSettings())
    val viewModel: WidgetsViewModel = viewModel(
        factory = viewModelFactory { WidgetsViewModel(container.widgetDataRepository) }
    )
    val prefs by viewModel.prefs.collectAsState()
    val enabledPrefs = prefs.filter { it.enabled }
    val hiddenPrefs = prefs.filter { !it.enabled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        com.revyu.app.ui.components.RevyuTopHeader(
            headerDrawableRes = com.revyu.app.R.drawable.widgets_header,
            onOpenSettings = onOpenSettings,
            settings = settings
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LauncherWidgetCard(
                    onPin = {
                        WidgetPinController.pin(
                            context,
                            ComponentName(context.packageName, "com.revyu.app.widget.RevyuWidgetProvider")
                        )
                    },
                    onRefresh = { RevyuWidgetProvider.refreshAll(context) },
                    openStudySet = onOpenStudySet,
                    openExam = onOpenExam
                )
            }

            item {
                PracticeLauncherWidgetsSection()
            }

            item {
                Text(
                    "On your Home grid",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(enabledPrefs, key = { p -> p.key }) { pref ->
                val isPractice = pref.key in practiceWidgetKeys
                HomeWidgetCard(
                    pref = pref,
                    showToggle = true,
                    onToggle = { viewModel.toggleEnabled(pref.key) },
                    fullWidthPreview = isPractice,
                    pinContent = if (pref.key in pinnableWidgetKeys) {
                        { PinnedSetPicker(pref.key) }
                    } else null,
                    preview = {
                        if (isPractice) PracticeWidgetPreview(key = pref.key)
                        else WidgetPreview(key = pref.key)
                    }
                )
            }

            if (hiddenPrefs.isNotEmpty()) {
                item {
                    Text(
                        "Hidden",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(hiddenPrefs, key = { it.key }) { pref ->
                    HomeWidgetCard(
                        pref = pref,
                        showToggle = true,
                        onToggle = { viewModel.toggleEnabled(pref.key) },
                        showPreview = false
                    )
                }
            }
            if (enabledPrefs.isEmpty()) {
                item {
                    Text(
                        "All Home widgets are hidden. Show at least one to see it on the Home tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LauncherWidgetCard(
    onPin: () -> Unit,
    onRefresh: () -> Unit,
    openStudySet: (String) -> Unit = {},
    openExam: (String) -> Unit = {}
) {
    val container = LocalAppContainer.current
    var smartData by remember { mutableStateOf<com.revyu.app.core.widget.SmartScheduleData?>(null) }
    LaunchedEffect(Unit) {
        smartData = container.widgetDataRepository.smartScheduleData()
    }

    MarginRuleCard(accentColor = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Launcher widget", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your schedule at a glance — 2x2, 4x2, or 4x4 on the home screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            smartData?.let { data ->
                RevyuScheduleWidgetCard(
                    data = data,
                    openStudySet = openStudySet,
                    openExam = openExam
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPin, modifier = Modifier.weight(1f)) {
                    Text("Add to home screen")
                }
                com.revyu.app.ui.components.SecondaryButton(
                    text = "Refresh",
                    onClick = onRefresh,
                    modifier = Modifier.height(40.dp)
                )
            }
        }
    }
}

@Composable
private fun PracticeLauncherWidgetsSection() {
    val context = LocalContext.current
    val widgets = listOf(
        Triple(
            R.string.practice_widget_label_flashcard,
            R.string.practice_widget_desc_flashcard,
            ComponentName(context.packageName, "com.revyu.app.widget.interactive.FlashcardWidgetProvider")
        ),
        Triple(
            R.string.practice_widget_label_question,
            R.string.practice_widget_desc_question,
            ComponentName(context.packageName, "com.revyu.app.widget.interactive.QuestionWidgetProvider")
        ),
        Triple(
            R.string.practice_widget_label_qna,
            R.string.practice_widget_desc_qna,
            ComponentName(context.packageName, "com.revyu.app.widget.interactive.QnAWidgetProvider")
        ),
        Triple(
            R.string.practice_widget_label_exam,
            R.string.practice_widget_desc_exam,
            ComponentName(context.packageName, "com.revyu.app.widget.interactive.ExamWidgetProvider")
        )
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text(
            stringResource(R.string.practice_widgets_section),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        Text(
            stringResource(R.string.practice_widgets_section_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        widgets.forEach { (labelRes, descRes, component) ->
            val label = context.getString(labelRes)
            MarginRuleCard(accentColor = MaterialTheme.colorScheme.secondary) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                context.getString(descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = {
                            WidgetPinController.pin(
                                context,
                                component,
                                fallbackMessage = "Long-press a free spot on your home screen, then Widgets \u2192 $label."
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.practice_add))
                    }
                }
            }
        }
    }
}