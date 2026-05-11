package com.remodex.mobile.core.transport

import com.remodex.mobile.core.model.SecureErrorMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json

/**
 * FIFO delivery of secure wire control kinds, matching the buffer/waiter behavior in
 * [CodexService+SecureTransport.swift](CodexMobile/CodexMobile/Services/CodexService+SecureTransport.swift).
 */
internal class SecureControlMultiplexer(
    private val json: Json,
    private val onSecureError: (SecureErrorMessage) -> Unit,
) {
    private val lock = Any()
    private val channels = mutableMapOf<String, Channel<String>>()

    private fun channel(kind: String): Channel<String> =
        synchronized(lock) {
            channels.getOrPut(kind) { Channel(capacity = 16) }
        }

    suspend fun receive(
        kind: String,
        timeoutMs: Long = 12_000L,
    ): String {
        val ch = channel(kind)
        return withTimeout(timeoutMs) { ch.receive() }
    }

    fun offer(
        kind: String,
        raw: String,
    ) {
        if (kind == "secureError") {
            val parsed = runCatching { json.decodeFromString<SecureErrorMessage>(raw) }.getOrNull()
            synchronized(lock) {
                val existing = channels.values.toList()
                channels.clear()
                val err =
                    CodexSecureTransportErrorEvent(
                        parsed?.message ?: "Secure relay error",
                        parsed,
                    )
                existing.forEach { ch -> ch.close(err) }
            }
            if (parsed != null) {
                onSecureError(parsed)
            }
            return
        }
        channel(kind).trySend(raw)
    }

    fun reset() {
        synchronized(lock) {
            channels.values.forEach { ch -> ch.close() }
            channels.clear()
        }
    }
}

internal class CodexSecureTransportErrorEvent(
    override val message: String,
    val payload: SecureErrorMessage?,
) : Exception(message)
