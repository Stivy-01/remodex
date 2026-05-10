package com.remodex.mobile.ui.turn

import com.remodex.mobile.core.model.CodexMessage
import com.remodex.mobile.core.model.CodexMessageKind
import com.remodex.mobile.core.model.CodexMessageRole
import com.remodex.mobile.core.model.CodexPlanState
import com.remodex.mobile.core.model.CodexPlanStep
import com.remodex.mobile.core.model.CodexPlanStepStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnPlanAccessoryCardTest {
    @Test
    fun `snapshot marks inProgress when any step is running`() {
        val message =
            basePlanMessage(
                steps =
                    listOf(
                        CodexPlanStep(step = "A", status = CodexPlanStepStatus.completed),
                        CodexPlanStep(step = "B", status = CodexPlanStepStatus.inProgress),
                    ),
            )
        val snapshot = PlanAccessorySnapshot.fromMessage(message)
        assertEquals(PlanAccessoryStatus.InProgress, snapshot.status)
        assertEquals("B", snapshot.summary)
        assertEquals("1/2", snapshot.progressText)
    }

    @Test
    fun `snapshot marks completed when all steps are complete`() {
        val message =
            basePlanMessage(
                steps =
                    listOf(
                        CodexPlanStep(step = "A", status = CodexPlanStepStatus.completed),
                        CodexPlanStep(step = "B", status = CodexPlanStepStatus.completed),
                    ),
            )
        val snapshot = PlanAccessorySnapshot.fromMessage(message)
        assertEquals(PlanAccessoryStatus.Completed, snapshot.status)
        assertEquals("B", snapshot.summary)
    }

    @Test
    fun `snapshot falls back to explanation when no steps exist`() {
        val message =
            basePlanMessage(
                steps = emptyList(),
                explanation = "Explain first",
            )
        val snapshot = PlanAccessorySnapshot.fromMessage(message)
        assertEquals(PlanAccessoryStatus.Pending, snapshot.status)
        assertEquals("Explain first", snapshot.summary)
        assertNull(snapshot.progressText)
    }

    @Test
    fun `pinned plan accepts streaming system plan`() {
        val message =
            basePlanMessage(
                isStreaming = true,
                steps = listOf(CodexPlanStep(step = "Ship baseline", status = CodexPlanStepStatus.pending)),
            )
        assertTrue(message.shouldDisplayPinnedPlanAccessory())
    }

    @Test
    fun `pinned plan rejects completed non-streaming plan`() {
        val message =
            basePlanMessage(
                isStreaming = false,
                steps = listOf(CodexPlanStep(step = "Done", status = CodexPlanStepStatus.completed)),
            )
        assertFalse(message.shouldDisplayPinnedPlanAccessory())
    }

    @Test
    fun `completed plan accepts completed non-streaming plan`() {
        val message =
            basePlanMessage(
                isStreaming = false,
                steps =
                    listOf(
                        CodexPlanStep(step = "A", status = CodexPlanStepStatus.completed),
                        CodexPlanStep(step = "B", status = CodexPlanStepStatus.completed),
                    ),
            )
        assertTrue(message.shouldDisplayCompletedPlanAccessory())
    }

    @Test
    fun `completed plan rejects active plan`() {
        val message =
            basePlanMessage(
                isStreaming = false,
                steps =
                    listOf(
                        CodexPlanStep(step = "A", status = CodexPlanStepStatus.completed),
                        CodexPlanStep(step = "B", status = CodexPlanStepStatus.inProgress),
                    ),
            )
        assertFalse(message.shouldDisplayCompletedPlanAccessory())
    }

    @Test
    fun `selection picks latest active plan message`() {
        val oldActive =
            basePlanMessage(
                id = "old",
                steps = listOf(CodexPlanStep(step = "First", status = CodexPlanStepStatus.inProgress)),
            )
        val finished =
            basePlanMessage(
                id = "finished",
                steps = listOf(CodexPlanStep(step = "Done", status = CodexPlanStepStatus.completed)),
            )
        val latestActive =
            basePlanMessage(
                id = "latest",
                steps = listOf(CodexPlanStep(step = "Second", status = CodexPlanStepStatus.pending)),
            )

        val selected = selectPinnedPlanAccessoryMessage(listOf(oldActive, finished, latestActive))
        assertEquals("latest", selected?.id)
    }

    @Test
    fun `selection returns null when no active plan exists`() {
        val completed =
            basePlanMessage(
                steps = listOf(CodexPlanStep(step = "Done", status = CodexPlanStepStatus.completed)),
            )
        assertNull(selectPinnedPlanAccessoryMessage(listOf(completed)))
    }

    @Test
    fun `completed selection picks latest completed plan message`() {
        val oldCompleted =
            basePlanMessage(
                id = "old",
                steps = listOf(CodexPlanStep(step = "Old", status = CodexPlanStepStatus.completed)),
            )
        val active =
            basePlanMessage(
                id = "active",
                steps = listOf(CodexPlanStep(step = "Doing", status = CodexPlanStepStatus.inProgress)),
            )
        val latestCompleted =
            basePlanMessage(
                id = "latest",
                steps = listOf(CodexPlanStep(step = "Done", status = CodexPlanStepStatus.completed)),
            )

        val selected = selectCompletedPlanAccessoryMessage(listOf(oldCompleted, active, latestCompleted))
        assertEquals("latest", selected?.id)
    }

    private fun basePlanMessage(
        id: String = "id",
        isStreaming: Boolean = false,
        steps: List<CodexPlanStep>,
        explanation: String = "Plan",
    ): CodexMessage =
        CodexMessage(
            id = id,
            threadId = "thread",
            role = CodexMessageRole.system,
            kind = CodexMessageKind.plan,
            text = "plan",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            isStreaming = isStreaming,
            planState = CodexPlanState(explanation = explanation, steps = steps),
        )
}
