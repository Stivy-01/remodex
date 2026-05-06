package com.remodex.mobile.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineBoundedCacheTest {
    @Test
    fun getOrPutReusesValueForSameKey() {
        val cache = TimelineBoundedCache<String, Int>(maxEntries = 2)
        var calls = 0

        val first = cache.getOrPut("a") { ++calls }
        val second = cache.getOrPut("a") { ++calls }

        assertEquals(1, first)
        assertEquals(1, second)
        assertEquals(1, calls)
    }

    @Test
    fun evictsLeastRecentlyUsedEntryBeyondLimit() {
        val cache = TimelineBoundedCache<String, Int>(maxEntries = 2)

        cache.getOrPut("a") { 1 }
        cache.getOrPut("b") { 2 }
        cache.getOrPut("a") { 10 }
        cache.getOrPut("c") { 3 }

        assertEquals(2, cache.size())
        assertEquals(1, cache.getOrPut("a") { 10 })
        assertEquals(20, cache.getOrPut("b") { 20 })
    }

    @Test
    fun clearRemovesEntries() {
        val cache = TimelineBoundedCache<String, Int>(maxEntries = 2)
        cache.getOrPut("a") { 1 }

        cache.clear()

        assertEquals(0, cache.size())
    }
}
