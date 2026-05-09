package com.remodex.mobile.beta

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

interface BetaKeyValueStore {
    fun getString(key: String): String?
    fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean

    fun putString(
        key: String,
        value: String?,
    )

    fun putBoolean(
        key: String,
        value: Boolean,
    )
}

class SharedPreferencesBetaKeyValueStore(
    context: Context,
) : BetaKeyValueStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = prefs.getBoolean(key, defaultValue)

    override fun putString(
        key: String,
        value: String?,
    ) {
        prefs.edit().apply {
            if (value == null) {
                remove(key)
            } else {
                putString(key, value)
            }
        }.apply()
    }

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        prefs.edit().putBoolean(key, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "remodex_beta"
    }
}

class BetaTesterStore(
    private val storage: BetaKeyValueStore,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun currentJoinState(): BetaJoinState =
        BetaJoinState(
            optedIn = storage.getBoolean(KEY_OPTED_IN, false),
            testerId = storage.getString(KEY_TESTER_ID),
            displayName = storage.getString(KEY_DISPLAY_NAME),
        )

    fun storedTesterId(): String? =
        storage.getString(KEY_TESTER_ID)?.trim()?.takeIf { it.isNotEmpty() }

    /** Persist canonical tester id + opt-in from server ([BetaTesterProfile]). */
    fun restoreFromServerProfile(profile: BetaTesterProfile) {
        storage.putString(KEY_TESTER_ID, profile.testerId.trim())
        storage.putString(KEY_DISPLAY_NAME, normalizedDisplayName(profile.displayName))
        storage.putBoolean(KEY_OPTED_IN, true)
    }

    fun getOrCreateTesterId(): String {
        val existing = storage.getString(KEY_TESTER_ID)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val id = idFactory().trim()
        storage.putString(KEY_TESTER_ID, id)
        return id
    }

    fun markOptedIn(displayName: String?) {
        storage.putBoolean(KEY_OPTED_IN, true)
        setDisplayName(displayName)
    }

    fun setDisplayName(displayName: String?) {
        storage.putString(KEY_DISPLAY_NAME, normalizedDisplayName(displayName))
    }

    companion object {
        private const val KEY_TESTER_ID = "tester_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_OPTED_IN = "opted_in"

        fun normalizedDisplayName(value: String?): String? {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return trimmed.take(20)
        }
    }
}

