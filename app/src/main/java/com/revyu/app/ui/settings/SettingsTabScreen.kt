package com.revyu.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import com.revyu.app.core.preferences.AppSettings
import com.revyu.app.core.preferences.ThemeMode
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.di.viewModelFactory
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.ChipGroup
import com.revyu.app.ui.components.SecondaryButton

@Composable
fun SettingsTabScreen(onOpenApiKey: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            SettingsViewModel(container.appSettingsStore, container.settingsRepository)
        }
    )
    val settings by viewModel.settings.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()
    val username by viewModel.username.collectAsState()
    val isLlccian by viewModel.isLlccian.collectAsState()

    SettingsTabContent(
        settings = settings,
        hasApiKey = hasApiKey,
        username = username,
        isLlccian = isLlccian,
        onOpenApiKey = onOpenApiKey,
        onResyncLlccCalendar = {
            viewModel.resyncLlccCalendar(container.schoolCalendarRepository) {
                com.revyu.app.work.WorkScheduler.refreshWidgetsNow(context)
            }
        },
        onLogout = viewModel::logout,
        onSetThemeMode = viewModel::setThemeMode,
        onSetAccentColor = { accent -> viewModel.setAccentColor(accent, context) },
        onSetAutoStudySuggestions = viewModel::setAutoStudySuggestions,
        onSetStudyBlockMinutes = { viewModel.setStudyBlockMinutes(it) },
        onSetReviewBlockMinutes = { viewModel.setReviewBlockMinutes(it) },
        onSetStartHour = { viewModel.setStartHour(it) },
        onSetEndHour = { viewModel.setEndHour(it) },
        onSetMaxMinutesPerDay = { viewModel.setMaxMinutesPerDay(it) },
        onSetWeekendStudy = viewModel::setWeekendStudy,
        onSetWidgetUpdatesEnabled = viewModel::setWidgetUpdatesEnabled
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTabContent(
    settings: AppSettings,
    hasApiKey: Boolean,
    username: String,
    isLlccian: Boolean,
    onOpenApiKey: () -> Unit,
    onResyncLlccCalendar: () -> Unit,
    onLogout: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentColor: (com.revyu.app.core.preferences.AccentColor) -> Unit,
    onSetAutoStudySuggestions: (Boolean) -> Unit,
    onSetStudyBlockMinutes: (Int) -> Unit,
    onSetReviewBlockMinutes: (Int) -> Unit,
    onSetStartHour: (Int) -> Unit,
    onSetEndHour: (Int) -> Unit,
    onSetMaxMinutesPerDay: (Int) -> Unit,
    onSetWeekendStudy: (Boolean) -> Unit,
    onSetWidgetUpdatesEnabled: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
    ) {
        com.revyu.app.ui.components.RevyuTopHeader(
            headerDrawableRes = com.revyu.app.R.drawable.settings_header,
            settings = settings
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader("Account & Institution")
            MarginRuleCard(accentColor = MaterialTheme.colorScheme.primary) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "User: ${if (username.isBlank()) "LLCC Student" else username}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (isLlccian) "Institution: Lapu-Lapu City College (LLCCian)" else "Institution: Standard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isLlccian) {
                        SecondaryButton(
                            text = "Re-sync LLCC Calendar AY 2026–2027",
                            onClick = onResyncLlccCalendar,
                            modifier = Modifier.fillMaxWidth(),
                            buttonHeight = 64.dp,
                            maxLines = 2
                        )
                    }
                    SecondaryButton(
                        text = "Log Out",
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        buttonHeight = 64.dp
                    )
                }
            }

            SectionHeader("Appearance")
            MarginRuleCard(accentColor = MaterialTheme.colorScheme.primary) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme mode", style = MaterialTheme.typography.titleMedium)
                    ChipGroup {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = {
                                    Text(
                                        mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            )
                        }
                    }

                    Text("Cat mascot & color theme", style = MaterialTheme.typography.titleMedium)
                    ChipGroup {
                        com.revyu.app.core.preferences.AccentColor.entries.forEach { accent ->
                            FilterChip(
                                selected = settings.accentColor == accent,
                                onClick = { onSetAccentColor(accent) },
                                label = {
                                    Text(
                                        accent.name.lowercase().replaceFirstChar { it.uppercase() },
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            )
                        }
                    }
                }
            }

            SectionHeader("Study suggestions")
            MarginRuleCard(accentColor = MaterialTheme.colorScheme.tertiary) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleRow(
                        label = "Auto-generate study sessions",
                        checked = settings.autoStudySuggestions,
                        onCheckedChange = onSetAutoStudySuggestions
                    )
                    StepperRow(
                        label = "Study block (min)",
                        value = settings.studyBlockMinutes,
                        onDecrease = { onSetStudyBlockMinutes(settings.studyBlockMinutes - 15) },
                        onIncrease = { onSetStudyBlockMinutes(settings.studyBlockMinutes + 15) }
                    )
                    StepperRow(
                        label = "Review block (min)",
                        value = settings.reviewBlockMinutes,
                        onDecrease = { onSetReviewBlockMinutes(settings.reviewBlockMinutes - 15) },
                        onIncrease = { onSetReviewBlockMinutes(settings.reviewBlockMinutes + 15) }
                    )
                    StepperRow(
                        label = "Preferred start hour",
                        value = settings.preferredStartHour,
                        suffix = ":00",
                        onDecrease = { onSetStartHour(settings.preferredStartHour - 1) },
                        onIncrease = { onSetStartHour(settings.preferredStartHour + 1) }
                    )
                    StepperRow(
                        label = "Preferred end hour",
                        value = settings.preferredEndHour,
                        suffix = ":00",
                        onDecrease = { onSetEndHour(settings.preferredEndHour - 1) },
                        onIncrease = { onSetEndHour(settings.preferredEndHour + 1) }
                    )
                    StepperRow(
                        label = "Max suggested min/day",
                        value = settings.maxSuggestedMinutesPerDay,
                        onDecrease = { onSetMaxMinutesPerDay(settings.maxSuggestedMinutesPerDay - 15) },
                        onIncrease = { onSetMaxMinutesPerDay(settings.maxSuggestedMinutesPerDay + 15) }
                    )
                    ToggleRow(
                        label = "Allow weekend review",
                        checked = settings.weekendStudy,
                        onCheckedChange = onSetWeekendStudy
                    )
                }
            }

            SectionHeader("Widgets")
            MarginRuleCard(accentColor = MaterialTheme.colorScheme.secondary) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow(
                        label = "Refresh widgets automatically",
                        checked = settings.widgetUpdatesEnabled,
                        onCheckedChange = onSetWidgetUpdatesEnabled
                    )
                    Text(
                        "When this is on, widgets refresh on a daily schedule plus every time your calendar data changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionHeader("AI provider")
            MarginRuleCard(
                accentColor = if (hasApiKey) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                onClick = onOpenApiKey
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OpenRouter API key", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (hasApiKey) "Key saved — tap to change" else "Not set yet — tap to add",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    suffix: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.TextButton(
                onClick = onDecrease,
                enabled = value > 1,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(
                "$value$suffix",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(48.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            androidx.compose.material3.TextButton(
                onClick = onIncrease,
                enabled = value < 240,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsTabPreview() {
    RevyuTheme {
        SettingsTabContent(
            settings = AppSettings(),
            hasApiKey = true,
            username = "llcc_student",
            isLlccian = true,
            onOpenApiKey = {},
            onResyncLlccCalendar = {},
            onLogout = {},
            onSetThemeMode = {},
            onSetAccentColor = {},
            onSetAutoStudySuggestions = {},
            onSetStudyBlockMinutes = {},
            onSetReviewBlockMinutes = {},
            onSetStartHour = {},
            onSetEndHour = {},
            onSetMaxMinutesPerDay = {},
            onSetWeekendStudy = {},
            onSetWidgetUpdatesEnabled = {}
        )
    }
}