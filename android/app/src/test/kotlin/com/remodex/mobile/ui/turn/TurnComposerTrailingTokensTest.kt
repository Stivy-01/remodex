package com.remodex.mobile.ui.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnComposerTrailingTokensTest {
    @Test
    fun parsesAtFilePath() {
        val text = "see @src/a/B.kt"
        val p = TurnComposerTrailingTokens.parseTrailingToken(text, caret = text.length)!!
        assertEquals("@src/a/B.kt", p.raw)
        assertEquals(ComposerMentionKind.File, p.payload.kind)
        assertEquals("B.kt", p.payload.displayLabel)
        assertEquals("src/a/B.kt", p.payload.semanticValue)
    }

    @Test
    fun parsesAtFilePath_ignoresTrailingPunctuation() {
        val text = "see @src/a/B.kt,"
        val p = TurnComposerTrailingTokens.parseTrailingToken(text, caret = text.length)!!
        assertEquals("@src/a/B.kt", p.raw)
        assertEquals("src/a/B.kt", p.payload.semanticValue)
    }

    @Test
    fun parsesAtFilePathWithSpacesWhenItLooksLikePath() {
        val text = "update @Codex Mobile App Plan/Codex iOS Recap TLDR.md"
        val p = TurnComposerTrailingTokens.parseTrailingToken(text, caret = text.length)!!
        assertEquals("@Codex Mobile App Plan/Codex iOS Recap TLDR.md", p.raw)
        assertEquals("Codex Mobile App Plan/Codex iOS Recap TLDR.md", p.payload.semanticValue)
    }

    @Test
    fun rejectsSwiftAttributeAndBareTerminalHandle() {
        assertNull(TurnComposerTrailingTokens.parseTrailingToken("add @State"))
        assertNull(TurnComposerTrailingTokens.parseTrailingToken("paste @remodex"))
    }

    @Test
    fun rejectsTerminalScopedTaskLabelButKeepsLineReferencedFile() {
        assertNull(TurnComposerTrailingTokens.parseTrailingToken("paste @t3tools/contracts:build"))

        val p = TurnComposerTrailingTokens.parseTrailingToken("open @Views/Turn/TurnView.swift:42")!!
        assertEquals("Views/Turn/TurnView.swift:42", p.payload.semanticValue)
    }

    @Test
    fun parsesCommonExtensionlessFile() {
        val p = TurnComposerTrailingTokens.parseTrailingToken("check @Makefile")!!
        assertEquals("Makefile", p.payload.semanticValue)
    }

    @Test
    fun parsesDollarSkillTrailing() {
        val text = "Run \$code-review"
        val p = TurnComposerTrailingTokens.parseTrailingToken(text, caret = text.length)!!
        assertEquals("\$code-review", p.raw)
        assertEquals(ComposerMentionKind.Skill, p.payload.kind)
        assertEquals("Code Review", p.payload.displayLabel)
        assertEquals("code-review", p.payload.semanticValue)
    }

    @Test
    fun parsesDollarSkillTrailing_ignoresTrailingPunctuation() {
        val text = "Run \$code-review."
        val p = TurnComposerTrailingTokens.parseTrailingToken(text, caret = text.length)!!
        assertEquals("\$code-review", p.raw)
        assertEquals("code-review", p.payload.semanticValue)
    }

    @Test
    fun rejectsPureNumericDollarToken() {
        assertNull(TurnComposerTrailingTokens.parseTrailingToken("cost is \$100"))
    }

    @Test
    fun parsesSlashCommand() {
        val text = "use /compact-edit"
        val p = TurnComposerTrailingTokens.parseTrailingToken(text, caret = text.length)!!
        assertEquals("/compact-edit", p.raw)
        assertEquals(ComposerMentionKind.SlashCommand, p.payload.kind)
        assertEquals("/Compact Edit", p.payload.displayLabel)
        assertEquals("compact-edit", p.payload.semanticValue)
    }

    @Test
    fun rejectsMentionBodyWithInlinePunctuation() {
        val text = "see @src/a/(B).kt"
        assertNull(TurnComposerTrailingTokens.parseTrailingToken(text, caret = text.length))
    }

    @Test
    fun rejectsMidToken_thatDoesNotMatchPrefixKinds() {
        assertNull(TurnComposerTrailingTokens.parseTrailingToken("hello.world", caret = 11))
    }

    @Test
    fun replaceTrailing_removesLexemeAtCaret() {
        val draft = "x @Makefile"
        val repl =
            TurnComposerTrailingTokens.replaceTrailingSegment(
                draft,
                caret = draft.length,
                replacement = "",
            )
        assertTrue(repl.replaced)
        assertEquals("x ", repl.text)
        assertEquals(2, repl.caret)
    }

    @Test
    fun trailingSegment_accountsForCaret_midString() {
        val draft = "aa @B.kt cc"
        val end = TurnComposerTrailingTokens.trailingTokenEndExclusive(draft, caret = 8)
        assertEquals(
            8,
            end,
        )
        assertEquals(
            3,
            TurnComposerTrailingTokens.trailingLexemeStart(draft, end),
        )
        val parsed = TurnComposerTrailingTokens.parseTrailingToken(draft, caret = 8)!!
        assertEquals("@B.kt", parsed.raw)
    }
}
