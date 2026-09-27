package ch.deepslate.slatechat.core.model

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.managers.TellManager
import ch.deepslate.slatechat.core.network.TellRelayClient
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * Orchestrates a single /tell end to end for a target already known to be on this server:
 * filter -> permission-gate -> render (all via [TellFormatting], shared with [TellRelay]'s
 * cross-server sibling) -> deliver to sender+recipient -> socialspy fan-out (local, plus a
 * fire-and-forget notice to the proxy for any network-scope watcher elsewhere) -> console
 * mirror.
 *
 * Folia note, same rule [Message]/[MessageDelivery] already follow -- a player's own state
 * (permissions, PDC, sendMessage) may only be touched from the thread that currently owns
 * their region: [deliver] runs on sender's own `EntityScheduler` (everything in it reads only
 * sender's own state, or thread-safe identity fields like `.name`/`.uniqueId`), recipient's
 * `sendMessage`/`hasPermission` check happen on recipient's own scheduler in
 * [deliverToRecipient], and each socialspy watcher's own PDC read happens on *their* own
 * scheduler in [TellFormatting.fanOutToLocalSpies].
 */
class Tell(
    private val sender: Player,
    private val recipient: Player,
    rawMessage: String
) {

    private var message: String = rawMessage
    private var colorPermissionDenied: Boolean = false

    /** Step 1 — validation (filter, caps, etc) */
    fun validate(): Tell {
        message = TellFormatting.validate(sender, message)
        return this
    }

    /** Step 2 — formatting + placeholders (same permission nodes channel chat gates on) */
    fun prepare(): Tell {
        val prepared = TellFormatting.prepare(sender, message)
        message = prepared.message
        colorPermissionDenied = prepared.colorPermissionDenied
        return this
    }

    /** Step 3 — send */
    fun send() {
        val scheduled = sender.scheduler.execute(
            SlateChat.instance,
            { deliver() },
            { logGone(sender, "disconnected before their tell could be sent") },
            1L
        )
        if (!scheduled) logGone(sender, "was already offline before their tell could be sent")
    }

    /** Runs on sender's own owning thread -- see the class doc for why that matters here. */
    private fun deliver() {
        val safeMessage = "<bold:false><italic:false><underlined:false><strikethrough:false><obfuscated:false>$message"

        val senderLine = TellFormatting.render(sender, TellFormatting.format("format_sent", sender.name, recipient.name, safeMessage))
        val recipientLine = TellFormatting.render(sender, TellFormatting.format("format_received", sender.name, recipient.name, safeMessage))
        val spyLine = TellFormatting.render(sender, TellFormatting.format("format_spy", sender.name, recipient.name, safeMessage))

        if (colorPermissionDenied) {
            sender.sendMessage(Messages.resolve(SlateChat.instance.config, "no_permission", "<red>You do not have permission!"))
        }
        sender.sendMessage(senderLine)

        // Console log always fires, regardless of chat.tell.spy.bypass -- that permission hides
        // a tell from other players' socialspy, not from the server owner's own audit trail.
        TellFormatting.writeConsole(sender, sender.name, recipient.name, message)

        TellManager.recordConversation(sender, recipient)

        deliverToRecipient(recipientLine, spyLine)
    }

    /** Runs on recipient's own owning thread -- safe to call sendMessage/hasPermission on
     *  them here. */
    private fun deliverToRecipient(recipientLine: Component, spyLine: Component) {
        val scheduled = recipient.scheduler.execute(
            SlateChat.instance,
            {
                recipient.sendMessage(recipientLine)

                // Exempt if either side of the conversation opted out of being spied on --
                // covers a staff member being messaged by a regular player too, not just
                // staff-initiated tells.
                val exempt = sender.hasPermission(SPY_EXEMPT_PERMISSION) || recipient.hasPermission(SPY_EXEMPT_PERMISSION)
                if (!exempt) {
                    TellFormatting.fanOutToLocalSpies(spyLine, exclude = setOf(sender.uniqueId, recipient.uniqueId))
                    // Recipient is the conduit here (not sender) -- we're already on their
                    // owning thread and any online player works as the proxy's outgoing
                    // connection, see ProxyMessenger.send. Both identities travel explicitly in
                    // the payload since the conduit is genuinely ambiguous, see
                    // TellRelayClient.notifyLocalSpy's own doc.
                    TellRelayClient.notifyLocalSpy(recipient, sender.uniqueId, recipient.uniqueId, spyLine)
                }
            },
            { logGone(recipient, "disconnected before their tell could be delivered") },
            1L
        )
        if (!scheduled) logGone(recipient, "was already offline before their tell could be delivered")
    }

    private fun logGone(player: Player, detail: String) = DebugLog.log { "${player.name} $detail" }

    companion object {
        /** Hardcoded on purpose, like every other tell permission -- see TellCommand.kt's
         *  class doc for why. */
        const val SPY_EXEMPT_PERMISSION = "chat.tell.spy.bypass"
    }
}
