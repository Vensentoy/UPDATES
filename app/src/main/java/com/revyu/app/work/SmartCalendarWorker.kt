package com.revyu.app.work

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.revyu.app.RevyuApplication
import kotlinx.coroutines.flow.first

/**
 * Backs the "background scheduled check" half of Smart Calendar (the other half runs on
 * app open, in HomeViewModel, against the same SmartCalendarRepository).
 */
class SmartCalendarWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RevyuApplication
        val hasApiKey = app.container.settingsRepository.hasApiKey.value
        if (!hasApiKey) return Result.success() // nothing to remind about before setup is done

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return Result.success()

        val tasks = app.container.smartCalendarRepository.observePendingTasks().first()
        tasks.forEach { task ->
            val message = app.container.smartCalendarRepository.notificationMessage(task)
            NotificationHelper.showPendingTaskNotification(applicationContext, task, message)
        }
        return Result.success()
    }
}
