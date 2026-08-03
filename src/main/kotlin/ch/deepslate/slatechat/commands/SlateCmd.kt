package ch.deepslate.slatechat.commands

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.managers.ChannelsManager
import ch.deepslate.slatechat.core.managers.CustomConfigs
import ch.deepslate.slatechat.core.managers.FocusManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class SlateCmd : CommandExecutor {

    private val mm = MiniMessage.miniMessage()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (args.isEmpty()) {
            val version = SlateChat.instance.description.version

            val message: Component = Component.text("SlateChat ", NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("v$version by Deepslate Studios", NamedTextColor.AQUA))

            sender.sendMessage(message)
            return true
        }

        // /slatechat reload
        if (args.size == 1 && args[0].equals("reload", true)) {
            if (!sender.hasPermission("chat.reload")) {
                val noPerm = SlateChat.instance.config.getString("no_permission") ?: "<red>You do not have permission!"
                sender.sendMessage(mm.deserialize(noPerm))
                return true
            }
            val plugin = SlateChat.instance

            // Disable current state
            ChannelsManager.disable()
            FocusManager.unload()

            // Reload base config
            plugin.reloadConfig()

            // Reload channel config
            SlateChat.cConfig = CustomConfigs.createCustomConfig("channels")

            // Reload modules configs/flags
            SlateChat.useFilter = false
            if (plugin.config.getBoolean("modules.focus")) {
                SlateChat.fConfig = CustomConfigs.createCustomConfig("focus")
            }
            if (plugin.config.getBoolean("modules.chatcolor")) {
                SlateChat.colorsConfig = CustomConfigs.createCustomConfig("color")
                plugin.getCommand("chatcor")?.setExecutor(Cores())
            }
            if (plugin.config.getBoolean("modules.filter")) {
                SlateChat.useFilter = true
                SlateChat.filterConfig = CustomConfigs.createCustomConfig("filter")
            }

            // Re-evaluate DiscordSRV presence
            SlateChat.isDiscordSRV = false
            if (plugin.config.getBoolean("modules.discordsrv")) {
                val discord = Bukkit.getPluginManager().getPlugin("DiscordSRV")
                if (discord != null && discord.isEnabled) {
                    SlateChat.isDiscordSRV = true
                }
            }

            // Re-load channels and focus
            ChannelsManager.load()
            if (plugin.config.getBoolean("modules.focus")) {
                FocusManager.prepare()
            }

            val msg = plugin.config.getString("plugin_reloaded") ?: "<green>Plugin reloaded."
            sender.sendMessage(mm.deserialize(msg))
            return true
        }

        return false
    }
}
