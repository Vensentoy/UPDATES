package com.revyu.app.widget.interactive

import android.content.Context

class FlashcardWidgetProvider : InteractiveWidgetBase() {
    override val sessionPrefix: String = FLASHCARD_PREFIX

    override suspend fun buildSession(appContext: Context, widgetId: Int): PracticeWidgetSession? =
        PracticeWidgetData.buildFlashcardSession(appContext, widgetId)

    companion object {
        const val FLASHCARD_PREFIX = "flashcard"
    }
}