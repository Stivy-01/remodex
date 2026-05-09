package com.remodex.mobile.beta

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object BetaDeviceInfo {
    fun appVersionName(context: Context): String =
        try {
            val pm = context.packageManager
            val pkg = context.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "0.1.0"
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).versionName ?: "0.1.0"
            }
        } catch (_: Exception) {
            "0.1.0"
        }

    fun coarseDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.trim()
        val model = Build.MODEL.trim()
        return when {
            manufacturer.isEmpty() && model.isEmpty() -> "Android"
            manufacturer.isEmpty() -> model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }.take(80)
    }

    /**
     * Stable per device + app signing key (survives reinstall). SHA-256 hex for Supabase `device_key`.
     */
    fun stableBetaDeviceKey(context: Context): String {
        val raw =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != "9774d56d682e549c" }
                ?: "unknown"
        val material = "${context.packageName}|$raw"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}

