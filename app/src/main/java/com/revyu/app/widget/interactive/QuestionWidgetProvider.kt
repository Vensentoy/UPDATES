package com.revyu.app.widget.interactive

import android.content.Context

class QuestionWidgetProvider : InteractiveWidgetBase() {
    override val sessionPrefix: String = QUESTION_PREFIX

    override suspend fun buildSession(appContext: Context, widgetId: Int): PracticeWidgetSession? =
        PracticeWidgetData.buildChoiceSession(appContext, widgetId)

    companion object {
        const val QUESTION_PREFIX = "choice"
    }
}