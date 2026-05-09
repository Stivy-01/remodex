package com.remodex.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.remodex.mobile.core.model.AppLanguagePreference
import com.remodex.mobile.core.model.AppThemePreference
import com.remodex.mobile.data.LanguagePreferences
import com.remodex.mobile.data.ThemePreferences
import com.remodex.mobile.core.notification.RemodexLocalNotificationPresenter
import com.remodex.mobile.ui.LocalAIChangeSetPersistence
import com.remodex.mobile.ui.LocalCodexRepository
import com.remodex.mobile.ui.RootScreen
import com.remodex.mobile.ui.theme.RemodexTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguagePreferences.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationLaunchIntent(intent)
        setContent {
            val context = LocalContext.current
            var themePref by remember { mutableStateOf(ThemePreferences.read(context)) }
            val systemDark = isSystemInDarkTheme()
            DisposableEffect(context) {
                val prefs =
                    context.applicationContext.getSharedPreferences(
                        ThemePreferences.PREFS_NAME,
                        android.content.Context.MODE_PRIVATE,
                    )
                val listener =
                    android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == AppThemePreference.storageKey) {
                            themePref = ThemePreferences.read(context)
                        } else if (key == AppLanguagePreference.storageKey) {
                            recreate()
                        }
                    }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            val darkTheme = themePref.isDark(systemDark)
            CompositionLocalProvider(
                LocalCodexRepository provides AppContainer.codexRepository,
                LocalAIChangeSetPersistence provides AppContainer.aiChangeSetPersistence,
            ) {
                RemodexTheme(darkTheme = darkTheme) {
                    RootScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationLaunchIntent(intent)
    }

    private fun handleNotificationLaunchIntent(intent: Intent?) {
        val tid =
            intent?.getStringExtra(RemodexLocalNotificationPresenter.EXTRA_THREAD_ID)?.trim()
                ?: return
        if (tid.isNotEmpty()) {
            AppContainer.setPendingOpenThreadFromNotification(tid)
        }
    }
}
