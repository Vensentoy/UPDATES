package com.revyu.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the result of a [android.appwidget.AppWidgetManager.requestPinAppWidget] attempt
 * (API 34+ passes a confirmation PendingIntent). The system already animates/confirms the
 * pin on its own sheet, so we have nothing to draw — the receiver exists only to satisfy
 * the required result PendingIntent.
 */
class WidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}