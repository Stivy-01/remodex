package com.remodex.mobile.ui.shell

import com.remodex.mobile.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingRequestPresentationTest {
    @Test
    fun approvalKindTitleRes_mapsKnownMethods() {
        assertEquals(
            R.string.approval_kind_command,
            PendingRequestPresentation.approvalKindTitleRes("item/commandExecution/requestApproval"),
        )
        assertEquals(
            R.string.approval_kind_file_change,
            PendingRequestPresentation.approvalKindTitleRes("item/fileChange/requestApproval"),
        )
        assertEquals(
            R.string.approval_kind_tool,
            PendingRequestPresentation.approvalKindTitleRes("desktop/custom/requestApproval"),
        )
        assertEquals(
            R.string.approval_kind_apply_patch,
            PendingRequestPresentation.approvalKindTitleRes("item/tool/apply_patch/requestApproval"),
        )
    }

    @Test
    fun supportsAcceptForSession_trueForCommandExecutionVariants() {
        assertTrue(
            PendingRequestPresentation.supportsAcceptForSession("item/commandExecution/requestApproval"),
        )
        assertTrue(
            PendingRequestPresentation.supportsAcceptForSession("item/command_execution/request_approval"),
        )
    }

    @Test
    fun supportsAcceptForSession_falseForOtherApprovals() {
        assertFalse(
            PendingRequestPresentation.supportsAcceptForSession("item/fileChange/requestApproval"),
        )
        assertFalse(
            PendingRequestPresentation.supportsAcceptForSession("desktop/custom/requestApproval"),
        )
    }

    @Test
    fun approvalMessageOrNull_joinsReasonAndCommand() {
        assertNull(
            PendingRequestPresentation.approvalMessageOrNull(null, null),
        )
        assertEquals(
            "Inspect\n\nCommand: git status",
            PendingRequestPresentation.approvalMessageOrNull("Inspect", "Command: git status"),
        )
        assertEquals(
            "Inspect",
            PendingRequestPresentation.approvalMessageOrNull("Inspect", null),
        )
    }
}
