package com.remodex.mobile.data

import android.content.Context
import com.remodex.mobile.core.model.AppFontStyle

object AppFontPreferences {
    private const val PREFS_NAME = "remodex_ui"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readFontStyle(context: Context): AppFontStyle {
        val raw = prefs(context).getString(AppFontStyle.storageKey, null) ?: return AppFontStyle.defaultStyle
        return runCatching { AppFontStyle.valueOf(raw) }.getOrDefault(AppFontStyle.defaultStyle)
    }

    fun writeFontStyle(
        context: Context,
        style: AppFontStyle,
    ) {
        prefs(context).edit().putString(AppFontStyle.storageKey, style.name).apply()
    }
}
