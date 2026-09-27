package com.revyu.app.data.remote

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure, provider-scoped storage for AI API keys. Everything sits in
 * EncryptedSharedPreferences so no key ever touches disk in plaintext, and the
 * [ApiKeyProvider] in-memory holder is only updated in the same write that persists.
 */
class ApiKeyManager(context: Context) {

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = createEncryptedPrefs(context.applicationContext)

    @Synchronized
    fun getKey(provider: AiProvider): String? =
        try {
            prefs.getString(providerKey(provider), null)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read key for $provider — encrypted prefs corrupt", e)
            null
        }

    @Synchronized
    fun hasKey(provider: AiProvider): Boolean = getKey(provider) != null

    @Synchronized
    fun putKey(provider: AiProvider, key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(providerKey(provider), trimmed).commit()
    }

    @Synchronized
    fun clearKey(provider: AiProvider) {
        prefs.edit().remove(providerKey(provider)).commit()
    }

    private fun providerKey(provider: AiProvider) = "api_key_${provider.id}"

    companion object {
        private const val TAG = "ApiKeyManager"
        private const val KEY_FILE = "revyu_secure_prefs"

        private fun createEncryptedPrefs(context: Context): android.content.SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return try {
                EncryptedSharedPreferences.create(
                    context,
                    KEY_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.w(TAG, "EncryptedPrefs corrupt — deleting and recreating", e)
                context.deleteSharedPreferences(KEY_FILE)
                EncryptedSharedPreferences.create(
                    context,
                    KEY_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        }
    }
}