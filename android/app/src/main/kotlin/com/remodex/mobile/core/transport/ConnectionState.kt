package com.remodex.mobile.core.transport

/** High-level relay + JSON-RPC session lifecycle for UI and coordinators. */
sealed class ConnectionState {
    data object Offline : ConnectionState()

    data object Connecting : ConnectionState()

    /** WebSocket is up, secure handshake finished, and `initialize` completed. */
    data object Connected : ConnectionState()

    data class Error(
        val message: String,
    ) : ConnectionState()
}
