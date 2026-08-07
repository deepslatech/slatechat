package ch.deepslate.slatechat.commands

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.managers.SocialSpyManager
import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/socialspy` -- toggles whether the sender sees other players' /tell traffic (minus any
 * conversation with a `chat.tell.spy.bypass` participant, hidden entirely -- see
 * [ch.deepslate.slatechat.core.model.Tell]). Gated on `chat.tell.spy`, hardcoded for the same
 * reason as every node in [TellCommand].
 */
object SocialSpyCommand {

    private const val PERMISSION = "chat.tell.spy"

    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("socialspy")
            .requires { source ->
                SlateChat.instance.config.getBoolean("modules.tell.enable") && source.sender.hasPermission(PERMISSION)
            }
            .executes { ctx -> toggle(ctx.source.sender); Command.SINGLE_SUCCESS }
            .build()

    private fun toggle(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(Messages.resolve(SlateChat.instance.config, "players_only", "<red>Only players can execute this command!"))
            return
        }

        val enabled = SocialSpyManager.toggle(sender)
        // tell.yml nests everything under a top-level "tell:" key, same shape as a
        // channels.yml entry -- see the file itself.
        val path = "tell." + if (enabled) "socialspy_on" else "socialspy_off"
        sender.sendMessage(Messages.resolve(SlateChat.tellConfig, path, "<green>Socialspy toggled."))
    }
}
