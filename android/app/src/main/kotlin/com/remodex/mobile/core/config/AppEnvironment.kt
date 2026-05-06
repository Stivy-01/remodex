package com.remodex.mobile.core.config

import android.content.Context
import android.content.pm.PackageManager

/**
 * Local runtime defaults for relay / bridge endpoints.
 * Mirrors [AppEnvironment.swift](CodexMobile/CodexMobile/Services/AppEnvironment.swift): open-source
 * builds ship with no implicit hosted relay; override via manifest meta-data when needed.
 */
object AppEnvironment {
    /** Same key as iOS `Info.plist` for parity across clients. */
    const val DEFAULT_RELAY_URL_META_KEY = "PHODEX_DEFAULT_RELAY_URL"

    /**
     * Fallback when no meta-data / resource override is set.
     * Intentionally empty so pairing / QR defines the relay (local-first).
     */
    const val defaultRelayURLString: String = ""

    /**
     * Resolved relay base URL: application `<meta-data android:name="PHODEX_DEFAULT_RELAY_URL" android:value="..."/>`
     * if present and non-placeholder; otherwise [defaultRelayURLString].
     */
    fun relayBaseURL(context: Context): String {
        val raw =
            readMetaDataString(context, DEFAULT_RELAY_URL_META_KEY)
                ?: readStringResource(context, "phodex_default_relay_url")
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return defaultRelayURLString
        if (trimmed.startsWith("$(") && trimmed.endsWith(")")) {
            return defaultRelayURLString
        }
        return trimmed
    }

    private fun readMetaDataString(
        context: Context,
        key: String,
    ): String? {
        val appInfo =
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
        val bundle = appInfo.metaData ?: return null
        return bundle.getString(key)
    }

    private fun readStringResource(
        context: Context,
        name: String,
    ): String? {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
        if (resId == 0) return null
        val value = context.getString(resId).trim()
        return value.ifEmpty { null }
    }
}
