package com.remodex.mobile.core.model

/**
 * Light/dark appearance override (stored locally). `system` follows [android.content.res.Configuration].
 */
enum class AppThemePreference {
    system,
    light,
    dark,
    ;

    fun isDark(systemIsDark: Boolean): Boolean =
        when (this) {
            system -> systemIsDark
            light -> false
            dark -> true
        }

    companion object {
        const val storageKey: String = "remodex.appTheme"

        val default: AppThemePreference = system

        fun fromStorage(raw: String?): AppThemePreference =
            if (raw.isNullOrBlank()) {
                default
            } else {
                runCatching { valueOf(raw) }.getOrDefault(default)
            }
    }
}
