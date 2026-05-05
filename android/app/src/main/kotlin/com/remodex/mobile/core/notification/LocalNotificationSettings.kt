package com.remodex.mobile.core.notification

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object LocalNotificationSettings {
    enum class PermissionStatus {
        Granted,
        RuntimePermissionRequired,
        BlockedBySystemSettings,
        NotRequired,
    }

    fun canPostNotifications(context: Context): Boolean {
        return permissionStatus(context).let { status ->
            status == PermissionStatus.Granted || status == PermissionStatus.NotRequired
        }
    }

    fun permissionStatus(context: Context): PermissionStatus {
        val nm = NotificationManagerCompat.from(context)
        val managerEnabled = nm.areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= 33) {
            val granted =
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            return evaluatePermissionStatus(
                sdkInt = Build.VERSION.SDK_INT,
                managerEnabled = managerEnabled,
                runtimePermissionGranted = granted,
            )
        }
        return evaluatePermissionStatus(
            sdkInt = Build.VERSION.SDK_INT,
            managerEnabled = managerEnabled,
            runtimePermissionGranted = true,
        )
    }

    fun evaluatePermissionStatus(
        sdkInt: Int,
        managerEnabled: Boolean,
        runtimePermissionGranted: Boolean,
    ): PermissionStatus {
        if (sdkInt < 33) {
            return if (managerEnabled) PermissionStatus.NotRequired else PermissionStatus.BlockedBySystemSettings
        }
        if (!runtimePermissionGranted) return PermissionStatus.RuntimePermissionRequired
        return if (managerEnabled) PermissionStatus.Granted else PermissionStatus.BlockedBySystemSettings
    }
}
