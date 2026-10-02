package com.revyu.app.work

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.revyu.app.MainActivity
import com.revyu.app.RevyuApplication
import com.revyu.app.R
import com.revyu.app.core.widget.SmartScheduleData
import com.revyu.app.widget.RevyuWidgetProvider
import com.revyu.app.widget.RevyuWidgetRenderer
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Re-renders every native Revyu widget from current Room data. Runs on any schedule that
 * touches the calendar — data change, daily tick, or manual refresh from the Widgets tab.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RevyuApplication
        val manager = AppWidgetManager.getInstance(applicationContext)

        // Interactive practice widgets (Flashcard / Quiz / Q&A / Exam).
        com.revyu.app.widget.interactive.InteractiveWidgetBase.refreshAll(applicationContext, manager)

        val ids = manager.getAppWidgetIds(
            ComponentName(applicationContext, RevyuWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return Result.success()

        // Never retry: a failed read must still paint a (helpful) state rather than leave
        // the widget sitting on its raw XML — which is what read as "blank/placeholder".
        val data = runCatching { app.container.widgetDataRepository.smartScheduleData() }
            .getOrElse { placeholderData() }
        val settings = runCatching { app.container.appSettingsStore.settings.first() }
            .getOrDefault(com.revyu.app.core.preferences.AppSettings())

        ids.forEach { widgetId ->
            // One broken widget (or a flaky size lookup) must not block the others.
            runCatching {
                val layoutRes = RevyuWidgetRenderer.appWidgetManagerSizeToLayout(manager, widgetId)
                    .takeIf { it != 0 }
                    ?: R.layout.widget_schedule_medium

                val views = RevyuWidgetRenderer.render(applicationContext, layoutRes, data, settings.accentColor).apply {
                    setOnClickPendingIntent(
                        R.id.widget_root,
                        openCalendarPendingIntent(REQUEST_CALENDAR)
                    )

                    // "Start Review →" deep-links into exam mode or that subject.
                    val exam = data.exam
                    if (exam != null && RevyuWidgetRenderer.canShowExam(layoutRes)) {
                        val pendingIntent = if (exam.studySetId != null) {
                            openStudySetPendingIntent(exam.studySetId, examRequestCode(exam.subjectId))
                        } else {
                            openSubjectPendingIntent(exam.subjectId, examRequestCode(exam.subjectId))
                        }
                        setOnClickPendingIntent(R.id.widget_exam_start, pendingIntent)
                    }

                    if (RevyuWidgetRenderer.canShowFooter(layoutRes)) {
                        setOnClickPendingIntent(
                            R.id.widget_footer_viewall,
                            openCalendarPendingIntent(REQUEST_VIEW_ALL)
                        )
                    }
                }
                manager.updateAppWidget(widgetId, views)
            }
        }
        return Result.success()
    }

    private fun placeholderData(): SmartScheduleData {
        val today = Date()
        return SmartScheduleData(
            weekdayLabel = SimpleDateFormat("EEEE", Locale.US).format(today).uppercase(Locale.US),
            dateLabel = SimpleDateFormat("MMMM d", Locale.US).format(today),
            now = null,
            exam = null,
            next = null,
            classCountToday = 0,
            hasSchedule = false
        )
    }

    private fun openCalendarPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_SMART_CALENDAR)
        }
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openSubjectPendingIntent(subjectId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (subjectId != null) putExtra(MainActivity.EXTRA_OPEN_SUBJECT_ID, subjectId)
        }
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openStudySetPendingIntent(studySetId: String, requestCode: Int): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_STUDY_SET_ID, studySetId)
        }
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Distinct request code per subject so two exams never overwrite each other's intent. */
    private fun examRequestCode(subjectId: String?): Int =
        REQUEST_EXAM_BASE + (subjectId?.hashCode() ?: 0)

    private companion object {
        const val REQUEST_CALENDAR = 1001
        const val REQUEST_VIEW_ALL = 1002
        const val REQUEST_EXAM_BASE = 2000
    }
}