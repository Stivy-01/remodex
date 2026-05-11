package com.remodex.mobile.core.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayUrlPolicyTest {
    @Test
    fun localCleartextRelay_isAllowed() {
        val validation = assertNotNull(validateRelayUrl("ws://192.168.1.10:9000/relay"))
        assertTrue(validation.cleartext)
        assertEquals("192.168.1.10", validation.httpUrl.host)
    }

    @Test
    fun publicCleartextRelay_isRejected() {
        assertNull(validateRelayUrl("ws://relay.example.com/relay"))
        assertNull(validateRelayUrl("http://relay.example.com/relay"))
    }

    @Test
    fun publicSecureRelay_isAllowed() {
        val validation = assertNotNull(validateRelayUrl("wss://relay.example.com/relay"))
        assertEquals("https", validation.httpUrl.scheme)
        assertEquals("relay.example.com", validation.httpUrl.host)
    }

    @Test
    fun relayUrlWithUserInfo_isRejected() {
        assertNull(validateRelayUrl("wss://user:pass@relay.example.com/relay"))
    }
}
