package ch.deepslate.slatechat.core.model

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.format.ChatFilter
import ch.deepslate.slatechat.core.format.ChatPermissionGate
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.format.MineDownConverter
import ch.deepslate.slatechat.core.format.MiniMessageRenderer
import ch.deepslate.slatechat.core.integrations.ItemsAdderBridge
import ch.deepslate.slatechat.core.integrations.PaperSelectorResolver
import ch.deepslate.slatechat.core.managers.SocialSpyManager
import ch.deepslate.slatechat.core.managers.TellManager
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Orchestrates a single /tell end to end: filter -> permission-gate -> render -> deliver to
 * sender+recipient -> socialspy fan-out -> console mirror. Mirrors [Message]'s
 * validate/prepare/send shape and reuses the exact same collaborators (ChatFilter,
 * ChatPermissionGate, MineDownConverter, ItemsAdderBridge, MiniMessageRenderer,
 * PaperSelectorResolver) -- the only thing that differs from a channel message is *who* the
 * rendered text goes to and how many differently-worded copies of it there are (sender's own
 * line, the recipient's line, the socialspy line).
 *
 * Folia note, same rule [Message]/[MessageDelivery] already follow -- a player's own state
 * (permissions, PDC, sendMessage) may only be touched from the thread that currently owns
 * their region: [deliver] runs on sender's own `EntityScheduler` (everything in it reads only
 * sender's own state, or thread-safe identity fields like `.name`/`.uniqueId`), recipient's
 * `sendMessage`/`hasPermission` check happen on recipient's own scheduler in
 * [deliverToRecipient], and each socialspy watcher's own PDC read happens on *their* own
 * scheduler in [fanOutToSpies].
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
        if (!SlateChat.useFilter) return this

        val blockedWords = SlateChat.filterConfig.getStringList("blocked_words")
        val replacements = SlateChat.filterConfig.getStringList("replace")
        val result = ChatFilter.apply(message, blockedWords, replacements)

        message = result.text
        result.events.forEach { logFilter(it.reason, it.detail, it.original) }

        return this
    }

    /** Step 2 — formatting + placeholders (same permission nodes channel chat gates on) */
    fun prepare(): Tell {
        if (!sender.hasPermission("chat.colored")) {
            val stripped = ChatPermissionGate.stripColorTags(message)
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
            { logGone(sender, "disconnected before their tell could be sent") },
            1L
        )
        if (!scheduled) logGone(sender, "was already offline before their tell could be sent")
    }

    /** Runs on sender's own owning thread -- see the class doc for why that matters here. */
    private fun deliver() {
        val safeMessage = "<bold:false><italic:false><underlined:false><strikethrough:false><obfuscated:false>$message"

        val senderLine = render(tellFormat("format_sent", safeMessage))
        val recipientLine = render(tellFormat("format_received", safeMessage))
        val spyLine = render(tellFormat("format_spy", safeMessage))

        if (colorPermissionDenied) {
            sender.sendMessage(Messages.resolve(SlateChat.instance.config, "no_permission", "<red>You do not have permission!"))
        }
        sender.sendMessage(senderLine)

        // Console log always fires, regardless of chat.tell.spy.bypass -- that permission hides
        // a tell from other players' socialspy, not from the server owner's own audit trail.
        writeConsole()

        TellManager.recordConversation(sender, recipient)

        deliverToRecipient(recipientLine, spyLine)
    }

    private fun tellFormat(path: String, msg: String): String {
        // Everything in tell.yml lives under a top-level "tell:" key, same shape as a
        // channels.yml entry -- see SocialSpyManager/TellCommand for the other reads of it.
        val template = SlateChat.tellConfig.getString("tell.$path") ?: "<player> -> <target>: <message>"
        // <reply> is its own configured snippet (tell.reply_button) rather than something
        // baked in here -- a no-op .replace() on any format that doesn't mention <reply> at
        // all, same as <player>/<target>/<message> would be if a format left one of those out.
        val replyButton = SlateChat.tellConfig.getString("tell.reply_button") ?: ""
        return template
            .replace("<player>", sender.name)
            .replace("<target>", recipient.name)
            .replace("<message>", msg)
            .replace("<reply>", replyButton)
    }

    /** Placeholders/MineDown/ItemsAdder/selectors all resolve against sender, same as channel
     *  chat -- the recipient and any socialspy watcher just receive the finished component. */
    private fun render(formatted: String): Component {
        var withPlaceholders = PlaceholderAPI.setPlaceholders(sender, formatted)
        withPlaceholders = MineDownConverter.convert(withPlaceholders, sender)
        withPlaceholders = ItemsAdderBridge.applyFontImages(sender, withPlaceholders)
        val component = MiniMessageRenderer.render(withPlaceholders)
        return PaperSelectorResolver.resolve(component, sender)
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
                if (!exempt) fanOutToSpies(spyLine)
            },
            { logGone(recipient, "disconnected before their tell could be delivered") },
            1L
        )
        if (!scheduled) logGone(recipient, "was already offline before their tell could be delivered")
    }

    /** Global online-player list is safe to read from any thread (not owned by any one
     *  region) -- each candidate's own socialspy state is only read once we're on *their*
     *  own scheduler below. */
    private fun fanOutToSpies(spyLine: Component) {
        Bukkit.getOnlinePlayers()
            .filter { it.uniqueId != sender.uniqueId && it.uniqueId != recipient.uniqueId }
            .forEach { watcher ->
                watcher.scheduler.execute(
                    SlateChat.instance,
                    { if (SocialSpyManager.isEnabled(watcher)) watcher.sendMessage(spyLine) },
                    {}, // watcher disconnected mid-flight -- nobody left to show the spy line to
                    1L
                )
            }
    }

    private fun writeConsole() {
        if (!SlateChat.instance.config.getBoolean("log_to_console", true)) return

        val consoleMsg = tellFormat("console_log", message)
        val consoleFinal = PlaceholderAPI.setPlaceholders(sender, consoleMsg)
        val consoleMiniMessage = MiniMessageRenderer.convertLegacyToMiniMessage(
            MiniMessageRenderer.convertHexToMiniMessage(consoleFinal)
        )
        val consoleComponent = MiniMessageRenderer.deserialize(consoleMiniMessage)
        val plainConsoleMsg = PlainTextComponentSerializer.plainText().serialize(consoleComponent)

        if (SlateChat.instance.config.getBoolean("console_format_colors", false)) {
            SlateChat.instance.componentLogger.info(consoleComponent)
        } else {
            SlateChat.instance.logger.info(plainConsoleMsg)
        }
    }

    private fun logFilter(reason: String, detail: String, original: String) {
        if (!SlateChat.instance.config.getBoolean("log_to_console", true)) return
        Bukkit.getLogger().info("[SlateChat][$reason] ${sender.name}: '$original' (detail: $detail)")
    }

    private fun logGone(player: Player, detail: String) = DebugLog.log { "${player.name} $detail" }

    companion object {
        /** Hardcoded on purpose, like every other tell permission -- see TellCommand.kt's
         *  class doc for why. */
        const val SPY_EXEMPT_PERMISSION = "chat.tell.spy.bypass"
    }
}
