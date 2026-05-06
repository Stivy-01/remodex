package com.remodex.mobile.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Encrypted preferences backed by Android Keystore (AES-256-GCM), analogous to iOS Keychain.
 * Values are strings; binary payloads use Base64.
 */
class SecureStore(
    context: Context,
) {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    fun readString(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        val trimmed = raw.trim()
        return trimmed.ifEmpty { null }
    }

    fun writeString(
        key: String,
        value: String,
    ) {
        if (value.isEmpty()) {
            deleteValue(key)
            return
        }
        prefs.edit().putString(key, value).apply()
    }

    fun readData(key: String): ByteArray? {
        val b64 = readString(key) ?: return null
        return try {
            android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun writeData(
        key: String,
        value: ByteArray,
    ) {
        if (value.isEmpty()) {
            deleteValue(key)
            return
        }
        val encoded = android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP)
        writeString(key, encoded)
    }

    inline fun <reified T> readCodable(key: String): T? {
        val json = readString(key) ?: return null
        return runCatching { jsonFormat.decodeFromString(serializer<T>(), json) }.getOrNull()
    }

    inline fun <reified T> writeCodable(
        key: String,
        value: T,
    ) {
        val encoded =
            runCatching { jsonFormat.encodeToString(serializer<T>(), value) }.getOrNull()
                ?: return
        writeString(key, encoded)
    }

    fun deleteValue(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val PREFS_NAME = "remodex_secure_store"

        @PublishedApi
        internal val jsonFormat =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        private fun createPrefs(context: Context): SharedPreferences {
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
