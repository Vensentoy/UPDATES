package com.revyu.app.widget.interactive

import android.content.Context
import kotlinx.serialization.json.Json

object PracticeWidgetSessionStore {
    private const val PREFS = "practice_widget_sessions"
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun sessionKey(prefix: String, widgetId: Int) = "${prefix}_${widgetId}"

    fun load(context: Context, key: String): PracticeWidgetSession? {
        val raw = prefs(context).getString(key, null) ?: return null
        return runCatching {
            json.decodeFromString<PracticeWidgetSession>(raw)
        }.getOrNull()
    }

    fun save(context: Context, key: String, session: PracticeWidgetSession) {
        prefs(context).edit()
            .putString(key, json.encodeToString(PracticeWidgetSession.serializer(), session))
            .apply()
    }

    fun clear(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }
}
