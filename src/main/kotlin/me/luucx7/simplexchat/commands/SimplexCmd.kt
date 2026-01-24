package me.luucx7.simplexchat.commands

import me.luucx7.simplexchat.SimplexChat
import me.luucx7.simplexchat.core.managers.ChannelsManager
import me.luucx7.simplexchat.core.managers.CustomConfigs
import me.luucx7.simplexchat.core.managers.FocusManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class SimplexCmd : CommandExecutor {

    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (args.isEmpty()) {
            val version = SimplexChat.instance.description.version

            val message: Component = Component.text("SimplexChat ", NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("v$version by Luucx7 @ SagaciusDev @ Vincent", NamedTextColor.AQUA))

            sender.sendMessage(message)
            return true
        }

        // /simplexchat reload
        if (args.size == 1 && args[0].equals("reload", true)) {
            if (!sender.hasPermission("simplexchat.reload")) {
                val noPerm = SimplexChat.instance.config.getString("no_permission") ?: "&cYou do not have permission!"
                sender.sendMessage(legacy.deserialize(noPerm))
                return true
            }
            val plugin = SimplexChat.instance

            // Disable current state
            ChannelsManager.disable()
            FocusManager.unload()

            // Reload base config
            plugin.reloadConfig()

            // Reload channel config
            SimplexChat.cConfig = CustomConfigs.createCustomConfig("channels")

            // Reload modules configs/flags
            SimplexChat.useFilter = false
            if (plugin.config.getBoolean("modules.focus")) {
                SimplexChat.fConfig = CustomConfigs.createCustomConfig("focus")
            }
            if (plugin.config.getBoolean("modules.chatcolor")) {
                SimplexChat.colorsConfig = CustomConfigs.createCustomConfig("color")
                plugin.getCommand("chatcor")?.setExecutor(Cores())
            }
            if (plugin.config.getBoolean("modules.filter")) {
                SimplexChat.useFilter = true
                SimplexChat.filterConfig = CustomConfigs.createCustomConfig("filter")
            }

            // Re-evaluate DiscordSRV presence
            SimplexChat.isDiscordSRV = false
            if (plugin.config.getBoolean("modules.discordsrv")) {
                val discord = Bukkit.getPluginManager().getPlugin("DiscordSRV")
                if (discord != null && discord.isEnabled) {
                    SimplexChat.isDiscordSRV = true
                }
            }

            // Re-load channels and focus
            ChannelsManager.load()
            if (plugin.config.getBoolean("modules.focus")) {
                FocusManager.prepare()
            }

            val msg = plugin.config.getString("plugin_reloaded") ?: "&aPlugin reloaded."
            sender.sendMessage(legacy.deserialize(msg))
            return true
        }

        return false
    }
}
