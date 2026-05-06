package com.remodex.mobile.ui.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnConversationPaneAutocompleteTest {
    @Test
    fun structuredMentions_roundTripThroughQueuePrefixHelpers() {
        val chips =
            listOf(
                ComposerMentionChipPayload(
                    kind = ComposerMentionKind.Skill,
                    displayLabel = "Code Review",
                    semanticValue = "code-review",
                    rawSegment = "\$code-review",
                    sourcePath = "/Users/me/.codex/skills/code-review/SKILL.md",
                ),
                ComposerMentionChipPayload(
                    kind = ComposerMentionKind.File,
                    displayLabel = "B.kt",
                    semanticValue = "src/a/B.kt",
                    rawSegment = "@src/a/B.kt",
                ),
            )

        val text = mergeMentionChipsIntoDraft("Body text", chips)
        val skillMentions = mentionChipsToSkillMentions(chips)
        val fileMentions = mentionChipsToFileMentions(chips)

        assertEquals(
            "Body text",
            stripMergedMentionPrefix(
                text = text,
                skillMentions = skillMentions,
                fileMentions = fileMentions,
            ),
        )

        val restored = restoreMentionChips(skillMentions, fileMentions)
        assertEquals(2, restored.size)
        assertEquals(ComposerMentionKind.Skill, restored[0].kind)
        assertEquals("code-review", restored[0].semanticValue)
        assertEquals("/Users/me/.codex/skills/code-review/SKILL.md", skillMentions.single().path)
        assertEquals(ComposerMentionKind.File, restored[1].kind)
        assertEquals("src/a/B.kt", restored[1].semanticValue)
    }

    @Test
    fun pluginSuggestionsBecomeStructuredMentionItems() {
        val state =
            buildComposerAutocompleteState(
                parse =
                    TrailingComposerMentionParse(
                        raw = "\$git",
                        rangeInText = 0..3,
                        payload =
                            ComposerMentionChipPayload(
                                kind = ComposerMentionKind.Skill,
                                displayLabel = "git",
                                semanticValue = "git",
                                rawSegment = "\$git",
                            ),
                    ),
                skillSuggestions =
                    listOf(
                        SkillAutocompleteSuggestion(
                            id = "github",
                            label = "GitHub",
                            path = "plugin://github@openai-curated",
                            description = "Work with GitHub",
                            marketplace = "openai-curated",
                            isPlugin = true,
                            searchBlob = "github git openai-curated",
                        ),
                    ),
                fileCandidates = emptyList(),
                isThreadRunning = false,
            )

        val item = state?.items?.single()
        assertEquals(ComposerMentionKind.Plugin, item?.payload?.kind)
        assertEquals("plugin://github@openai-curated", item?.payload?.semanticValue)

        val mentions = mentionChipsToFileMentions(listOf(item!!.payload))
        assertEquals("GitHub", mentions.single().name)
        assertEquals("plugin://github@openai-curated", mentions.single().path)
    }

    @Test
    fun slashAutocompleteIncludesCompactCommand() {
        val state =
            buildComposerAutocompleteState(
                parse =
                    TrailingComposerMentionParse(
                        raw = "/com",
                        rangeInText = 0..3,
                        payload =
                            ComposerMentionChipPayload(
                                kind = ComposerMentionKind.SlashCommand,
                                displayLabel = "com",
                                semanticValue = "com",
                                rawSegment = "/com",
                            ),
                    ),
                skillSuggestions = emptyList(),
                fileCandidates = emptyList(),
                isThreadRunning = false,
            )

        val item = state?.items?.single()
        assertEquals("/compact", item?.title)
        assertEquals(ComposerMentionKind.SlashCommand, item?.payload?.kind)
        assertEquals("compact", item?.payload?.semanticValue)
    }
}
