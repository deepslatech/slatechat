package ch.deepslate.slatechat.commands

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.managers.ChannelsManager
import ch.deepslate.slatechat.core.model.Mensagem
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ChannelCommand(private val commandName: String) : CommandExecutor {

    private val mm: MiniMessage = MiniMessage.miniMessage()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (sender !is Player) {
            sender.sendMessage(msg("players_only"))
            return true
        }

        val channel = ChannelsManager.canaisCache.keys
            .firstOrNull { it.command.equals(commandName, true) }
            ?: return true

        if (channel.restrict && (channel.permission == null || !sender.hasPermission(channel.permission!!))) {
            sender.sendMessage(msg("no_permission"))
            return true
        }

        if (args.isEmpty()) {
            val usage = SlateChat.instance.config
                .getString("channel_usage_message")
                ?.replace("<command>", commandName.lowercase())
                ?: "<red>Usage message missing"

            sender.sendMessage(mm.deserialize(usage))
            return true
        }

        // Run message processing async
        Bukkit.getScheduler().runTaskAsynchronously(SlateChat.instance, Runnable {
            Mensagem(sender, args.joinToString(" "), channel)
                .validar()
                .preparar()
                .enviar()
        })

        return true
    }

    private fun msg(path: String) =
        mm.deserialize(
            SlateChat.instance.config.getString(path)
                ?: "<red>Missing message: $path"
        )
}
