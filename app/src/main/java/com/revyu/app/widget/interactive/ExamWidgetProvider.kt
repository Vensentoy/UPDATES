package com.revyu.app.widget.interactive

import android.content.Context

class ExamWidgetProvider : InteractiveWidgetBase() {
    override val sessionPrefix: String = EXAM_PREFIX

    override suspend fun buildSession(appContext: Context, widgetId: Int): PracticeWidgetSession? =
        PracticeWidgetData.buildExamSession(appContext, widgetId)

    companion object {
        const val EXAM_PREFIX = "exam"
    }
}