package com.remodex.mobile.ui.turn

import com.remodex.mobile.core.model.TurnCodeCommentDirectiveParsing
import com.remodex.mobile.core.model.TurnCodeCommentDirectiveFormatter
import com.remodex.mobile.core.model.TurnCodeCommentDirectiveFinding
import com.remodex.mobile.core.model.TurnThinkingDisclosureHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeCommentDirectiveParsingTest {

    @Test
    fun validDirective_extractsStructuredFinding_andCleansFallbackText() {
        val outcome =
            TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(
                """See ::code-comment{title="Bug" body="Fix this" file="app/src/Foo.kt" start=10 end=12 priority=1 confidence=0.75} notes here.""",
            )

        assertEquals(1, outcome.findings.size)
        val finding = outcome.findings.single()
        assertEquals("app/src/Foo.kt|10|12|Bug", finding.id)
        assertEquals("Bug", finding.title)
        assertEquals("Fix this", finding.body)
        assertEquals("app/src/Foo.kt", finding.file)
        assertEquals(10, finding.startLine)
        assertEquals(12, finding.endLine)
        assertEquals(1, finding.priority)
        assertEquals(0.75, finding.confidence ?: -1.0, 0.0)
        assertTrue(outcome.hasFindings)
        assertEquals("See notes here.", outcome.cleanedText)
    }

    @Test
    fun titlePrefixInfersPriority_andIsRemovedFromDisplayedTitle() {
        val outcome =
            TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(
                """::code-comment{title="[P1] Foo" body="Body" file="Foo.kt"}""",
            )

        val finding = outcome.findings.single()
        assertEquals("Foo", finding.title)
        assertEquals(1, finding.priority)
        assertEquals("Foo.kt|-1|-1|Foo", finding.id)
    }

    @Test
    fun explicitPriorityWinsOverTitlePrefix() {
        val outcome =
            TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(
                """::code-comment{title="[P1] Foo" body="Body" file="Foo.kt" priority=3}""",
            )

        val finding = outcome.findings.single()
        assertEquals("Foo", finding.title)
        assertEquals(3, finding.priority)
    }

    @Test
    fun multipleDirectivesPreserveOrder_andStripAll() {
        val outcome =
            TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(
                """a ::code-comment{title="One" body="A" file="one.kt"} b ::code-comment{title="Two" body="B" file="two.kt"}c""",
            )

        assertEquals(2, outcome.findings.size)
        assertEquals(listOf("One", "Two"), outcome.findings.map { it.title })
        assertEquals("a b c", outcome.cleanedText)
    }

    @Test
    fun quotedBodyWithCommasAndSpaces_isParsedAsSingleValue() {
        val outcome =
            TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(
                """::code-comment{title="Comma" body="Fix this, then that, and keep spaces" file="Foo.kt"}""",
            )

        assertEquals("Fix this, then that, and keep spaces", outcome.findings.single().body)
    }

    @Test
    fun quotedEscapes_areNormalized() {
        val outcome =
            TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(
                """::code-comment{title="Quote \"ok\"" body="Path C:\\tmp" file="Foo.kt"}""",
            )

        val finding = outcome.findings.single()
        assertEquals("Quote \"ok\"", finding.title)
        assertEquals("Path C:\\tmp", finding.body)
    }

    @Test
    fun missingRequiredFields_doNotCreateFinding_andDirectiveIsRemoved() {
        val outcome =
            TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(
                """Before ::code-comment{title="Only title" body="Body"} after""",
            )

        assertTrue(outcome.findings.isEmpty())
        assertFalse(outcome.hasFindings)
        assertEquals("Before after", outcome.cleanedText)
    }

    @Test
    fun malformedOptionalValue_doesNotCreateFinding_andDirectiveIsRemoved() {
        val outcome =
            TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(
                """Before ::code-comment{title="Bad" body="Body" file="Foo.kt" start=ten} after""",
            )

        assertTrue(outcome.findings.isEmpty())
        assertEquals("Before after", outcome.cleanedText)
    }

    @Test
    fun malformedAttribute_doesNotCreateFinding_andDirectiveIsRemoved() {
        val outcome =
            TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(
                """Before ::code-comment{title="Bad" @ body="Body" file="Foo.kt"} after""",
            )

        assertTrue(outcome.findings.isEmpty())
        assertEquals("Before after", outcome.cleanedText)
    }

    @Test
    fun residualMarkdownBeforeAndAfterDirective_isPreserved() {
        val outcome =
            TurnCodeCommentDirectiveParsing.parseCodeCommentDirectives(
                """**Before** ::code-comment{title="Bug" body="Body" file="Foo.kt"} [After](Foo.kt)""",
            )

        assertEquals("**Before** [After](Foo.kt)", outcome.cleanedText)
    }

    @Test
    fun copyFormatter_reconstructsValidDirective() {
        val finding =
            TurnCodeCommentDirectiveFinding(
                id = "Foo.kt|10|12|Bug",
                title = "Bug",
                body = "Fix this",
                file = "Foo.kt",
                startLine = 10,
                endLine = 12,
                priority = 2,
                confidence = 0.5,
            )

        assertEquals(
            """::code-comment{title="Bug" body="Fix this" file="Foo.kt" start=10 end=12 priority=2 confidence=0.5}""",
            TurnCodeCommentDirectiveFormatter.format(finding),
        )
    }

    @Test
    fun copyFormatter_omitsNullOptionals() {
        val finding =
            TurnCodeCommentDirectiveFinding(
                id = "Foo.kt|-1|-1|Bug",
                title = "Bug",
                body = "Fix this",
                file = "Foo.kt",
                startLine = null,
                endLine = null,
                priority = null,
                confidence = null,
            )

        assertEquals(
            """::code-comment{title="Bug" body="Fix this" file="Foo.kt"}""",
            TurnCodeCommentDirectiveFormatter.format(finding),
        )
    }

    @Test
    fun copyFormatter_escapesQuotesAndBackslashes() {
        val finding =
            TurnCodeCommentDirectiveFinding(
                id = "C:\\Foo.kt|-1|-1|Quote",
                title = "Quote \"ok\"",
                body = "Path C:\\tmp",
                file = "C:\\Foo.kt",
                startLine = null,
                endLine = null,
                priority = null,
                confidence = null,
            )

        assertEquals(
            """::code-comment{title="Quote \"ok\"" body="Path C:\\tmp" file="C:\\Foo.kt"}""",
            TurnCodeCommentDirectiveFormatter.format(finding),
        )
    }

    @Test
    fun thinkingTags_stripped_balanced_simple() {
        val raw =
            """Before <thinking>
            peek
            </thinking> after"""
                .trimIndent()
        val stripped = TurnThinkingDisclosureHints.stripSimpleThinkingTags(raw)
        assertEquals("Before peek after", stripped.lines().joinToString(" ").trim())
    }

    @Test
    fun disclosure_excerpt_truncates() {
        val long = "a".repeat(500)
        val ex = TurnThinkingDisclosureHints.disclosureExcerpt(long, maxChars = 10)
        assertEquals(11, ex.length)
        assertTrue(ex.endsWith("…"))
    }
}
