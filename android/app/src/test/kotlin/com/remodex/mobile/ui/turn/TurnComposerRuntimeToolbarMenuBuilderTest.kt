package com.remodex.mobile.ui.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnComposerRuntimeToolbarMenuBuilderTest {

    @Test
    fun `omits reasoning section when only auto placeholder has no model efforts`() {
        val autoOnly =
            TurnComposerRuntimeSelectorState(
                selected =
                    TurnComposerRuntimeOption(
                        id = TURN_COMPOSER_RUNTIME_AUTO_ID,
                        label = "Auto",
                        enabled = false,
                    ),
                options =
                    listOf(TurnComposerRuntimeOption(TURN_COMPOSER_RUNTIME_AUTO_ID, "Auto", enabled = false)),
                enabled = false,
            )
        val runtime =
            TurnComposerRuntimeControlsState(
                reasoningEffort = autoOnly,
                serviceTier = sampleSpeed(),
            )
        val menu = TurnComposerRuntimeToolbarMenuBuilder.build(runtime = runtime)
        assertEquals(1, menu.sections.size)
        assertEquals("Speed", menu.sections.single().title)
    }

    @Test
    fun `includes reasoning rows for non-auto efforts with selection`() {
        val runtime =
            TurnComposerRuntimeControlsState(
                reasoningEffort =
                    TurnComposerRuntimeSelectorState(
                        selected = TurnComposerRuntimeOption(id = "high", label = "High"),
                        options =
                            listOf(
                                TurnComposerRuntimeOption(TURN_COMPOSER_RUNTIME_AUTO_ID, "Auto"),
                                TurnComposerRuntimeOption(id = "low", label = "Low"),
                                TurnComposerRuntimeOption(id = "high", label = "High"),
                            ),
                        enabled = true,
                    ),
                serviceTier = sampleSpeed(),
            )
        val menu = TurnComposerRuntimeToolbarMenuBuilder.build(runtime = runtime)
        val reasoning = menu.sections.first()
        assertEquals("Reasoning", reasoning.title)
        assertEquals(2, reasoning.items.size)
        assertEquals("low", reasoning.items[0].actionKey.id)
        assertFalse(reasoning.items[0].checked)
        assertTrue(reasoning.items[1].checked)
        assertEquals(TurnComposerToolbarActionKey.Kind.ReasoningEffort, reasoning.items[0].actionKey.kind)
    }

    @Test
    fun `speed section lists normal then tier options`() {
        val runtime =
            TurnComposerRuntimeControlsState(
                reasoningEffort = reasoningWithOneEffort(selectedId = "m"),
                serviceTier =
                    TurnComposerRuntimeSelectorState(
                        selected =
                            TurnComposerRuntimeOption(
                                id = TURN_COMPOSER_RUNTIME_AUTO_ID,
                                label = "Normal",
                            ),
                        options =
                            listOf(
                                TurnComposerRuntimeOption(TURN_COMPOSER_RUNTIME_AUTO_ID, "Normal"),
                                TurnComposerRuntimeOption(id = "fast", label = "Fast", detail = "Desc"),
                            ),
                    ),
            )
        val speed =
            TurnComposerRuntimeToolbarMenuBuilder.build(runtime = runtime).sections.last()
        assertEquals(2, speed.items.size)
        assertEquals(TURN_COMPOSER_RUNTIME_AUTO_ID, speed.items[0].actionKey.id)
        assertTrue(speed.items[0].checked)
        assertEquals("fast", speed.items[1].actionKey.id)
        assertEquals("Desc", speed.items[1].detail)
        assertEquals(TurnComposerToolbarActionKey.Kind.ServiceTierSpeed, speed.items[1].actionKey.kind)
    }

    @Test
    fun `respects custom labels`() {
        val labels =
            TurnComposerToolbarLabels(
                chatRuntimeRootTitle = "RT",
                reasoningSectionTitle = "R",
                speedSectionTitle = "S",
            )
        val runtime =
            TurnComposerRuntimeControlsState(
                reasoningEffort = reasoningWithOneEffort("x"),
                serviceTier = sampleSpeed(),
            )
        val menu = TurnComposerRuntimeToolbarMenuBuilder.build(labels = labels, runtime = runtime)
        assertEquals("RT", menu.rootTitle)
        assertEquals("R", menu.sections[0].title)
        assertEquals("S", menu.sections[1].title)
    }

    @Test
    fun `disables reasoning rows when selector disabled`() {
        val runtime =
            TurnComposerRuntimeControlsState(
                reasoningEffort =
                    TurnComposerRuntimeSelectorState(
                        selected = TurnComposerRuntimeOption(id = "a", label = "A"),
                        options =
                            listOf(
                                TurnComposerRuntimeOption(id = "a", label = "A"),
                            ),
                        enabled = false,
                    ),
                serviceTier = sampleSpeed(),
            )
        val reasoning =
            TurnComposerRuntimeToolbarMenuBuilder.build(runtime = runtime).sections.first()
        assertFalse(reasoning.items.single().enabled)
    }

    private fun sampleSpeed(): TurnComposerRuntimeSelectorState =
        TurnComposerRuntimeSelectorState(
            selected = TurnComposerRuntimeOption(TURN_COMPOSER_RUNTIME_AUTO_ID, "Normal"),
            options =
                listOf(
                    TurnComposerRuntimeOption(TURN_COMPOSER_RUNTIME_AUTO_ID, "Normal"),
                    TurnComposerRuntimeOption(id = "fast", label = "Fast"),
                ),
        )

    private fun reasoningWithOneEffort(selectedId: String): TurnComposerRuntimeSelectorState =
        TurnComposerRuntimeSelectorState(
            selected =
                TurnComposerRuntimeOption(
                    id = selectedId,
                    label = selectedId,
                ),
            options =
                listOf(
                    TurnComposerRuntimeOption(TURN_COMPOSER_RUNTIME_AUTO_ID, "Auto"),
                    TurnComposerRuntimeOption(id = selectedId, label = selectedId),
                ),
            enabled = true,
        )
}
