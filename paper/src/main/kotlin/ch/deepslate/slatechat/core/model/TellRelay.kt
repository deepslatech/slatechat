package ch.deepslate.slatechat.core.model

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.common.network.NetworkMessage
import ch.deepslate.slatechat.common.network.PayloadReader
import ch.deepslate.slatechat.common.network.PayloadWriter
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.managers.TellManager
import ch.deepslate.slatechat.core.network.PendingRequests
import ch.deepslate.slatechat.core.network.ProxyMessenger
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.entity.Player
import java.util.UUID

/**
 * The cross-server half of /tell -- constructed by
 * [ch.deepslate.slatechat.commands.TellCommand] whenever a target can't be resolved on this
 * server. Mirrors [Tell]'s validate()/prepare()/send() chain exactly (both share
 * [TellFormatting] for everything up through rendering), but delivery is an async round trip
 * through the proxy instead of a direct recipient handoff -- see Velocity's own
 * `TellRelayHandler` for the vanish-check-then-deliver flow this kicks off, and
 * [ch.deepslate.slatechat.core.network.TellRelayClient] for the inbound half of that round trip.
 *
 * [targetQuery]/[targetUuid]: exactly one is meaningful per call -- a fresh `/tell <name>` only
 * has a typed name, a `/r` to a since-disconnected-from-this-server partner only has
 * [TellManager]'s cached UUID. [displayTargetName] is always present (the typed name, or the
 * cached partner's name for `/r`) since sender-side rendering has to happen before the network
 * round trip resolves anything at all. [notFoundMessageKey] lets `/tell` and `/r` keep their
 * own already-distinct wording (`target_offline` vs `no_reply_target`) for what's the same
 * underlying outcome here -- deliberately not unified.
 */
class TellRelay(
    private val sender: Player,
    private val targetQuery: String,
    private val targetUuid: UUID?,
    private val displayTargetName: String,
    rawMessage: String,
    private val notFoundMessageKey: String,
) {

    private var message: String = rawMessage
    private var colorPermissionDenied: Boolean = false

    fun validate(): TellRelay {
        message = TellFormatting.validate(sender, message)
        return this
    }

    fun prepare(): TellRelay {
        val prepared = TellFormatting.prepare(sender, message)
        message = prepared.message
        colorPermissionDenied = prepared.colorPermissionDenied
        return this
    }

    fun send() {
        val scheduled = sender.scheduler.execute(
            SlateChat.instance,
            { deliver() },
            { logGone("disconnected before their tell could be relayed") },
            1L
        )
        if (!scheduled) logGone("was already offline before their tell could be relayed")
    }

    /** Runs on sender's own owning thread -- see [Tell]'s own class doc for why that matters. */
    private fun deliver() {
        val safeMessage = "<bold:false><italic:false><underlined:false><strikethrough:false><obfuscated:false>$message"

        val senderLine = TellFormatting.render(sender, TellFormatting.format("format_sent", sender.name, displayTargetName, safeMessage))
        val recipientLine = TellFormatting.render(sender, TellFormatting.format("format_received", sender.name, displayTargetName, safeMessage))
        val spyLine = TellFormatting.render(sender, TellFormatting.format("format_spy", sender.name, displayTargetName, safeMessage))

        // Always logs regardless of whether the target is ever actually found across the
        // network -- matches Tell.deliver()'s own "console mirrors the attempt, not just a
        // successful delivery" timing.
        TellFormatting.writeConsole(sender, sender.name, displayTargetName, message)

        val requestId = UUID.randomUUID()
        PendingRequests.await(
            requestId,
            sender,
            onResult = { reply -> handleResult(reply, senderLine) },
            onTimeout = { sender.sendMessage(notFoundMessage()) },
        )

        val request = PayloadWriter()
            .writeUuid(requestId)
            .writeString(targetQuery)
            .writeOptionalUuid(targetUuid)
            .writeBoolean(sender.hasPermission(VANISH_BYPASS_PERMISSION))
            .writeBoolean(sender.hasPermission(SPY_EXEMPT_PERMISSION))
            .writeString(GsonComponentSerializer.gson().serialize(recipientLine))
            .writeString(GsonComponentSerializer.gson().serialize(spyLine))
            // Plain message text, on top of the two already-rendered Components above -- the
            // recipient's own backend has no live sender Player to re-render anything from (see
            // TellFormatting.writeConsole's own doc), but still needs this for its own
            // console-log audit trail on delivery. Not a new privacy exposure: the proxy already
            // has to fully deserialize recipientLine/spyLine to route them, so it can already
            // read the message content today.
            .writeString(message)
            .build(REQUEST)
        ProxyMessenger.send(sender, request)
    }

    /** Runs on sender's own thread already -- see [PendingRequests.complete]'s own doc for why
     *  no redispatch is needed here. */
    private fun handleResult(reply: NetworkMessage, senderLine: Component) {
        val r = PayloadReader(reply.payload)
        r.readUuid() // requestId -- already correlated by PendingRequests, nothing left to do with it
        val delivered = r.readBoolean()
        val targetUuid = r.readOptionalUuid()
        val targetName = r.readString()

        if (!delivered || targetUuid == null) {
            sender.sendMessage(notFoundMessage())
            return
        }

        if (colorPermissionDenied) {
            sender.sendMessage(Messages.resolve(SlateChat.instance.config, "no_permission", "<red>You do not have permission!"))
        }
        sender.sendMessage(senderLine)
        TellManager.recordConversation(sender.uniqueId, TellManager.ReplyTarget(targetUuid, targetName))
    }

    private fun notFoundMessage(): Component =
        Messages.resolve(SlateChat.tellConfig, "tell.$notFoundMessageKey", "<red>$displayTargetName is not online.")

    private fun logGone(detail: String) = DebugLog.log { "${sender.name} $detail" }

    companion object {
        private const val REQUEST = "tell-relay-request"
        private const val VANISH_BYPASS_PERMISSION = "chat.tell.vanish.bypass"
        private const val SPY_EXEMPT_PERMISSION = "chat.tell.spy.bypass"
    }
}
