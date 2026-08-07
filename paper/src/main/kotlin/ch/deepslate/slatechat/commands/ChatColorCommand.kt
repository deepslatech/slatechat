package ch.deepslate.slatechat.commands

import me.clip.placeholderapi.PlaceholderAPI
import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.managers.ChatPlayerManager
import ch.deepslate.slatechat.common.format.MinecraftColors
import ch.deepslate.slatechat.core.format.MineDownConverter
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.common.format.MiniMessageRenderer
import ch.deepslate.slatechat.gui.ChatColorMenu
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import java.util.regex.Pattern

/**
 * `/chatcolor <color>` sets it directly; `/chatcolor` with no argument opens [ChatColorMenu]
 * instead (used to just print a usage message -- see [setColor]). [apply]/[applyClear] are
 * the one shared path both the typed command and a menu click go through, so permission
 * checks and confirmation messages can never drift between the two entry points.
 */
object ChatColorCommand {

    // HEX needs full 6 digits to avoid ambiguous MiniMessage parsing
    val RGB: Pattern =
        Pattern.compile("#([0-9a-fA-F]{6})")

    val LEGACY: Pattern =
        Pattern.compile("&([0-9a-fA-F])")

    // MiniMessage's own bracketed forms -- <#rrggbb> for hex, <red>/<dark_aqua>/etc. for the
    // 16 named colors (validated against MinecraftColors.NAMES below, not just "looks
    // tag-shaped" -- <foobar> still has to be rejected).
    val MINIMESSAGE_HEX: Pattern =
        Pattern.compile("<#([0-9a-fA-F]{6})>")

    val MINIMESSAGE_NAMED: Pattern =
        Pattern.compile("<([a-z_]+)>")

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

        if (input != null) {
            apply(sender, input)
            return
        }

        // Bare /chatcolor used to just print "type a color" -- opening the picker is strictly
        // more useful for the exact same permission this always required.
        if (!sender.hasPermission("chat.setcolor")) {
            sender.sendMessage(msg("no_permission"))
            return
        }
        ChatColorMenu.open(sender)
    }

    /** Sets [sender]'s chat color to [input] (or clears it, if [input] matches one of
     *  color.yml's `clear_keywords`) -- the one path both [setColor] and a
     *  [ChatColorMenu] click go through. */
    fun apply(sender: Player, input: String) {
        if (!sender.hasPermission("chat.setcolor")) {
            sender.sendMessage(msg("no_permission"))
            return
        }

        val clearKeywords = SlateChat.colorsConfig.getStringList("clear_keywords")
        if (clearKeywords.any { input.equals(it, true) }) {
            applyClear(sender)
            return
        }

        if (!sender.hasPermission("chat.colored")) {
            sender.sendMessage(msg("no_permission"))
            return
        }

        if (!validate(input)) {
            sender.sendMessage(msg("invalid"))
            return
        }

        ChatPlayerManager.get(sender).color = input
        sendSuccess(sender)
    }

    /** Clears [sender]'s chat color back to the channel's own default. */
    fun applyClear(sender: Player) {
        if (!sender.hasPermission("chat.setcolor")) {
            sender.sendMessage(msg("no_permission"))
            return
        }

        ChatPlayerManager.get(sender).color = ""
        sender.sendMessage(msg("removed"))
    }

    private fun sendSuccess(sender: Player) {
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
        sender.sendMessage(Messages.deserialize(converted))
    }

    private fun validate(input: String): Boolean {
        val rgbEnabled = SlateChat.colorsConfig.getBoolean("enable_rgb")

        if (rgbEnabled && RGB.matcher(input).matches()) return true
        if (LEGACY.matcher(input).matches()) return true
        if (rgbEnabled && MINIMESSAGE_HEX.matcher(input).matches()) return true

        val named = MINIMESSAGE_NAMED.matcher(input)
        if (named.matches()) return named.group(1) in MinecraftColors.NAMES

        return false
    }

    private fun msg(path: String) =
        Messages.resolve(SlateChat.colorsConfig, path, "<red>Missing message: $path</red>")
}
