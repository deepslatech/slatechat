package ch.deepslate.slatechat.commands

import me.clip.placeholderapi.PlaceholderAPI
import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.managers.ChatPlayerManager
import ch.deepslate.slatechat.core.format.MineDownConverter
import ch.deepslate.slatechat.core.format.MiniMessageRenderer
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.util.regex.Pattern

object ChatColorCommand {

    private val mm = MiniMessage.miniMessage()

    // HEX needs full 6 digits to avoid ambiguous MiniMessage parsing
    val RGB: Pattern =
        Pattern.compile("#([0-9a-fA-F]{6})")

    val LEGACY: Pattern =
        Pattern.compile("&([0-9a-fA-F])")

    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("chatcolor")
            .requires { SlateChat.instance.config.getBoolean("modules.chatcolor") }
            .executes { ctx -> setColor(ctx.source, null); Command.SINGLE_SUCCESS }
            .then(
                // Greedy, not word() -- the original just read args[0] and silently ignored
                // anything past it, so trailing garbage after the color still has to parse.
                Commands.argument("color", StringArgumentType.greedyString())
                    .executes { ctx ->
                        val firstWord = StringArgumentType.getString(ctx, "color").substringBefore(' ')
                        setColor(ctx.source, firstWord)
                        Command.SINGLE_SUCCESS
                    }
            )
            .build()

    private fun setColor(source: CommandSourceStack, input: String?) {
        val sender = source.sender

        if (sender !is Player) {
            sender.sendMessage(msg("only_players"))
            return
        }

        if (!sender.hasPermission("chat.setcolor")) {
            sender.sendMessage(msg("no_permission"))
            return
        }

        if (input == null) {
            sender.sendMessage(msg("no_arg"))
            return
        }

        val chatPlayer = ChatPlayerManager.get(sender)

        // Clear color (configurable keywords in color.yml)
        val clearKeywords = SlateChat.colorsConfig.getStringList("clear_keywords")
        if (clearKeywords.any { input.equals(it, true) }) {
            chatPlayer.color = ""
            sender.sendMessage(msg("removed"))
            return
        }

        if (!sender.hasPermission("chat.colored")) {
            sender.sendMessage(msg("no_permission"))
            return
        }

        // Validate format
        if (!validate(input)) {
            sender.sendMessage(msg("invalid"))
            return
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
