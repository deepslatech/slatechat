package ch.deepslate.slatechat.commands

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.managers.SocialSpyManager
import ch.deepslate.slatechat.core.network.SocialSpyNetwork
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/socialspy` -- one active spying mode per player at a time: off, this server only (the
 * original PDC-backed toggle, `/socialspy` with no args, gated on `chat.tell.spy`), one named
 * server, or the whole network (`/socialspy server <name>` / `/socialspy all`, both additionally
 * gated on `chat.tell.spy.network`). Turning one mode on always clears the other -- see
 * [toggleLocal]/[setNetworkScope]. Running the same network subcommand a second time while it's
 * already active turns it fully off, not back to local.
 *
 * Network scope lives entirely in Velocity's own memory (see
 * [ch.deepslate.slatechat.core.network.SocialSpyNetwork] and Velocity's own
 * `SocialSpySubscriptions`), not durable across relog, unlike the local PDC toggle -- see the
 * approved cross-server design for why.
 */
object SocialSpyCommand {

    private const val PERMISSION = "chat.tell.spy"
    private const val NETWORK_PERMISSION = "chat.tell.spy.network"

    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("socialspy")
            .requires { source ->
                SlateChat.instance.config.getBoolean("modules.tell.enable") && source.sender.hasPermission(PERMISSION)
            }
            .executes { ctx -> toggleLocal(ctx.source.sender); Command.SINGLE_SUCCESS }
            .then(
                Commands.literal("all")
                    .requires { source -> source.sender.hasPermission(NETWORK_PERMISSION) }
                    .executes { ctx -> setNetworkScope(ctx.source.sender, "ALL", ""); Command.SINGLE_SUCCESS }
            )
            .then(
                Commands.literal("server")
                    .requires { source -> source.sender.hasPermission(NETWORK_PERMISSION) }
                    .then(
                        Commands.argument("name", StringArgumentType.word())
                            .executes { ctx ->
                                setNetworkScope(ctx.source.sender, "SERVER", StringArgumentType.getString(ctx, "name"))
                                Command.SINGLE_SUCCESS
                            }
                    )
            )
            .build()

    private fun toggleLocal(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(playersOnly())
            return
        }

        val enabled = SocialSpyManager.toggle(sender)
        // Exclusivity: turning local mode on always clears any active network subscription.
        // Fire-and-forget -- see SocialSpyNetwork.clearScope's own doc for why nothing needs
        // to wait on this.
        if (enabled) SocialSpyNetwork.clearScope(sender)
        sender.sendMessage(msg(if (enabled) "socialspy_on" else "socialspy_off"))
    }

    private fun setNetworkScope(sender: CommandSender, scopeType: String, serverName: String) {
        if (sender !is Player) {
            sender.sendMessage(playersOnly())
            return
        }

        // Exclusivity, the other direction.
        if (SocialSpyManager.isEnabled(sender)) SocialSpyManager.disable(sender)

        SocialSpyNetwork.setScope(
            sender,
            scopeType,
            serverName,
            onResult = { result ->
                sender.sendMessage(
                    when {
                        !result.ok -> msgWithServer("socialspy_unknown_server", serverName)
                        result.activeScope == "OFF" -> msg("socialspy_network_off")
                        result.activeScope == "ALL" -> msg("socialspy_network_all_on")
                        else -> msgWithServer("socialspy_network_server_on", result.serverName)
                    }
                )
            },
            onTimeout = { sender.sendMessage(msg("socialspy_network_unavailable")) },
        )
    }

    private fun playersOnly() =
        Messages.resolve(SlateChat.instance.config, "players_only", "<red>Only players can execute this command!")

    // tell.yml nests everything under a top-level "tell:" key, same shape as TellCommand's own
    // msg() helper.
    private fun msg(path: String) =
        Messages.resolve(SlateChat.tellConfig, "tell.$path", "<red>Missing message: $path")

    private fun msgWithServer(path: String, serverName: String) =
        Messages.deserialize(
            Messages.raw(SlateChat.tellConfig, "tell.$path", "<red>Missing message: $path").replace("<server>", serverName)
        )
}
