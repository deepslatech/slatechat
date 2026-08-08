package ch.deepslate.slatechat.velocity.network

import ch.deepslate.slatechat.common.network.NetworkMessage

/**
 * Proxy-side reply half of `/slatechat network`'s round trip (see paper's own ProxyPing) --
 * every "ping" received from a backend gets an immediate empty "pong" sent straight back down
 * the same connection it arrived on. No logging here: paper's side already reports success once
 * the pong lands, and a proxy-side log line for every single ping would just be noise the
 * console owner never asked for.
 */
object ProxyPing {

    private const val PING = "ping"
    private const val PONG = "pong"

    fun install(messenger: ProxyMessenger) {
        messenger.onMessage(PING) { connection, _ -> messenger.sendTo(connection, NetworkMessage(PONG, ByteArray(0))) }
    }
}
