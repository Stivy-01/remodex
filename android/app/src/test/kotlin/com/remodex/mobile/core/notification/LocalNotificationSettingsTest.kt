package com.remodex.mobile.core.notification

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalNotificationSettingsTest {
    @Test
    fun evaluatePermissionStatus_preAndroid13DoesNotRequireRuntimePermission() {
        assertEquals(
            LocalNotificationSettings.PermissionStatus.NotRequired,
            LocalNotificationSettings.evaluatePermissionStatus(
                sdkInt = 32,
                managerEnabled = true,
                runtimePermissionGranted = false,
            ),
        )
    }

    @Test
    fun evaluatePermissionStatus_android13RequiresRuntimePermissionWhenMissing() {
        assertEquals(
            LocalNotificationSettings.PermissionStatus.RuntimePermissionRequired,
            LocalNotificationSettings.evaluatePermissionStatus(
                sdkInt = 33,
                managerEnabled = true,
                runtimePermissionGranted = false,
            ),
        )
    }

    @Test
    fun evaluatePermissionStatus_detectsSystemBlockedAfterPermissionGranted() {
        assertEquals(
            LocalNotificationSettings.PermissionStatus.BlockedBySystemSettings,
            LocalNotificationSettings.evaluatePermissionStatus(
                sdkInt = 36,
                managerEnabled = false,
                runtimePermissionGranted = true,
            ),
        )
    }
}
