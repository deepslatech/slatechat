package ch.deepslate.slatechat.commands

import me.clip.placeholderapi.PlaceholderAPI
import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.managers.JogadorManager
import ch.deepslate.slatechat.core.model.MineDownConverter
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.regex.Pattern

class Cores : CommandExecutor {

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

        val chatPlayer = JogadorManager.get(sender)
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
        // Convert hex and legacy codes to MiniMessage and ensure reset after placeholder
        val converted = convertColorCodes(withPlaceholders) + "<reset>"
        sender.sendMessage(mm.deserialize(converted))

        return true
    }
    
    private fun convertColorCodes(text: String): String {
        var result = text
        
        // Convert #RRGGBB to <#RRGGBB>
        result = RGB.matcher(result).replaceAll("<#$1>")
        
        // Convert &X legacy codes to MiniMessage tags
        result = result
            .replace("&0", "<black>")
            .replace("&1", "<dark_blue>")
            .replace("&2", "<dark_green>")
            .replace("&3", "<dark_aqua>")
            .replace("&4", "<dark_red>")
            .replace("&5", "<dark_purple>")
            .replace("&6", "<gold>")
            .replace("&7", "<gray>")
            .replace("&8", "<dark_gray>")
            .replace("&9", "<blue>")
            .replace("&a", "<green>")
            .replace("&b", "<aqua>")
            .replace("&c", "<red>")
            .replace("&d", "<light_purple>")
            .replace("&e", "<yellow>")
            .replace("&f", "<white>")
            .replace("&l", "<bold>")
            .replace("&o", "<italic>")
            .replace("&n", "<underlined>")
            .replace("&m", "<strikethrough>")
            .replace("&k", "<obfuscated>")
            .replace("&r", "<reset>")
        
        return result
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
