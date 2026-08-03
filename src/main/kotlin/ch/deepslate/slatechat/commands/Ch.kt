package ch.deepslate.slatechat.commands

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Channel
import ch.deepslate.slatechat.core.managers.ChannelsManager
import ch.deepslate.slatechat.core.managers.JogadorManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class Ch : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()

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

        if (args.isEmpty()) {
            sender.sendMessage(msg("channel_command_usage"))
            return true
        }

        val channel = ChannelsManager.canaisCache.keys.firstOrNull {
            it.name.equals(args[0], true) || it.command.equals(args[0], true)
        }

        if (channel == null ||
            (channel.restrict && (channel.permission == null || !sender.hasPermission(channel.permission!!)))) {
            sender.sendMessage(msg("invalid_channel"))
            return true
        }

        JogadorManager.get(sender).channel = channel

        val message = SlateChat.instance.config.getString("channel_changed")!!
            .replace("<channelName>", channel.name)
            .replace("<channelCommand>", channel.command)

        sender.sendMessage(mm.deserialize(message))
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {

        val channels = ChannelsManager.canaisCache.keys
            .filter {
                !it.restrict || it.permission?.let { perm -> sender.hasPermission(perm) } == true
            }
            .map { it.name }

        return if (args.size == 1) {
            channels.filter { it.startsWith(args[0], ignoreCase = true) }
        } else emptyList()
    }

    companion object {
        fun getMessage(path: String): String =
            SlateChat.instance.config.getString(path) ?: ""
    }

    private fun msg(path: String) =
        mm.deserialize(
            SlateChat.instance.config.getString(path) ?: "<red>Missing message: $path"
        )
}
