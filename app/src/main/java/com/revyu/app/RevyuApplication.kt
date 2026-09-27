package com.revyu.app

import android.app.Application
import com.revyu.app.di.AppContainer
import com.revyu.app.work.NotificationHelper
import com.revyu.app.work.WorkScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class RevyuApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)
        WorkScheduler.scheduleDailySmartCalendarCheck(this)
        WorkScheduler.scheduleDailyWidgetRefresh(this)
    }
}
