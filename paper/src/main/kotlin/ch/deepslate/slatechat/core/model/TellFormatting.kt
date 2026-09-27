package ch.deepslate.slatechat.core.model

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.common.format.ChatFilter
import ch.deepslate.slatechat.common.format.ChatPermissionGate
import ch.deepslate.slatechat.common.format.MiniMessageRenderer
import ch.deepslate.slatechat.core.format.MineDownConverter
import ch.deepslate.slatechat.core.integrations.ItemsAdderBridge
import ch.deepslate.slatechat.core.integrations.PaperSelectorResolver
import ch.deepslate.slatechat.core.managers.SocialSpyManager
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * The filter/permission-gate/render/format/console-log pipeline every /tell shares, whether the
 * target ends up being a local [Player] ([Tell]) or resolved across the network ([TellRelay]).
 * Extracted out of what used to be [Tell]'s own private methods while cross-server relay was
 * already touching this code -- two independent copies of this pipeline was exactly the kind of
 * drift risk this codebase avoids elsewhere.
 *
 * Every function here takes the sender's own state plus plain strings, never a recipient
 * [Player] -- [TellRelay] doesn't have a live one at request time, only whatever name was typed
 * or [ch.deepslate.slatechat.core.managers.TellManager]'s cached one.
 */
object TellFormatting {

    data class Prepared(val message: String, val colorPermissionDenied: Boolean)

    /** Step 1 -- filter (mirrors what used to be `Tell.validate()`). */
    fun validate(sender: Player, rawMessage: String): String {
        if (!SlateChat.useFilter) return rawMessage

        val blockedWords = SlateChat.filterConfig.getStringList("blocked_words")
        val replacements = SlateChat.filterConfig.getStringList("replace")
        val result = ChatFilter.apply(rawMessage, blockedWords, replacements)

        result.events.forEach { logFilter(sender, it.reason, it.detail, it.original) }
        return result.text
    }

    /** Step 2 -- formatting + permission gates (mirrors what used to be `Tell.prepare()`), same
     *  nodes channel chat gates on. */
    fun prepare(sender: Player, message: String): Prepared {
        var result = message
        var colorPermissionDenied = false

        if (!sender.hasPermission("chat.colored")) {
            val stripped = ChatPermissionGate.stripColorTags(result)
            if (stripped != result) colorPermissionDenied = true
            result = stripped
        }
        // Independent of chat.colored above -- a per-server policy switch, not a per-player
        // permission, see ChatPermissionGate.stripPrideTag's own doc.
        if (!SlateChat.instance.config.getBoolean("modules.chatformat.pride", true)) {
            result = ChatPermissionGate.stripPrideTag(result)
        }
        if (!sender.hasPermission("chat.cosmetic")) {
            result = ChatPermissionGate.stripCosmeticTags(result)
        }
        if (!sender.hasPermission("chat.advanced")) {
            result = ChatPermissionGate.stripAdvancedTags(result)
        }
        result = PlaceholderAPI.setPlaceholders(sender, result)
        return Prepared(result, colorPermissionDenied)
    }

    /** tell.yml's `tell.$path` template, with `<player>`/`<target>`/`<message>`/`<reply>`
     *  substituted -- [playerName]/[targetName] are plain strings (not a live [Player]) since
     *  neither [TellRelay] (no live recipient) nor [TellRelayClient]'s inbound `deliver` handler
     *  (no live sender -- they're on a different server) always has one to read `.name` from. */
    fun format(path: String, playerName: String, targetName: String, msg: String): String {
        val template = SlateChat.tellConfig.getString("tell.$path") ?: "<player> -> <target>: <message>"
        // <reply> is its own configured snippet (tell.reply_button) rather than something baked
        // in here -- a no-op .replace() on any format that doesn't mention <reply> at all, same
        // as <player>/<target>/<message> would be if a format left one of those out.
        val replyButton = SlateChat.tellConfig.getString("tell.reply_button") ?: ""
        return template
            .replace("<player>", playerName)
            .replace("<target>", targetName)
            .replace("<message>", msg)
            .replace("<reply>", replyButton)
    }

    /** Placeholders/MineDown/ItemsAdder/selectors all resolve against sender, same as channel
     *  chat -- the recipient (local or remote) just receives the finished component. */
    fun render(sender: Player, formatted: String): Component {
        var withPlaceholders = PlaceholderAPI.setPlaceholders(sender, formatted)
        withPlaceholders = MineDownConverter.convert(withPlaceholders, sender)
        withPlaceholders = ItemsAdderBridge.applyFontImages(sender, withPlaceholders)
        val component = MiniMessageRenderer.render(withPlaceholders)
        return PaperSelectorResolver.resolve(component, sender)
    }

    /** [context] is whichever live [Player] is actually on this server -- the sender for a
     *  same-server [Tell] or an outgoing [TellRelay], the recipient for an inbound
     *  [TellRelayClient] delivery (the sender isn't local to this server in that case). Only used
     *  for the final [PlaceholderAPI] pass over the assembled template; [playerName]/[targetName]
     *  are always plain strings, see [format]'s own doc. */
    fun writeConsole(context: Player, playerName: String, targetName: String, message: String) {
        if (!SlateChat.instance.config.getBoolean("log_to_console", true)) return

        val consoleMsg = format("console_log", playerName, targetName, message)
        val consoleFinal = PlaceholderAPI.setPlaceholders(context, consoleMsg)
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

    /** Same-server socialspy fan-out to every online player with local `/socialspy` (no args)
     *  active, minus [exclude] (always the conversation's own two participants -- they never spy
     *  on themselves). Shared by [Tell] (both sides local) and [TellRelayClient]'s inbound
     *  `deliver` handler (recipient local, sender on another server) so a plain local socialspy
     *  toggle catches every tell touching this server, not just fully-local ones. Network-scope
     *  watchers (`/socialspy all`/`server <name>`) are a separate path, fanned out directly by
     *  Velocity -- see `TellRelayHandler.fanOutSpy`. */
    fun fanOutToLocalSpies(spyLine: Component, exclude: Set<UUID>) {
        Bukkit.getOnlinePlayers()
            .filter { it.uniqueId !in exclude }
            .forEach { watcher ->
                watcher.scheduler.execute(
                    SlateChat.instance,
                    { if (SocialSpyManager.isEnabled(watcher)) watcher.sendMessage(spyLine) },
                    {}, // watcher disconnected mid-flight -- nobody left to show the spy line to
                    1L
                )
            }
    }

    private fun logFilter(sender: Player, reason: String, detail: String, original: String) {
        if (!SlateChat.instance.config.getBoolean("log_to_console", true)) return
        Bukkit.getLogger().info("[SlateChat][$reason] ${sender.name}: '$original' (detail: $detail)")
    }
}
