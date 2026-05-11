package com.remodex.mobile.ui.turn

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownUrlPolicyTest {
    @Test
    fun allowsOnlyExpectedExternalSchemes() {
        assertTrue(MarkdownUrlPolicy.isAllowedLink("https://example.com"))
        assertTrue(MarkdownUrlPolicy.isAllowedLink("http://example.com"))
        assertTrue(MarkdownUrlPolicy.isAllowedLink("mailto:security@example.com"))

        assertFalse(MarkdownUrlPolicy.isAllowedLink("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(MarkdownUrlPolicy.isAllowedLink("file:///data/data/com.remodex.mobile/shared_prefs/store.xml"))
        assertFalse(MarkdownUrlPolicy.isAllowedLink("content://com.example.provider/item"))
        assertFalse(MarkdownUrlPolicy.isAllowedLink("javascript:alert(1)"))
        assertFalse(MarkdownUrlPolicy.isAllowedLink("./local/file.kt"))
    }
}
