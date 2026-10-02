package com.revyu.app.data.remote

/**
 * Small in-memory holder for the OpenRouter API key.
 *
 * The source of truth is EncryptedSharedPreferences (see SettingsRepository), but OkHttp
 * interceptors run synchronously on a background thread and need the key without a suspend
 * call, so SettingsRepository pushes the current value in here on load and on every update.
 */
class ApiKeyProvider {
    @Volatile
    var apiKey: String? = null
        private set

    fun update(newKey: String?) {
        apiKey = newKey?.takeIf { it.isNotBlank() }
    }
}
