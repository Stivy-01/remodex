package com.remodex.mobile.beta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BetaTesterStoreTest {
    @Test
    fun testerIdGeneratedOnceAndPersisted() {
        var next = 0
        val store =
            BetaTesterStore(
                storage = InMemoryBetaKeyValueStore(),
                idFactory = { "tester-${++next}" },
            )

        assertEquals("tester-1", store.getOrCreateTesterId())
        assertEquals("tester-1", store.getOrCreateTesterId())
        assertEquals(1, next)
    }

    @Test
    fun optInDefaultsFalse() {
        val store = BetaTesterStore(InMemoryBetaKeyValueStore())

        val state = store.currentJoinState()

        assertFalse(state.optedIn)
        assertNull(state.testerId)
        assertNull(state.displayName)
    }

    @Test
    fun markOptedInNormalizesNickname() {
        val store =
            BetaTesterStore(
                storage = InMemoryBetaKeyValueStore(),
                idFactory = { "tester-id" },
            )

        store.markOptedIn("  abcdefghijklmnopqrstuvwxyz  ")

        val state = store.currentJoinState()
        assertEquals(true, state.optedIn)
        assertEquals("tester-id", state.testerId)
        assertEquals("abcdefghijklmnopqrst", state.displayName)
    }
}

class InMemoryBetaKeyValueStore : BetaKeyValueStore {
    private val strings = mutableMapOf<String, String>()
    private val bools = mutableMapOf<String, Boolean>()

    override fun getString(key: String): String? = strings[key]

    override fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = bools[key] ?: defaultValue

    override fun putString(
        key: String,
        value: String?,
    ) {
        if (value == null) {
            strings.remove(key)
        } else {
            strings[key] = value
        }
    }

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        bools[key] = value
    }
}

