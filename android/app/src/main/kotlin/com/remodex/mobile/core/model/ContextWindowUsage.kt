package com.remodex.mobile.core.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ContextWindowUsage(
    val tokensUsed: Int,
    val tokenLimit: Int,
) {
    companion object {
        val Zero = ContextWindowUsage(tokensUsed = 0, tokenLimit = 0)
    }

    val tokensRemaining: Int get() = max(0, tokenLimit - tokensUsed)

    val fractionUsed: Double
        get() =
            if (tokenLimit <= 0) {
                0.0
            } else {
                min(1.0, tokensUsed.toDouble() / tokenLimit.toDouble())
            }

    val percentUsed: Int get() = (fractionUsed * 100).roundToInt()

    val percentRemaining: Int get() = max(0, 100 - percentUsed)

    val tokensUsedFormatted: String get() = formatTokenCount(tokensUsed)

    val tokenLimitFormatted: String get() = formatTokenCount(tokenLimit)

    private fun formatTokenCount(count: Int): String =
        when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> {
                val value = count / 1_000.0
                if (value % 1.0 == 0.0) "${value.toInt()}K" else String.format("%.1fK", value)
            }
            else -> count.toString()
        }
}
