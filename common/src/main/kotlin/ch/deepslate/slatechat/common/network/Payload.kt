package ch.deepslate.slatechat.common.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/**
 * Builds a [NetworkMessage.payload] one field at a time -- a thin wrapper over
 * [DataOutputStream] adding a [UUID] helper, since every subchannel built on top of the
 * transport (tell relay, socialspy, vanish sync, ...) needs to write player identity at least
 * once. Fields must be read back in the exact order they were written, same as
 * [DataOutputStream]/[DataInputStream] themselves -- there's no self-describing field tagging
 * here, deliberately: this is a private wire format between two halves of the same plugin
 * released together, not one that needs to tolerate version skew.
 */
class PayloadWriter {
    private val bytes = ByteArrayOutputStream()
    private val out = DataOutputStream(bytes)

    fun writeString(value: String): PayloadWriter = apply { out.writeUTF(value) }

    fun writeUuid(value: UUID): PayloadWriter = apply {
        out.writeLong(value.mostSignificantBits)
        out.writeLong(value.leastSignificantBits)
    }

    fun writeBoolean(value: Boolean): PayloadWriter = apply { out.writeBoolean(value) }

    /** A presence flag followed by the UUID's two longs only if present -- not a sentinel
     *  all-zero UUID, which isn't actually guaranteed unused (some ecosystems use it as a
     *  "console" sentinel of their own) and reads worse than an explicit optional at the call
     *  site anyway. */
    fun writeOptionalUuid(value: UUID?): PayloadWriter = apply {
        out.writeBoolean(value != null)
        if (value != null) {
            out.writeLong(value.mostSignificantBits)
            out.writeLong(value.leastSignificantBits)
        }
    }

    fun build(subchannel: String): NetworkMessage = NetworkMessage(subchannel, bytes.toByteArray())
}

/** Reads a [NetworkMessage.payload] back out in the same order [PayloadWriter] wrote it. */
class PayloadReader(payload: ByteArray) {
    private val input = DataInputStream(ByteArrayInputStream(payload))

    fun readString(): String = input.readUTF()

    fun readUuid(): UUID = UUID(input.readLong(), input.readLong())

    fun readBoolean(): Boolean = input.readBoolean()

    fun readOptionalUuid(): UUID? {
        val present = input.readBoolean()
        return if (present) UUID(input.readLong(), input.readLong()) else null
    }
}
