package ch.deepslate.slatechat.core.model

import me.clip.placeholderapi.PlaceholderAPI
import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Channel
import ch.deepslate.slatechat.common.format.ChatFilter
import ch.deepslate.slatechat.common.format.ChatPermissionGate
import ch.deepslate.slatechat.core.format.MineDownConverter
import ch.deepslate.slatechat.common.format.MiniMessageRenderer
import ch.deepslate.slatechat.core.integrations.ItemsAdderBridge
import ch.deepslate.slatechat.core.integrations.PaperSelectorResolver
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Orchestrates a single chat message end to end: filter -> permission-gate -> render ->
 * resolve -> send + console-mirror. Each step below delegates to a single-responsibility
 * collaborator (see core/format and core/integrations) -- this class's job is the sequence
 * and the parts that are genuinely its own (recipient targeting, the actionbar read
 * receipt, filter-event logging), not the formatting/permission/integration logic itself.
 *
 * Folia note: everything from [deliver] onward is keyed on `sender` (PlaceholderAPI,
 * MineDownConverter's permission checks, and -- critically -- [PaperSelectorResolver], which
 * resolves `<selector>`/`<score>`/`<nbt>` against sender's own location/world), so [send]
 * schedules onto **sender's own `EntityScheduler`** rather than the global region scheduler --
 * a player's state may only be touched safely from the thread that currently owns their
 * region. Fan-out to recipients is delegated to [MessageDelivery], which does the same thing
 * per recipient for the same reason.
 */
class Message(
    private val sender: Player,
    rawMessage: String,
    private val channel: Channel
) {

    private var message: String = rawMessage

    // Recorded during prepare(), acted on during sendSenderActionBar() -- see there for why
    // this can't just fire an action bar immediately.
    private var colorPermissionDenied: Boolean = false

    /** Step 1 — validation (filter, caps, etc) */
    fun validate(): Message {
        if (!SlateChat.useFilter) return this

        val blockedWords = SlateChat.filterConfig.getStringList("blocked_words")
        val replacements = SlateChat.filterConfig.getStringList("replace")
        val result = ChatFilter.apply(message, blockedWords, replacements)

        message = result.text
        result.events.forEach { logFilter(it.reason, it.detail, it.original) }

        return this
    }

    /** Step 2 — formatting + placeholders */
    fun prepare(): Message {
        if (!sender.hasPermission("chat.colored")) {
            val stripped = ChatPermissionGate.stripColorTags(message)
            // Only worth telling the sender anything if a color tag was actually there to
            // strip -- same "no permission" message /chatcolor already uses, but via the
            // action bar instead of a chat line: the message itself still sends normally
            // (just without the color), this is a quiet heads-up, not a rejection. Recorded
            // here rather than sent immediately -- see sendSenderActionBar() for why.
            if (stripped != message) colorPermissionDenied = true
            message = stripped
        }
        // Independent of chat.colored above -- a per-server policy switch, not a
        // per-player permission, see ChatPermissionGate.stripPrideTag's own doc.
        if (!SlateChat.instance.config.getBoolean("modules.chatformat.pride", true)) {
            message = ChatPermissionGate.stripPrideTag(message)
        }
        if (!sender.hasPermission("chat.cosmetic")) {
            message = ChatPermissionGate.stripCosmeticTags(message)
        }
        if (!sender.hasPermission("chat.advanced")) {
            message = ChatPermissionGate.stripAdvancedTags(message)
        }
        message = PlaceholderAPI.setPlaceholders(sender, message)
        return this
    }

    /** Step 3 — send */
    fun send() {
        val scheduled = sender.scheduler.execute(
            SlateChat.instance,
            { deliver() },
            { logSenderGone("disconnected before their message could be sent") },
            1L
        )
        // execute() returns false only when sender was *already* retired at call time (e.g.
        // disconnected in the same tick between prepare() and send()) -- neither callback
        // above fires in that case.
        if (!scheduled) logSenderGone("was already offline before their message could be sent")
    }

    /** Runs on sender's own owning thread -- see the class doc for why that matters here. */
    private fun deliver() {
        // Strip styles (bold/italic/etc.) without wiping colors to preserve channel/chat colors
        val safeMessage = "<bold:false><italic:false><underlined:false><strikethrough:false><obfuscated:false>$message"
        val formatted = channel.format
            .replace("<player>", sender.name)
            .replace("<message>", safeMessage)
            .replace("<channel>", channel.name)

        // Apply PlaceholderAPI to the entire formatted string
        var withPlaceholders = PlaceholderAPI.setPlaceholders(sender, formatted)

        // Convert MineDown syntax to MiniMessage (pass player for permission checks)
        withPlaceholders = MineDownConverter.convert(withPlaceholders, sender)
        // Resolve ItemsAdder :emoji: font images, if ItemsAdder is installed
        withPlaceholders = ItemsAdderBridge.applyFontImages(sender, withPlaceholders)
        // <br>/\n, hex, legacy, then deserialize -- the shared render pipeline
        val component = MiniMessageRenderer.render(withPlaceholders)
        // <selector>/<score>/<nbt> come out of render() as real components, but per
        // Adventure's own docs they're "rendered serverside" against "platform-defined
        // context" -- sent as-is via a plain Audience#sendMessage(), a client just falls
        // back to showing the literal pattern (e.g. "@e[limit=5]") instead of resolved
        // names, because nothing ever told the platform who/where to resolve against. This
        // resolves them against the message's sender before anything goes out.
        val resolvedComponent = PaperSelectorResolver.resolve(component, sender)

        // Verbose diagnostic logging -- off by default, noisy (dumps the full component tree
        // as JSON for every message). Toggle with `debug: true` in config.yml.
        DebugLog.log {
            "withPlaceholders=$withPlaceholders | gson=" + GsonComponentSerializer.gson().serialize(resolvedComponent)
        }

        ConsoleWriter.write(channel, sender, message)

        // Safe now -- we're on sender's own thread.
        val senderPosition = RecipientEligibility.Position(sender.world.uid, sender.location.x, sender.location.y, sender.location.z)
        // Safe from any thread -- the server's global player list, not owned by any one region,
        // unlike an individual player's live location/permission state.
        val recipients = Bukkit.getOnlinePlayers().toList()

        MessageDelivery(sender, channel, senderPosition, resolvedComponent)
            .dispatch(recipients) { eligibleCount -> sendSenderActionBar(eligibleCount) }
    }

    private fun logFilter(reason: String, detail: String, original: String) {
        if (!SlateChat.instance.config.getBoolean("log_to_console", true)) return
        Bukkit.getLogger().info("[SlateChat][$reason] ${sender.name}: '$original' (detail: $detail)")
    }

    private fun logSenderGone(detail: String) = DebugLog.log { "${sender.name} $detail" }

    /** Called by [MessageDelivery.dispatch]'s completion callback, guaranteed to already be
     *  running on sender's own thread -- safe to call `sendActionBar` directly. */
    private fun sendSenderActionBar(eligibleCount: Int) {
        val parts = mutableListOf<String>()

        if (colorPermissionDenied) {
            parts += SlateChat.instance.config.getString("no_permission") ?: "<red>You do not have permission!"
        }

        if (channel.actionbar) {
            val readReceipt = if (eligibleCount == 0) {
                SlateChat.instance.config.getString("no_one") ?: ""
            } else {
                SlateChat.instance.config.getString("amount_readed")
                    ?.replace("<amount>", eligibleCount.toString()) ?: ""
            }

            if (readReceipt.isNotBlank()) parts += readReceipt
        }

        if (parts.isNotEmpty()) {
            sender.sendActionBar(MiniMessageRenderer.deserialize(parts.joinToString(" <gray>|</gray> ")))
        }
    }
}
