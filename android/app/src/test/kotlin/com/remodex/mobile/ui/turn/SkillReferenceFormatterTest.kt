package com.remodex.mobile.ui.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillReferenceFormatterTest {
    @Test
    fun formatsMentionTokensInVisibleProse() {
        assertEquals(
            "Use Code Review before sending.",
            SkillReferenceFormatter.formatVisibleProse("Use \$code-review before sending."),
        )
    }

    @Test
    fun preservesInlineCodeMentions() {
        assertEquals(
            "Run `\$code-review` after Code Review.",
            SkillReferenceFormatter.formatVisibleProse("Run `\$code-review` after \$code-review."),
        )
    }

    @Test
    fun preservesFencedCodeBlocks() {
        val input =
            """
            Use ${'$'}code-review here.
            ```text
            ${'$'}code-review stays raw
            ```
            Then ${'$'}bug-fix.
            """.trimIndent()

        val expected =
            """
            Use Code Review here.
            ```text
            ${'$'}code-review stays raw
            ```
            Then Bug Fix.
            """.trimIndent()

        assertEquals(expected, SkillReferenceFormatter.formatVisibleProse(input))
    }

    @Test
    fun formatsExplicitSkillMarkdownLinks() {
        assertEquals(
            "Use Code Review now.",
            SkillReferenceFormatter.formatVisibleProse("Use [ignored](skill:code-review) now."),
        )
    }

    @Test
    fun leavesNormalMarkdownLinksAlone() {
        val input = "Open [docs](https://example.test/skills)."
        assertEquals(input, SkillReferenceFormatter.formatVisibleProse(input))
    }

    @Test
    fun formatsCodexSkillPathReferenceInProse() {
        val input = "See C:/Users/andre/.codex/skills/code-review/Skill.md for details."
        assertEquals("See Code Review for details.", SkillReferenceFormatter.formatVisibleProse(input))
    }

    @Test
    fun formatsAgentsSkillPathReferenceInProse() {
        val input = "Read /.agents/skills/bug-fix/Skill.md before continuing."
        assertEquals("Read Bug Fix before continuing.", SkillReferenceFormatter.formatVisibleProse(input))
    }

    @Test
    fun preservesSkillPathInsideInlineCode() {
        val input = "Use `/.codex/skills/code-review/Skill.md` and /.codex/skills/code-review/Skill.md."
        val expected = "Use `/.codex/skills/code-review/Skill.md` and Code Review."
        assertEquals(expected, SkillReferenceFormatter.formatVisibleProse(input))
    }
}
