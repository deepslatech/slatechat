package ch.deepslate.slatechat.core.network

import ch.deepslate.slatechat.common.network.PayloadReader
import ch.deepslate.slatechat.common.network.PayloadWriter
import ch.deepslate.slatechat.core.managers.TellManager
import ch.deepslate.slatechat.core.model.TellFormatting
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Every inbound tell-relay subchannel Paper listens for, registered through [ProxyMessenger]
 * (see its own doc for the Folia-safe redispatch every handler here already runs under, and for
 * why sending needs a connected [Player] as the outgoing conduit). Mirrors Velocity's own
 * `TellRelayHandler` -- see that class for the full request/vanish-check/deliver round trip
 * this is the Paper-side half of.
 */
object TellRelayClient {

    private const val RESULT = "tell-relay-result"
    private const val VANISH_QUERY = "tell-relay-vanish-query"
    private const val VANISH_REPLY = "tell-relay-vanish-reply"
    private const val DELIVER = "tell-relay-deliver"
    private const val SPY_BROADCAST = "tell-spy-broadcast"

    fun install() {
        // The correlated response to a TellRelay's own outgoing request -- handed straight to
        // PendingRequests, which already knows which sender is waiting and on which thread.
        ProxyMessenger.onMessage(RESULT) { _, message ->
            val requestId = PayloadReader(message.payload).readUuid()
            PendingRequests.complete(requestId, message)
        }

        // The proxy asking THIS server whether one of its own players is vanished, as part of
        // resolving some other server's cross-server /tell target. Answered immediately, no
        // correlation bookkeeping needed on this side -- see VanishState's own doc for the
        // metadata-flag convention this reads.
        ProxyMessenger.onMessage(VANISH_QUERY) { player, message ->
            val requestId = PayloadReader(message.payload).readUuid()
            val reply = PayloadWriter()
                .writeUuid(requestId)
                .writeBoolean(VanishState.isVanished(player))
                .build(VANISH_REPLY)
            ProxyMessenger.send(player, reply)
        }

        // The proxy delivering a cross-server tell TO one of this server's own players --
        // recipient-side of TellRelay's round trip. Also records the reply-target locally so
        // /r works back from here, symmetric to what TellManager.recordConversation does on
        // the sender's own server; console-logs and fans out to this server's own local
        // socialspy watchers too, symmetric to what Tell.deliver()/fanOutToLocalSpies do for a
        // fully-local tell -- see TellFormatting's own docs for why both need a plain
        // [senderName]/[messageText] instead of a live sender Player here.
        ProxyMessenger.onMessage(DELIVER) { recipient, message ->
            val r = PayloadReader(message.payload)
            val senderUuid = r.readUuid()
            val senderName = r.readString()
            val recipientLine = GsonComponentSerializer.gson().deserialize(r.readString())
            val messageText = r.readString()
            val spyLine = GsonComponentSerializer.gson().deserialize(r.readString())
            val exempt = r.readBoolean()

            recipient.sendMessage(recipientLine)

            // Console log always fires, regardless of exempt -- same "audit trail, not just
            // socialspy" rule Tell.deliver() applies to a fully-local tell.
            TellFormatting.writeConsole(recipient, senderName, recipient.name, messageText)

            TellManager.recordConversation(recipient.uniqueId, TellManager.ReplyTarget(senderUuid, senderName))

            if (!exempt) {
                TellFormatting.fanOutToLocalSpies(spyLine, exclude = setOf(senderUuid, recipient.uniqueId))
            }
        }
    }

    /**
     * Fire-and-forget notice to the proxy that a fully-local tell just happened, so any
     * network-scope socialspy watcher elsewhere sees it too -- see
     * [ch.deepslate.slatechat.core.model.Tell]'s own call site.
     *
     * [conduit] deliberately isn't required to be [senderUuid]'s own `Player` -- any online
     * player works as the outgoing connection (see [ProxyMessenger.send]), and the real call
     * site fires this from the *recipient's* own thread (Folia-safe: [conduit] is the only live
     * `Player` this function itself touches). Because the conduit is genuinely ambiguous here,
     * unlike every other subchannel in this feature, both identities travel explicitly in the
     * payload instead of leaning on `ServerConnection.getPlayer()` on Velocity's side.
     */
    fun notifyLocalSpy(conduit: Player, senderUuid: UUID, recipientUuid: UUID, spyLine: Component) {
        val payload = PayloadWriter()
            .writeUuid(senderUuid)
            .writeUuid(recipientUuid)
            .writeString(GsonComponentSerializer.gson().serialize(spyLine))
            .build(SPY_BROADCAST)
        ProxyMessenger.send(conduit, payload)
    }
}
