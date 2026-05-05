package com.remodex.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertNotEquals

class TurnTimelineCacheKeyTest {
    @Test
    fun textKey_changesWhenTextChangesEvenIfSameLength() {
        val first = TurnTimelineCacheKey.textKey("visible-prose", "alpha")
        val second = TurnTimelineCacheKey.textKey("visible-prose", "omega")
        assertNotEquals(first, second)
    }
}

