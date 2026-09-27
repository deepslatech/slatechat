package ch.deepslate.slatechat.common.network

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkMessageTest {

    @Test
    fun `encode then decode round-trips subchannel and payload`() {
        val original = NetworkMessage("ping", byteArrayOf(1, 2, 3, 4))

        val decoded = NetworkMessage.decode(original.encode())

        assertEquals(original, decoded)
    }

    @Test
    fun `empty payload round-trips`() {
        val original = NetworkMessage("ping", ByteArray(0))

        val decoded = NetworkMessage.decode(original.encode())

        assertEquals(original.subchannel, decoded.subchannel)
        assertTrue(decoded.payload.isEmpty())
    }

    @Test
    fun `equals compares payload contents, not array identity`() {
        val a = NetworkMessage("tell", byteArrayOf(9, 9))
        val b = NetworkMessage("tell", byteArrayOf(9, 9))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `PayloadWriter then PayloadReader round-trips every field type in order`() {
        val playerId = UUID.randomUUID()

        val message = PayloadWriter()
            .writeString("Steve")
            .writeUuid(playerId)
            .writeBoolean(true)
            .build("tell")

        val decoded = NetworkMessage.decode(message.encode())
        val reader = PayloadReader(decoded.payload)

        assertEquals("Steve", reader.readString())
        assertEquals(playerId, reader.readUuid())
        assertEquals(true, reader.readBoolean())
    }

    @Test
    fun `a UTF-heavy string still round-trips`() {
        val message = PayloadWriter().writeString("olá, mundo! ☠️").build("chat")

        val decoded = PayloadReader(NetworkMessage.decode(message.encode()).payload)

        assertEquals("olá, mundo! ☠️", decoded.readString())
    }

    @Test
    fun `writeOptionalUuid then readOptionalUuid round-trips a present value`() {
        val id = UUID.randomUUID()
        val message = PayloadWriter().writeOptionalUuid(id).build("tell-relay-request")

        val decoded = PayloadReader(NetworkMessage.decode(message.encode()).payload)

        assertEquals(id, decoded.readOptionalUuid())
    }

    @Test
    fun `writeOptionalUuid then readOptionalUuid round-trips an absent value`() {
        val message = PayloadWriter().writeOptionalUuid(null).build("tell-relay-request")

        val decoded = PayloadReader(NetworkMessage.decode(message.encode()).payload)

        assertEquals(null, decoded.readOptionalUuid())
    }

    @Test
    fun `tell-relay-request shaped payload round-trips with an optional uuid interleaved among mandatory fields`() {
        val requestId = UUID.randomUUID()
        val targetUuid = UUID.randomUUID()

        val withUuid = PayloadWriter()
            .writeUuid(requestId)
            .writeString("Alex")
            .writeOptionalUuid(targetUuid)
            .writeBoolean(true)
            .writeBoolean(false)
            .writeString("{\"text\":\"hi\"}")
            .writeString("{\"text\":\"spy\"}")
            .build("tell-relay-request")
        val withoutUuid = PayloadWriter()
            .writeUuid(requestId)
            .writeString("Alex")
            .writeOptionalUuid(null)
            .writeBoolean(true)
            .writeBoolean(false)
            .writeString("{\"text\":\"hi\"}")
            .writeString("{\"text\":\"spy\"}")
            .build("tell-relay-request")

        val decodedWithUuid = PayloadReader(NetworkMessage.decode(withUuid.encode()).payload)
        assertEquals(requestId, decodedWithUuid.readUuid())
        assertEquals("Alex", decodedWithUuid.readString())
        assertEquals(targetUuid, decodedWithUuid.readOptionalUuid())
        assertEquals(true, decodedWithUuid.readBoolean())
        assertEquals(false, decodedWithUuid.readBoolean())
        assertEquals("{\"text\":\"hi\"}", decodedWithUuid.readString())
        assertEquals("{\"text\":\"spy\"}", decodedWithUuid.readString())

        val decodedWithoutUuid = PayloadReader(NetworkMessage.decode(withoutUuid.encode()).payload)
        decodedWithoutUuid.readUuid()
        decodedWithoutUuid.readString()
        assertEquals(null, decodedWithoutUuid.readOptionalUuid())
    }

    @Test
    fun `tell-relay-result shaped payload round-trips the optional-uuid-then-echo-string pairing`() {
        val requestId = UUID.randomUUID()
        val targetUuid = UUID.randomUUID()

        val delivered = PayloadWriter()
            .writeUuid(requestId)
            .writeBoolean(true)
            .writeOptionalUuid(targetUuid)
            .writeString("Notch")
            .build("tell-relay-result")
        val notFound = PayloadWriter()
            .writeUuid(requestId)
            .writeBoolean(false)
            .writeOptionalUuid(null)
            .writeString("")
            .build("tell-relay-result")

        val decodedDelivered = PayloadReader(NetworkMessage.decode(delivered.encode()).payload)
        assertEquals(requestId, decodedDelivered.readUuid())
        assertEquals(true, decodedDelivered.readBoolean())
        assertEquals(targetUuid, decodedDelivered.readOptionalUuid())
        assertEquals("Notch", decodedDelivered.readString())

        val decodedNotFound = PayloadReader(NetworkMessage.decode(notFound.encode()).payload)
        decodedNotFound.readUuid()
        assertEquals(false, decodedNotFound.readBoolean())
        assertEquals(null, decodedNotFound.readOptionalUuid())
        assertEquals("", decodedNotFound.readString())
    }
}
