package ch.deepslate.slatechat.core.model

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Channel
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fans a formatted chat message out to every recipient via each recipient's own
 * `EntityScheduler` -- the only Folia-safe way to read their location/permissions and call
 * `sendMessage` on them, since a player's state may only be touched from the thread that
 * currently owns their region -- then gathers how many of them counted toward the sender's
 * read-receipt, reporting back on the sender's own thread once every recipient has resolved.
 *
 * Delivered, ineligible, and disconnected-mid-flight all feed the exact same completion path,
 * so one recipient leaving mid-gather can never leave the count hanging.
 *
 * A `class`, not this codebase's usual stateless `object` collaborator -- it owns real
 * per-call state (the pending/eligible counters), one instance per [dispatch] call.
 */
class MessageDelivery(
    private val sender: Player,
    private val channel: Channel,
    private val senderPosition: RecipientEligibility.Position,
    private val component: Component
) {

    /**
     * Delivers [component] to every recipient. [recipients] is expected to include [sender] --
     * same as before this redesign, the sender always gets their own line, no special-casing
     * needed. [onComplete] is guaranteed to run on sender's own owning thread, so it's safe to
     * touch `sender` again from inside it (e.g. to send the actionbar) without scheduling again.
     */
    fun dispatch(recipients: List<Player>, onComplete: (eligibleCount: Int) -> Unit) {
        if (recipients.isEmpty()) {
            finish(0, onComplete)
            return
        }

        val pending = AtomicInteger(recipients.size)
        val eligibleCount = AtomicInteger(0)

        val recipientDone: (Boolean) -> Unit = { countedTowardReceipt ->
            if (countedTowardReceipt) eligibleCount.incrementAndGet()
            if (pending.decrementAndGet() == 0) finish(eligibleCount.get(), onComplete)
        }

        recipients.forEach { recipient ->
            val scheduled = recipient.scheduler.execute(
                SlateChat.instance,
                { deliverTo(recipient, recipientDone) },
                { recipientDone(false) }, // retired: recipient disconnected before this ran
                1L
            )
            // execute() returns false only when the recipient was *already* retired at call
            // time, in which case neither callback above fires -- has to feed the same
            // completion path itself or `pending` would never reach zero.
            if (!scheduled) recipientDone(false)
        }
    }

    /** Runs on `recipient`'s own owning thread -- safe to read their location/permissions here. */
    private fun deliverTo(recipient: Player, onDone: (countedTowardReceipt: Boolean) -> Unit) {
        val eligible = channel.broadcast || RecipientEligibility.isWithinRadius(
            senderPosition,
            RecipientEligibility.Position(recipient.world.uid, recipient.location.x, recipient.location.y, recipient.location.z),
            channel.radius
        )

        if (!eligible) {
            onDone(false)
            return
        }

        recipient.sendMessage(component)
        onDone(recipient.uniqueId != sender.uniqueId && !recipient.hasPermission("chat.count.bypass"))
    }

    private fun finish(eligibleCount: Int, onComplete: (Int) -> Unit) {
        val scheduled = sender.scheduler.execute(
            SlateChat.instance,
            { onComplete(eligibleCount) },
            { logSenderGone("disconnected before their read-receipt could be delivered") },
            1L
        )
        // Mirrors the same "already retired at call time" case as dispatch() above -- here it
        // just means there's nobody left to show the read-receipt to, not a real failure.
        if (!scheduled) logSenderGone("was already offline before their read-receipt could be delivered")
    }

    // Benign, expected race (player disconnecting mid-send) -- logged rather than silently
    // dropped, but gated behind the same `debug` flag Message.kt's own diagnostic logging
    // already uses, not a new always-on line for something this routine.
    private fun logSenderGone(detail: String) = DebugLog.log { "${sender.name} $detail" }
}
