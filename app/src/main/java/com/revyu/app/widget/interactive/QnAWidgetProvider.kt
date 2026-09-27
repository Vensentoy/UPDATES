package com.revyu.app.widget.interactive

import android.content.Context

class QnAWidgetProvider : InteractiveWidgetBase() {
    override val sessionPrefix: String = QNA_PREFIX

    override suspend fun buildSession(appContext: Context, widgetId: Int): PracticeWidgetSession? =
        PracticeWidgetData.buildQnaSession(appContext, widgetId)

    companion object {
        const val QNA_PREFIX = "qna"
    }
}