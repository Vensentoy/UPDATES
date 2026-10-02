package com.revyu.app.data.repository

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.revyu.app.BuildConfig
import com.revyu.app.data.remote.ApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SettingsRepository(
    context: Context,
    private val apiKeyProvider: ApiKeyProvider
) {
    private val prefs = createEncryptedPrefs(context.applicationContext)

    private val _hasApiKey = MutableStateFlow(readKey() != null)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _studyLoadImported = MutableStateFlow(readStudyLoadImported())
    val studyLoadImported: StateFlow<Boolean> = _studyLoadImported.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(readIsLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _username = MutableStateFlow(readUsername())
    val username: StateFlow<String> = _username.asStateFlow()

    private val _isLlccian = MutableStateFlow(readIsLlccian())
    val isLlccian: StateFlow<Boolean> = _isLlccian.asStateFlow()

    init {
        apiKeyProvider.update(readKey())
    }

    private fun readKey(): String? {
        val stored = try {
            prefs.getString(KEY_API_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read API key — encrypted prefs corrupt", e)
            null
        }
        if (stored != null) {
            prefs.edit().putBoolean(KEY_KEY_CONFIGURED, true).apply()
            return stored
        }
        // No key has ever been saved: seed once with the build-time default key so fresh
        // installs generate out of the box. saveApiKey/clearApiKey set KEY_KEY_CONFIGURED,
        // so once the user takes over the default never silently comes back.
        if (prefs.getBoolean(KEY_KEY_CONFIGURED, false)) return null
        val defaultKey = BuildConfig.OPENROUTER_API_KEY.takeIf { it.isNotBlank() } ?: return null
        prefs.edit()
            .putString(KEY_API_KEY, defaultKey)
            .putBoolean(KEY_KEY_CONFIGURED, true)
            .apply()
        return defaultKey
    }

    private fun readStudyLoadImported(): Boolean = try {
        prefs.getBoolean(KEY_STUDY_LOAD_IMPORTED, false)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read study load flag — encrypted prefs corrupt", e)
        false
    }

    private fun readIsLoggedIn(): Boolean = try {
        prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    } catch (e: Exception) {
        false
    }

    private fun readUsername(): String = try {
        prefs.getString(KEY_USERNAME, "") ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun readIsLlccian(): Boolean = try {
        prefs.getBoolean(KEY_IS_LLCCIAN, true)
    } catch (e: Exception) {
        true
    }

    suspend fun login(username: String, isLlccian: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USERNAME, username)
            .putBoolean(KEY_IS_LLCCIAN, isLlccian)
            .apply()
        _isLoggedIn.value = true
        _username.value = username
        _isLlccian.value = isLlccian
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
        _isLoggedIn.value = false
    }

    suspend fun saveApiKey(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).putBoolean(KEY_KEY_CONFIGURED, true).apply()
        apiKeyProvider.update(key.trim())
        _hasApiKey.value = true
    }

    suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_API_KEY).putBoolean(KEY_KEY_CONFIGURED, true).apply()
        apiKeyProvider.update(null)
        _hasApiKey.value = false
    }

    suspend fun markStudyLoadImported() = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_STUDY_LOAD_IMPORTED, true).apply()
        _studyLoadImported.value = true
    }

    companion object {
        private const val TAG = "SettingsRepository"
        private const val PREFS_FILE = "revyu_secure_prefs"
        private const val KEY_API_KEY = "openrouter_api_key"
        private const val KEY_KEY_CONFIGURED = "openrouter_api_key_configured"
        private const val KEY_STUDY_LOAD_IMPORTED = "study_load_imported"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USERNAME = "username"
        private const val KEY_IS_LLCCIAN = "is_llccian"

        private fun createEncryptedPrefs(context: Context): android.content.SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                try {
                    EncryptedSharedPreferences.create(
                        context,
                        PREFS_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "EncryptedPrefs corrupt — deleting and recreating", e)
                    context.deleteSharedPreferences(PREFS_FILE)
                    EncryptedSharedPreferences.create(
                        context,
                        PREFS_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "KeyStore unavailable (e.g. preview mode) — using standard SharedPreferences fallback", t)
                context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            }
        }
    }
}
