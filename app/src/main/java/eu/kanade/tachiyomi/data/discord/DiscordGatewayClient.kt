package eu.kanade.tachiyomi.data.discord

import kotlinx.coroutines.flow.StateFlow

enum class ConnectionStatus { Disconnected, Connecting, Connected, InvalidToken }

interface DiscordGatewayClient {
    val status: StateFlow<ConnectionStatus>

    /** Opens (or re-opens) the connection; idempotent while the same token stays desired. */
    fun connect(token: String)

    /** Sends an already-encoded gateway frame; no-op while not connected. */
    fun sendFrame(frame: String)

    /** Closes the socket and stops reconnect attempts. */
    fun disconnect()
}
