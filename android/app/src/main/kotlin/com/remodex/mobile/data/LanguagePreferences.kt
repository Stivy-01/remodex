package com.remodex.mobile.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.remodex.mobile.core.model.AppLanguagePreference
import java.util.Locale

object LanguagePreferences {
    private const val PREFS_NAME = "remodex_ui"
    private val englishLocale: Locale = Locale.forLanguageTag("en")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): AppLanguagePreference {
        val raw = prefs(context).getString(AppLanguagePreference.storageKey, null)
        return AppLanguagePreference.fromStorage(raw)
    }

    fun write(
        context: Context,
        preference: AppLanguagePreference,
    ) {
        prefs(context).edit().putString(AppLanguagePreference.storageKey, preference.name).apply()
    }

    fun wrapContext(context: Context): Context {
        val preference = read(context)
        if (preference == AppLanguagePreference.system) {
            Locale.setDefault(currentSystemLocale())
            return context
        }

        Locale.setDefault(englishLocale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(englishLocale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = englishLocale
        }
        return context.createConfigurationContext(config)
    }

    private fun currentSystemLocale(): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        } ?: Locale.getDefault()
}
