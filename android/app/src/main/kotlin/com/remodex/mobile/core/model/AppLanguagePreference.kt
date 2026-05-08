package com.remodex.mobile.core.model

enum class AppLanguagePreference {
    english,
    system,
    ;

    companion object {
        const val storageKey: String = "remodex.appLanguage"

        val default: AppLanguagePreference = english

        fun fromStorage(raw: String?): AppLanguagePreference =
            if (raw.isNullOrBlank()) {
                default
            } else {
                runCatching { valueOf(raw) }.getOrDefault(default)
            }
    }
}
