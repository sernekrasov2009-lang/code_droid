package ru.slicoo.code_droid.core.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Stage-1 key storage: plain SharedPreferences, no encryption yet.
 * Stage 2 swaps the implementation for Android Keystore-backed storage
 * (spec section 8) without touching the callers.
 */
interface ApiKeyStore {
    fun read(): String?
    fun write(key: String)
    fun clear()
}

class SharedPrefsApiKeyStore(context: Context) : ApiKeyStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY_NAME, null)

    override fun write(key: String) {
        prefs.edit().putString(KEY_NAME, key).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_NAME).apply()
    }

    private companion object {
        const val PREFS_NAME = "code_droid_secrets"
        const val KEY_NAME = "openai_compatible_api_key"
    }
}
