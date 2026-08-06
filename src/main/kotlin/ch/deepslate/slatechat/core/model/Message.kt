package ch.deepslate.slatechat.core.model

import me.clip.placeholderapi.PlaceholderAPI
import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Channel
import ch.deepslate.slatechat.core.format.ChatFilter
import ch.deepslate.slatechat.core.format.ChatPermissionGate
import ch.deepslate.slatechat.core.format.MineDownConverter
import ch.deepslate.slatechat.core.format.MiniMessageRenderer
import ch.deepslate.slatechat.core.integrations.ItemsAdderBridge
import ch.deepslate.slatechat.core.integrations.PaperSelectorResolver
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Orchestrates a single chat message end to end: filter -> permission-gate -> render ->
 * resolve -> send + console-mirror. Each step below delegates to a single-responsibility
 * collaborator (see core/format and core/integrations) -- this class's job is the sequence
 * and the parts that are genuinely its own (recipient targeting, the actionbar read
 * receipt, filter-event logging), not the formatting/permission/integration logic itself.
 */
class Message(
    private val sender: Player,
    rawMessage: String,
    private val channel: Channel
) {

    private var message: String = rawMessage

    // Recorded during prepare(), acted on during sendTo() -- see the comment there for why
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
            // here rather than sent immediately -- see sendTo() for why.
            if (stripped != message) colorPermissionDenied = true
            message = stripped
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
        // Global region thread only for Bukkit actions (Paper's Folia-aware scheduler)
        Bukkit.getGlobalRegionScheduler().execute(SlateChat.instance) {
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
            if (SlateChat.instance.config.getBoolean("debug", false)) {
                SlateChat.instance.logger.info(
                    "[DEBUG] withPlaceholders=$withPlaceholders | gson=" +
                        GsonComponentSerializer.gson().serialize(resolvedComponent)
                )
            }

            ConsoleWriter.write(channel, sender, message)

            if (channel.broadcast) {
                Bukkit.getOnlinePlayers().forEach { sendTo(it, resolvedComponent) }
            } else {
                val radius = channel.radius
                Bukkit.getOnlinePlayers()
                    .filter { it.world == sender.world }
                    .filter { it.location.distance(sender.location) <= radius }
                    .forEach { sendTo(it, resolvedComponent) }
            }
        }
    }

    private fun logFilter(reason: String, detail: String, original: String) {
        if (!SlateChat.instance.config.getBoolean("log_to_console", true)) return
        Bukkit.getLogger().info("[SlateChat][$reason] ${sender.name}: '$original' (detail: $detail)")
    }

    private fun sendTo(player: Player, component: Component) {
        val audience: Audience = player
        audience.sendMessage(component)

        // Only the sender gets an action bar at all -- everyone else was just a message
        // recipient. Two independent things want that one slot: the "you can't use color"
        // heads-up from prepare(), and the channel's own read-receipt count below. The client
        // only ever shows the *last* action bar sent to it, so sending them separately means
        // whichever fires second silently replaces the first -- collected into one combined
        // send instead of racing each other for the same line.
        if (player.uniqueId != sender.uniqueId) return

        val parts = mutableListOf<String>()

        if (colorPermissionDenied) {
            parts += SlateChat.instance.config.getString("no_permission") ?: "<red>You do not have permission!"
        }

        if (channel.actionbar) {
            val count = if (channel.broadcast) {
                Bukkit.getOnlinePlayers()
                    .count { it.uniqueId != sender.uniqueId && !it.hasPermission("chat.count.bypass") }
            } else {
                Bukkit.getOnlinePlayers()
                    .count {
                        it.world == sender.world &&
                            it.location.distance(sender.location) <= channel.radius &&
                            it.uniqueId != sender.uniqueId &&
                            !it.hasPermission("chat.count.bypass")
                    }
            }

            val readReceipt = if (count == 0) {
                SlateChat.instance.config.getString("no_one") ?: ""
            } else {
                SlateChat.instance.config.getString("amount_readed")
                    ?.replace("<amount>", count.toString()) ?: ""
            }

            if (readReceipt.isNotBlank()) parts += readReceipt
        }

        if (parts.isNotEmpty()) {
            audience.sendActionBar(MiniMessageRenderer.deserialize(parts.joinToString(" <gray>|</gray> ")))
        }
    }
}
