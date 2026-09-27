package com.revyu.app.ui.import

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.revyu.app.core.util.DateTimeUtils
import com.revyu.app.core.util.schoolcalendar.EventKind
import com.revyu.app.di.LocalAppContainer
import com.revyu.app.ui.components.ErrorBanner
import com.revyu.app.ui.components.MarginRuleCard
import com.revyu.app.ui.components.PrimaryButton
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DISPLAY_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolCalendarReviewScreen(
    navController: NavHostController,
    onImported: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: SchoolCalendarImportViewModel = rememberSchoolCalendarViewModel(navController)
    val state by viewModel.state.collectAsState()

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    val review = state as? SchoolCalendarImportUiState.Review

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        CenterAlignedTopAppBar(
            title = { Text("Review & save") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (review == null) {
            Text(
                "No import in progress.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp)
            )
            return@Column
        }

        if (review.warnings.isNotEmpty()) {
            val shown = review.warnings.take(MAX_WARNINGS)
            val extra = review.warnings.size - shown.size
            ErrorBanner(
                message = "We couldn't date these — tap a card to fix dates if needed: " +
                    shown.joinToString(" ") +
                    if (extra > 0) " …and $extra more." else "",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        val confirmed = review.events.filter { it.original.kind != EventKind.CATCH_ALL }
        val auto = review.events.filter { it.original.kind == EventKind.CATCH_ALL }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (confirmed.isNotEmpty()) {
                item { SectionHeader("Confirmed") }
                itemsIndexed(
                    review.events.filter { it.original.kind != EventKind.CATCH_ALL },
                    key = { _, e -> "c" + e.original.date.toString() + e.title }
                ) { index, event ->
                    val realIndex = review.events.indexOfFirst { it === event }
                    ReviewSchoolEventCard(
                        event = event,
                        onToggleIncluded = { viewModel.toggleIncluded(realIndex) },
                        onEditDate = { editingIndex = realIndex }
                    )
                }
            }
            if (auto.isNotEmpty()) {
                item { SectionHeader("Double-check these dates") }
                itemsIndexed(
                    review.events.filter { it.original.kind == EventKind.CATCH_ALL },
                    key = { _, e -> "a" + e.original.date.toString() + e.title }
                ) { index, event ->
                    val realIndex = review.events.indexOfFirst { it === event }
                    ReviewSchoolEventCard(
                        event = event,
                        onToggleIncluded = { viewModel.toggleIncluded(realIndex) },
                        onEditDate = { editingIndex = realIndex }
                    )
                }
            }

            val selectedCount = review.events.count { it.included }
            item {
                PrimaryButton(
                    text = "Save $selectedCount event(s)",
                    onClick = { viewModel.save { onImported() } },
                    enabled = selectedCount > 0,
                    loading = state is SchoolCalendarImportUiState.Saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    editingIndex?.let { index ->
        val event = review?.events?.getOrNull(index)
        if (event != null) {
            key(index) {
                val pickerState = rememberDatePickerState(
                    initialSelectedDateMillis = event.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                )
                DatePickerDialog(
                    onDismissRequest = { editingIndex = null },
                    confirmButton = {
                        TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                val date = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                viewModel.setDate(index, date)
                            }
                            editingIndex = null
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingIndex = null }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = pickerState)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun ReviewSchoolEventCard(
    event: ReviewableSchoolEvent,
    onToggleIncluded: () -> Unit,
    onEditDate: () -> Unit
) {
    val accent = if (event.included) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    MarginRuleCard(accentColor = accent) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = event.included, onCheckedChange = { onToggleIncluded() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (event.included) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = event.included) { onEditDate() }
                ) {
                    val dateText = event.date.format(DISPLAY_DATE)
                    Text(
                        if (event.hasTime) {
                            val start = event.original.startMinute ?: 0
                            val end = event.original.endMinute ?: start
                            "$dateText · ${DateTimeUtils.formatTimeRange(start, end)}"
                        } else {
                            dateText
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Filled.EditCalendar,
                        contentDescription = "Change date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .height(16.dp)
                    )
                }
                if (event.original.kind == EventKind.CATCH_ALL) {
                    Text(
                        "Auto-detected — please verify this date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private const val MAX_WARNINGS = 8