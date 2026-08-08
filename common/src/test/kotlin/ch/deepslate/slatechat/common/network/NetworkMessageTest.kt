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
}
