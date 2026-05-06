package com.remodex.mobile.core.config

import com.remodex.mobile.BuildConfig

object FeatureFlags {
    val designModeEnabled: Boolean
        get() = BuildConfig.DEBUG
}
