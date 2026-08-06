package ch.deepslate.slatechat.commands

import me.clip.placeholderapi.PlaceholderAPI
import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.managers.ChatPlayerManager
import ch.deepslate.slatechat.core.format.MineDownConverter
import ch.deepslate.slatechat.core.format.MiniMessageRenderer
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.regex.Pattern

class ChatColorCommand : CommandExecutor {

    private val mm = MiniMessage.miniMessage()

    companion object {
        // HEX needs full 6 digits to avoid ambiguous MiniMessage parsing
        val RGB: Pattern =
            Pattern.compile("#([0-9a-fA-F]{6})")

        val LEGACY: Pattern =
            Pattern.compile("&([0-9a-fA-F])")
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (sender !is Player) {
            sender.sendMessage(msg("only_players"))
            return true
        }

        if (!sender.hasPermission("chat.setcolor")) {
            sender.sendMessage(msg("no_permission"))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(msg("no_arg"))
            return true
        }

        val chatPlayer = ChatPlayerManager.get(sender)
        val input = args[0]

        // Clear color (configurable keywords in color.yml)
        val clearKeywords = SlateChat.colorsConfig.getStringList("clear_keywords")
        if (clearKeywords.any { input.equals(it, true) }) {
            chatPlayer.color = ""
            sender.sendMessage(msg("removed"))
            return true
        }

        if (!sender.hasPermission("chat.colored")) {
            sender.sendMessage(msg("no_permission"))
            return true
        }

        // Validate format
        if (!validate(input)) {
            sender.sendMessage(msg("invalid"))
            return true
        }

        chatPlayer.color = input

        val successRaw = SlateChat.colorsConfig.getString("success") ?: "<green>Color updated!"
        var withPlaceholders = PlaceholderAPI.setPlaceholders(sender, successRaw)
        
        // Convert MineDown syntax to MiniMessage (pass player for permission checks)
        withPlaceholders = MineDownConverter.convert(withPlaceholders, sender)
        // Hex and legacy codes to MiniMessage -- same tag-aware conversion the chat pipeline
        // uses (see MiniMessageRenderer), not a separate reimplementation that could drift
        // out of sync with it, like this used to be.
        val converted = MiniMessageRenderer.convertLegacyToMiniMessage(
            MiniMessageRenderer.convertHexToMiniMessage(withPlaceholders)
        ) + "<reset>"
        sender.sendMessage(mm.deserialize(converted))

        return true
    }

    private fun validate(hexColor: String): Boolean {
        val rgbEnabled = SlateChat.colorsConfig.getBoolean("enable_rgb")

        return when {
            rgbEnabled && RGB.matcher(hexColor).matches() -> true
            LEGACY.matcher(hexColor).matches() -> true
            else -> false
        }
    }

    private fun msg(path: String) =
        mm.deserialize(
            SlateChat.colorsConfig.getString(path)
                ?: "<red>Missing message: $path</red>"
        )
}
