package com.revyu.app.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.revyu.app.widget.WidgetPinResultReceiver

object WidgetPinController {

    /** Asks the launcher to place [component]; API 34+ uses the pin-result callback. */
    fun pin(
        context: Context,
        component: ComponentName,
        fallbackMessage: String = "Long-press a free spot on your home screen, then Widgets."
    ) {
        val manager = AppWidgetManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getBroadcast(
                context,
                42,
                Intent(context, WidgetPinResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.requestPinAppWidget(component, Bundle(), pi)
        } else {
            Toast.makeText(context, fallbackMessage, Toast.LENGTH_LONG).show()
        }
    }
}