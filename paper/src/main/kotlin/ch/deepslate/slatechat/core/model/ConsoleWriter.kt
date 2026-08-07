package ch.deepslate.slatechat.core.model

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Channel
import ch.deepslate.slatechat.common.format.MiniMessageRenderer
import ch.deepslate.slatechat.core.integrations.DiscordSrvBridge
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player

/**
 * Mirrors a chat message to the server console (plain text or, with `console_format_colors`,
 * real ANSI colors via Paper's ComponentLogger), and forwards it to DiscordSRV if that
 * bridge is enabled. Owns its own `log_to_console` check -- callers don't need to know
 * console mirroring is toggleable, they just call write() unconditionally.
 */
object ConsoleWriter {
    fun write(channel: Channel, sender: Player, message: String) {
        if (!SlateChat.instance.config.getBoolean("log_to_console", true)) return

        val consoleFormat = SlateChat.instance.config.getString("console_log")
            ?: "[<channel>] <player>: <message>"

        val consoleMsg = consoleFormat
            .replace("<channel>", channel.name)
            .replace("<channelCmd>", channel.command)
            .replace("<player>", sender.name)
            .replace("<message>", message)

        // Apply PlaceholderAPI for console log
        val consoleFinal = PlaceholderAPI.setPlaceholders(sender, consoleMsg)

        // Same conversions the chat message itself gets (via MiniMessageRenderer), so
        // &codes/#hex/raw MiniMessage tags in console_log or the player's message render
        // consistently here too, instead of only legacy codes being stripped like before.
        // Deliberately skips convertBreaks -- a literal newline in a console log line isn't
        // useful the way it is in chat.
        val consoleMiniMessage = MiniMessageRenderer.convertLegacyToMiniMessage(
            MiniMessageRenderer.convertHexToMiniMessage(consoleFinal)
        )
        val consoleComponent = MiniMessageRenderer.deserialize(consoleMiniMessage)
        // Hover/click never reach the console either way: PlainTextComponentSerializer
        // only extracts visible text, and ComponentLogger doesn't render interactive
        // metadata to a terminal -- so there's nothing extra to gate for those specifically.
        val plainConsoleMsg = PlainTextComponentSerializer.plainText().serialize(consoleComponent)

        if (SlateChat.instance.config.getBoolean("console_format_colors", false)) {
            // Real ANSI colors in the console/log file (auto-disabled if the terminal
            // doesn't support it, e.g. output redirected to a file).
            SlateChat.instance.componentLogger.info(consoleComponent)
        } else {
            SlateChat.instance.logger.info(plainConsoleMsg)
        }

        // Forward to the DiscordSRV channel mapped to this SlateChat channel (if any), only
        // when enabled explicitly to avoid duplicates with DiscordSRV's own listener.
        if (SlateChat.isDiscordSRV && SlateChat.instance.config.getBoolean("modules.discordsrv_send", false)) {
            DiscordSrvBridge.send(plainConsoleMsg, channel.name)
        }
    }
}
