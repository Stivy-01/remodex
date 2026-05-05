package com.remodex.mobile.ui.turn

import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MermaidKnownHeightCacheTest {
    @AfterTest
    fun tearDown() {
        MermaidKnownHeightCache.clear()
    }

    @Test
    fun storesHeightBySourceAndTheme() {
        val light = MermaidHeightCacheKey("flowchart TD\nA --> B", darkMode = false)
        val dark = light.copy(darkMode = true)

        MermaidKnownHeightCache.put(light, 240.dp)
        MermaidKnownHeightCache.put(dark, 320.dp)

        assertEquals(240.dp, MermaidKnownHeightCache.get(light))
        assertEquals(320.dp, MermaidKnownHeightCache.get(dark))
    }

    @Test
    fun clampsStoredHeightsToTimelineBounds() {
        val small = MermaidHeightCacheKey("small", darkMode = false)
        val large = MermaidHeightCacheKey("large", darkMode = false)

        MermaidKnownHeightCache.put(small, 10.dp)
        MermaidKnownHeightCache.put(large, 1200.dp)

        assertEquals(120.dp, MermaidKnownHeightCache.get(small))
        assertEquals(900.dp, MermaidKnownHeightCache.get(large))
    }

    @Test
    fun evictsLeastRecentlyUsedHeights() {
        val retained = MermaidHeightCacheKey("retained", darkMode = false)
        MermaidKnownHeightCache.put(retained, 180.dp)

        repeat(96) { index ->
            MermaidKnownHeightCache.put(MermaidHeightCacheKey("source-$index", darkMode = false), 200.dp)
        }

        assertNull(MermaidKnownHeightCache.get(retained))
        assertEquals(96, MermaidKnownHeightCache.size())
    }
}
