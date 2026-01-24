package me.luucx7.simplexchat.commands

import me.luucx7.simplexchat.SimplexChat
import me.luucx7.simplexchat.core.managers.ChannelsManager
import me.luucx7.simplexchat.core.model.Mensagem
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ChannelCommand(private val commandName: String) : CommandExecutor {

    private val legacy: LegacyComponentSerializer = LegacyComponentSerializer.legacyAmpersand()

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
            val usage = SimplexChat.instance.config
                .getString("channel_usage_message")
                ?.replace("{command}", commandName.lowercase())
                ?: "&cUsage message missing"

            sender.sendMessage(legacy.deserialize(usage))
            return true
        }

        // Run message processing async
        Bukkit.getScheduler().runTaskAsynchronously(SimplexChat.instance, Runnable {
            Mensagem(sender, args.joinToString(" "), channel)
                .validar()
                .preparar()
                .enviar()
        })

        return true
    }

    private fun msg(path: String) =
        legacy.deserialize(
            SimplexChat.instance.config.getString(path)
                ?: "&cMissing message: $path"
        )
}
