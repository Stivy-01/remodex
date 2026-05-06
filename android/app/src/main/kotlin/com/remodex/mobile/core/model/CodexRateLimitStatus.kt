package com.remodex.mobile.core.model

import java.time.Instant
import kotlin.math.max
import kotlin.math.min

data class CodexRateLimitWindow(
    val usedPercent: Int,
    val windowDurationMins: Int? = null,
    val resetsAt: Instant? = null,
) {
    val clampedUsedPercent: Int get() = min(100, max(0, usedPercent))

    val remainingPercent: Int get() = max(0, 100 - clampedUsedPercent)
}

data class CodexRateLimitDisplayRow(
    val id: String,
    val label: String,
    val window: CodexRateLimitWindow,
)

data class CodexRateLimitBucket(
    val limitId: String,
    val limitName: String? = null,
    val primary: CodexRateLimitWindow? = null,
    val secondary: CodexRateLimitWindow? = null,
) {
    val id: String get() = limitId

    val primaryOrSecondary: CodexRateLimitWindow? get() = primary ?: secondary

    val displayRows: List<CodexRateLimitDisplayRow>
        get() {
            val rows = mutableListOf<CodexRateLimitDisplayRow>()
            primary?.let {
                rows.add(
                    CodexRateLimitDisplayRow(
                        id = "$limitId-primary",
                        label = labelForWindow(it, limitName ?: limitId),
                        window = it,
                    ),
                )
            }
            secondary?.let {
                rows.add(
                    CodexRateLimitDisplayRow(
                        id = "$limitId-secondary",
                        label = labelForWindow(it, limitName ?: limitId),
                        window = it,
                    ),
                )
            }
            return rows
        }

    val sortDurationMins: Int get() = primaryOrSecondary?.windowDurationMins ?: Int.MAX_VALUE

    val displayLabel: String
        get() {
            durationLabel(primaryOrSecondary?.windowDurationMins)?.let { return it }
            val trimmedName = limitName?.trim()
            if (!trimmedName.isNullOrEmpty()) return trimmedName
            return limitId
        }

    companion object {
        fun visibleDisplayRows(buckets: List<CodexRateLimitBucket>): List<CodexRateLimitDisplayRow> {
            val rows = buckets.flatMap { it.displayRows }
            val deduped = LinkedHashMap<String, CodexRateLimitDisplayRow>()
            for (row in rows) {
                val existing = deduped[row.label]
                deduped[row.label] =
                    if (existing == null) {
                        row
                    } else {
                        preferredDisplayRow(existing, row)
                    }
            }
            return deduped.values.sortedWith { lhs, rhs ->
                val ld = lhs.window.windowDurationMins ?: Int.MAX_VALUE
                val rd = rhs.window.windowDurationMins ?: Int.MAX_VALUE
                if (ld != rd) {
                    ld.compareTo(rd)
                } else {
                    lhs.label.compareTo(rhs.label, ignoreCase = true)
                }
            }
        }

        private fun labelForWindow(
            window: CodexRateLimitWindow,
            fallback: String,
        ): String = durationLabel(window.windowDurationMins) ?: fallback

        private fun preferredDisplayRow(
            current: CodexRateLimitDisplayRow,
            candidate: CodexRateLimitDisplayRow,
        ): CodexRateLimitDisplayRow {
            if (candidate.window.clampedUsedPercent != current.window.clampedUsedPercent) {
                return if (candidate.window.clampedUsedPercent > current.window.clampedUsedPercent) {
                    candidate
                } else {
                    current
                }
            }
            return when {
                current.window.resetsAt == null && candidate.window.resetsAt != null -> candidate
                current.window.resetsAt != null && candidate.window.resetsAt == null -> current
                current.window.resetsAt != null && candidate.window.resetsAt != null ->
                    if (candidate.window.resetsAt!!.isBefore(current.window.resetsAt)) candidate else current
                else -> current
            }
        }

        private fun durationLabel(minutes: Int?): String? {
            if (minutes == null || minutes <= 0) return null
            val weekMinutes = 7 * 24 * 60
            val dayMinutes = 24 * 60
            return when {
                minutes % weekMinutes == 0 -> if (minutes == weekMinutes) "Weekly" else "${minutes / weekMinutes}w"
                minutes % dayMinutes == 0 -> "${minutes / dayMinutes}d"
                minutes % 60 == 0 -> "${minutes / 60}h"
                else -> "${minutes}m"
            }
        }
    }
}
