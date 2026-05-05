package com.remodex.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class CodexServiceTier {
    fast;

    val displayName: String
        get() =
            when (this) {
                fast -> "Fast"
            }

    val description: String
        get() =
            when (this) {
                fast -> "Lower latency using Codex Fast Mode."
            }

    val iconName: String
        get() =
            when (this) {
                fast -> "bolt.fill"
            }
}
