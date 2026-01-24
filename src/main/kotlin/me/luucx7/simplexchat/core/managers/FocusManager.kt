package me.luucx7.simplexchat.core.managers

import me.luucx7.simplexchat.SimplexChat
import me.luucx7.simplexchat.commands.Ch
import me.luucx7.simplexchat.core.api.Channel
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

object FocusManager {

    val focusWorlds: MutableMap<String, Channel> = linkedMapOf()

    private val fConfig: FileConfiguration
        get() = SimplexChat.fConfig

    private var cmdManager: CommandManager? = null

    fun prepare() {
        val plugin: JavaPlugin = SimplexChat.instance
        
        cmdManager = CommandManager(plugin, "ch").apply {
            var aliases = listOf("channel", "canal", "canais")

            register(Ch())
            usage = Ch.getMessage("channel_command_usage")
            aliases = aliases
        }

        loadWorldFocus()
    }

    private fun loadWorldFocus() {
        focusWorlds.clear()

        fConfig.getStringList("worlds").forEach { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size < 2) return@forEach

            val world = parts[0]
            val channelName = parts[1]

            val channel = ChannelsManager.canaisCache.keys
                .firstOrNull { it.name.equals(channelName, ignoreCase = true) }
                ?: return@forEach

            focusWorlds[world] = channel
        }
    }

    fun unload() {
        if (SimplexChat.instance.config.getBoolean("modules.focus")) {
            cmdManager?.unregister()
        }
    }
}
