package com.remodex.mobile.ui.sidebar

import com.remodex.mobile.core.model.CodexThread
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/**
 * Parity with [SidebarRelativeTimeFormatter.swift](../../../../../../../../CodexMobile/CodexMobile/Views/Sidebar/SidebarRelativeTimeFormatter.swift).
 */
object SidebarRelativeTimeFormatter {
    fun compactLabel(
        thread: CodexThread,
        now: Instant = Instant.now(),
    ): String? {
        val reference = thread.updatedAt ?: thread.createdAt ?: return null
        return compactRelativeTime(start = reference, end = now)
    }

    fun compactRelativeTime(
        start: Instant,
        end: Instant,
    ): String {
        val seconds = max(0.0, Duration.between(start, end).seconds.toDouble())
        val minute = 60.0
        val hour = 60.0 * minute
        val day = 24.0 * hour
        val week = 7.0 * day
        val month = 30.0 * day
        val year = 365.0 * day
        return when {
            seconds >= year -> "${(seconds / year).toInt()}y"
            seconds >= month -> "${(seconds / month).toInt()}mo"
            seconds >= week -> "${(seconds / week).toInt()}w"
            seconds >= day -> "${(seconds / day).toInt()}d"
            seconds >= hour -> "${(seconds / hour).toInt()}h"
            seconds >= minute -> "${(seconds / minute).toInt()}m"
            else -> "now"
        }
    }
}
