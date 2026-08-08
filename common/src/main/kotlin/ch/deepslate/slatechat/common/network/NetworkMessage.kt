package ch.deepslate.slatechat.common.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * One message crossing the [ProxyChannel.ID] plugin-messaging channel between Paper and
 * Velocity. [subchannel] names what kind of message this is ("ping", and eventually "tell",
 * "socialspy", "vanish", ...) -- everything downstream of the transport dispatches on it, same
 * idea as BungeeCord's classic plugin-messaging convention, just without depending on
 * BungeeCord's own API to get it. [payload] is whatever bytes that subchannel's own encoding
 * wants (see [PayloadWriter]/[PayloadReader]) -- this class never looks inside it.
 *
 * A plain class, not a `data class` -- [payload] is a `ByteArray`, and a data class's generated
 * equals/hashCode would compare it by reference instead of content. Nothing here actually needs
 * value equality today (messages are only ever encoded or decoded, never compared), but leaving
 * that footgun in for whoever eventually does isn't worth the two methods a data class would
 * save here.
 */
class NetworkMessage(val subchannel: String, val payload: ByteArray) {

    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF(subchannel)
            out.writeInt(payload.size)
            out.write(payload)
        }
        return bytes.toByteArray()
    }

    override fun equals(other: Any?): Boolean =
        other is NetworkMessage && subchannel == other.subchannel && payload.contentEquals(other.payload)

    override fun hashCode(): Int = 31 * subchannel.hashCode() + payload.contentHashCode()

    override fun toString(): String = "NetworkMessage(subchannel='$subchannel', payload=${payload.size} bytes)"

    companion object {
        /** Inverse of [encode]. Throws (caught by both platforms' listeners, see their own
         *  ProxyMessenger) on anything that isn't a well-formed envelope -- a corrupt or
         *  foreign-origin message on our own channel should never be able to crash the
         *  listener thread. */
        fun decode(bytes: ByteArray): NetworkMessage =
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val subchannel = input.readUTF()
                val payload = ByteArray(input.readInt())
                input.readFully(payload)
                NetworkMessage(subchannel, payload)
            }
    }
}
