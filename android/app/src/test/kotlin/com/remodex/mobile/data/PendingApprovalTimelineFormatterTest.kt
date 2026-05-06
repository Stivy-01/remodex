package com.remodex.mobile.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PendingApprovalTimelineFormatterTest {
    @Test
    fun bodyText_joinsReasonAndCommand() {
        assertEquals(
            "Inspect\n\nCommand: git status",
            PendingApprovalTimelineFormatter.bodyText(
                "item/commandExecution/requestApproval",
                "git status",
                "Inspect",
            ),
        )
    }

    @Test
    fun bodyText_fallbackHeadline_whenEmptyReasonAndCommand() {
        assertEquals(
            "Pending approval (command)",
            PendingApprovalTimelineFormatter.bodyText(
                "item/commandExecution/requestApproval",
                null,
                null,
            ),
        )
        assertEquals(
            "Pending approval (file change)",
            PendingApprovalTimelineFormatter.bodyText(
                "item/fileChange/requestApproval",
                "",
                "   ",
            ),
        )
    }
}
