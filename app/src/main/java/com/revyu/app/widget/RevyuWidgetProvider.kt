package com.revyu.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.revyu.app.R
import com.revyu.app.core.widget.SmartScheduleData
import com.revyu.app.work.WorkScheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Native launcher widget: today's classes + suggested study sessions. Pure RemoteViews.
 * Actual rendering is delegated to [com.revyu.app.work.WidgetRefreshWorker] (through
 * WorkManager) so we never block the main thread and updates are coalesced.
 */
class RevyuWidgetProvider : AppWidgetProvider() {

    /**
     * Bucket (layout resource) last rendered per widget. Lets us answer real resizes with a
     * re-render while ignoring the no-op [onAppWidgetOptionsChanged] echoes some launchers
     * fire back after our own [AppWidgetManager.updateAppWidget] — those report the same
     * width and used to re-enqueue forever.
     */
    private val lastLayoutByWidget = ConcurrentHashMap<Int, Int>()

    private val dateFormatter = DateTimeFormatter.ofPattern("MMMM d", Locale.US)

    /** First widget ever added — make sure it paints immediately, not on a later tick. */
    override fun onEnabled(context: Context) {
        WorkScheduler.refreshWidgetsNow(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Always commit views synchronously. Some launchers (ColorOS, etc.) treat an app
        // that only schedules an async render as "no views" and pin a persistent
        // "Problem loading widget" state — re-adding doesn't clear it. Painting a static
        // placeholder here guarantees the host has valid RemoteViews on bind; the worker
        // below replaces them with real data moments later.
        val today = LocalDate.now()
        val placeholder = SmartScheduleData(
            weekdayLabel = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
                .uppercase(Locale.US),
            dateLabel = today.format(dateFormatter),
            now = null,
            exam = null,
            next = null,
            classCountToday = 0,
            hasSchedule = false
        )
        appWidgetIds.forEach { id ->
            val layoutRes = RevyuWidgetRenderer.appWidgetManagerSizeToLayout(appWidgetManager, id)
                .takeIf { it != 0 }
                ?: R.layout.widget_schedule_medium
            appWidgetManager.updateAppWidget(
                id,
                RevyuWidgetRenderer.render(context, layoutRes, placeholder)
            )
        }
        WorkScheduler.refreshWidgetsNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val newLayout = RevyuWidgetRenderer.layoutResForMinWidth(minWidth)
        val previousLayout = lastLayoutByWidget[appWidgetId]
        if (previousLayout == null || previousLayout != newLayout) {
            lastLayoutByWidget[appWidgetId] = newLayout
            WorkScheduler.refreshWidgetsNow(context, force = true)
        }
    }

    companion object {
        /** Refresh every instance of this widget (used by the Widgets tab). */
        fun refreshAll(context: Context) {
            WorkScheduler.refreshWidgetsNow(context)
        }
    }
}