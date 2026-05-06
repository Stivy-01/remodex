package com.remodex.mobile.data

import android.content.Context
import com.remodex.mobile.core.model.AppThemePreference

object ThemePreferences {
    /** Matches [AppFontPreferences] so one SharedPreferences file drives UI options. */
    internal const val PREFS_NAME: String = "remodex_ui"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): AppThemePreference {
        val raw = prefs(context).getString(AppThemePreference.storageKey, null)
        return AppThemePreference.fromStorage(raw)
    }

    fun write(
        context: Context,
        preference: AppThemePreference,
    ) {
        prefs(context).edit().putString(AppThemePreference.storageKey, preference.name).apply()
    }
}
