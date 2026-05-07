package com.remodex.mobile.ui.turn

import com.remodex.mobile.core.model.CodexPluginMetadata
import com.remodex.mobile.core.model.CodexFuzzyFileMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun atPluginAutocompleteIncludesPluginSuggestions() {
        val state =
            buildComposerAutocompleteState(
                parse = TurnComposerTrailingTokens.parseTrailingToken("@gmail"),
                skillSuggestions = emptyList(),
                pluginSuggestions =
                    listOf(
                        CodexPluginMetadata(
                            id = "gmail",
                            name = "gmail",
                            marketplaceName = "openai-curated",
                            displayName = "Gmail",
                            shortDescription = "Work with Gmail",
                            installed = true,
                        ),
                    ),
                fileCandidates = emptyList(),
                isThreadRunning = false,
            )

        val item = state?.items?.single()
        assertEquals("Plugins", state?.title)
        assertEquals(ComposerMentionKind.Plugin, item?.payload?.kind)
        assertEquals("plugin://gmail@openai-curated", item?.payload?.semanticValue)
        assertEquals("@gmail", item?.replacementText)
    }

    @Test
    fun lowercaseAtTokenCanSearchFilesByFilename() {
        val parse = TurnComposerTrailingTokens.parseTrailingToken("fix @turnv")
        val state =
            buildComposerAutocompleteState(
                parse = parse,
                skillSuggestions = emptyList(),
                fileCandidates = emptyList(),
                fileMatches =
                    listOf(
                        CodexFuzzyFileMatch(
                            root = "/repo",
                            path = "CodexMobile/Views/Turn/TurnView.swift",
                            fileName = "TurnView.swift",
                            score = 1.0,
                        ),
                    ),
                isThreadRunning = false,
            )

        assertEquals(ComposerMentionKind.File, parse?.payload?.kind)
        assertEquals("turnv", parse?.payload?.semanticValue)
        val item = state?.items?.single()
        assertEquals(ComposerMentionKind.File, item?.payload?.kind)
        assertEquals("CodexMobile/Views/Turn/TurnView.swift", item?.payload?.semanticValue)
    }

    @Test
    fun fileAutocompleteCanShareAtSurfaceWithPluginSuggestions() {
        val state =
            buildComposerAutocompleteState(
                parse = TurnComposerTrailingTokens.parseTrailingToken("use @gma"),
                skillSuggestions = emptyList(),
                pluginSuggestions =
                    listOf(
                        CodexPluginMetadata(
                            id = "gmail",
                            name = "gmail",
                            marketplaceName = "openai-curated",
                            displayName = "Gmail",
                            installed = true,
                        ),
                    ),
                fileCandidates = emptyList(),
                fileMatches =
                    listOf(
                        CodexFuzzyFileMatch(
                            root = "/repo",
                            path = "app/GmailClient.kt",
                            fileName = "GmailClient.kt",
                            score = 1.0,
                        ),
                    ),
                isThreadRunning = false,
            )

        assertEquals(listOf(ComposerMentionKind.File, ComposerMentionKind.Plugin), state?.items?.map { it.payload.kind })
    }

    @Test
    fun bareAtCanShowPluginSuggestions() {
        val state =
            buildComposerAutocompleteState(
                parse = TurnComposerTrailingTokens.parseTrailingToken("@"),
                skillSuggestions = emptyList(),
                pluginSuggestions =
                    listOf(
                        CodexPluginMetadata(
                            id = "gmail",
                            name = "gmail",
                            marketplaceName = "openai-curated",
                            installed = true,
                        ),
                    ),
                fileCandidates = emptyList(),
                isThreadRunning = false,
            )

        assertEquals("Plugins", state?.title)
        assertEquals("gmail", state?.items?.single()?.payload?.displayLabel)
    }

    @Test
    fun emailLikeTextDoesNotTriggerPluginAutocomplete() {
        assertNull(TurnComposerTrailingTokens.parseTrailingToken("email@test.com"))
    }

    @Test
    fun atTokenRejectsSwiftAttributeAndSentencePunctuation() {
        assertNull(TurnComposerTrailingTokens.parseTrailingToken("add @State"))
        assertNull(TurnComposerTrailingTokens.parseTrailingToken("fix @turnv."))
        assertNull(TurnComposerTrailingTokens.parseTrailingToken("paste @t3tools/contracts:build"))
    }

    @Test
    fun pluginChipsRoundTripThroughQueuePrefixHelpers() {
        val chips =
            listOf(
                ComposerMentionChipPayload(
                    kind = ComposerMentionKind.Plugin,
                    displayLabel = "gmail",
                    semanticValue = "plugin://gmail@openai-curated",
                    rawSegment = "@gmail",
                    sourcePath = "plugin://gmail@openai-curated",
                ),
            )

        val text = mergeMentionChipsIntoDraft("Body text", chips)
        val mentions = mentionChipsToFileMentions(chips)
        val restored = restoreMentionChips(emptyList(), mentions)

        assertEquals("@gmail\n\nBody text", text)
        assertEquals("plugin://gmail@openai-curated", mentions.single().path)
        assertEquals(ComposerMentionKind.Plugin, restored.single().kind)
        assertEquals("plugin://gmail@openai-curated", restored.single().semanticValue)
    }

    @Test
    fun pathLikeAtTokenStillUsesFileAutocomplete() {
        val parse = TurnComposerTrailingTokens.parseTrailingToken("@src/Main.kt")

        assertNotNull(parse)
        assertEquals(ComposerMentionKind.File, parse?.payload?.kind)
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
