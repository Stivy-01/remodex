package com.remodex.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageStatusRefreshPolicyTest {
    @Test
    fun shouldAutoRefresh_requires_ready_connected_status_surface() {
        assertFalse(
            UsageStatusRefreshPolicy.shouldAutoRefresh(
                sessionReady = false,
                connected = true,
                threadId = "t1",
                contextWindowUsageByThread = emptyMap(),
                hasResolvedRateLimitsSnapshot = false,
            ),
        )
        assertFalse(
            UsageStatusRefreshPolicy.shouldAutoRefresh(
                sessionReady = true,
                connected = false,
                threadId = "t1",
                contextWindowUsageByThread = emptyMap(),
                hasResolvedRateLimitsSnapshot = false,
            ),
        )
        assertTrue(
            UsageStatusRefreshPolicy.shouldAutoRefresh(
                sessionReady = true,
                connected = true,
                threadId = " ",
                contextWindowUsageByThread = emptyMap(),
                hasResolvedRateLimitsSnapshot = false,
            ),
        )
        assertFalse(
            UsageStatusRefreshPolicy.shouldAutoRefresh(
                sessionReady = true,
                connected = true,
                threadId = " ",
                contextWindowUsageByThread = emptyMap(),
                hasResolvedRateLimitsSnapshot = true,
            ),
        )
    }

    @Test
    fun shouldAutoRefresh_when_context_or_rate_limits_missing() {
        val usage = ContextWindowUsage(tokensUsed = 10, tokenLimit = 100)

        assertTrue(
            UsageStatusRefreshPolicy.shouldAutoRefresh(
                sessionReady = true,
                connected = true,
                threadId = "t1",
                contextWindowUsageByThread = emptyMap(),
                hasResolvedRateLimitsSnapshot = true,
            ),
        )
        assertTrue(
            UsageStatusRefreshPolicy.shouldAutoRefresh(
                sessionReady = true,
                connected = true,
                threadId = "t1",
                contextWindowUsageByThread = mapOf("t1" to usage),
                hasResolvedRateLimitsSnapshot = false,
            ),
        )
        assertFalse(
            UsageStatusRefreshPolicy.shouldAutoRefresh(
                sessionReady = true,
                connected = true,
                threadId = "t1",
                contextWindowUsageByThread = mapOf("t1" to usage),
                hasResolvedRateLimitsSnapshot = true,
            ),
        )
    }
}
