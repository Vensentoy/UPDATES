package com.revyu.app.work

import android.content.Context
import android.os.SystemClock
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val WORK_NAME = "smart_calendar_daily_check"
    private const val WIDGET_REFRESH_NAME = "widget_refresh"
    private const val WIDGET_DAILY_NAME = "widget_daily_refresh"

    /** Coalesce burst-y update callbacks (hosts can spam ACTION_APPWIDGET_UPDATE). */
    private const val REFRESH_THROTTLE_MS = 2_000L

    @Volatile
    private var lastScheduledRefreshAt = 0L

    fun scheduleDailySmartCalendarCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // purely local DB + schedule math
            .build()

        val request = PeriodicWorkRequestBuilder<SmartCalendarWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Keep the launcher widget honest even on days nothing else changes the data. */
    fun scheduleDailyWidgetRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WIDGET_DAILY_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * One-shot widget re-render (data change, resize, manual refresh). Uses KEEP so a busy
     * host can't cancel the in-flight render — that leaves the launcher on a half-painted
     * widget and makes it retry in a loop. `force` bypasses the throttle so a real resize
     * still lands even inside a burst of host callbacks.
     */
    fun refreshWidgetsNow(context: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastScheduledRefreshAt < REFRESH_THROTTLE_MS) return
        lastScheduledRefreshAt = now

        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WIDGET_REFRESH_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
