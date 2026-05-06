package com.remodex.mobile.core.persistence

import android.content.Context

/**
 * First-run onboarding flag (parity with iOS `@AppStorage("codex.hasSeenOnboarding")`).
 */
class OnboardingPreferences(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun hasSeenOnboarding(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)

    fun setHasSeenOnboarding(value: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "remodex_onboarding"
        const val KEY_HAS_SEEN_ONBOARDING = "codex.hasSeenOnboarding"
    }
}
