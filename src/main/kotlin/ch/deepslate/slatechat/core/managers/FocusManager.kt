package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.commands.Ch
import ch.deepslate.slatechat.core.api.Channel
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

object FocusManager {

    val focusWorlds: MutableMap<String, Channel> = linkedMapOf()

    private val fConfig: FileConfiguration
        get() = SlateChat.fConfig

    private var cmdManager: CommandManager? = null

    fun prepare() {
        val plugin: JavaPlugin = SlateChat.instance
        
        cmdManager = CommandManager(plugin, "ch").apply {
            register(Ch())
            usage = Ch.getMessage("channel_command_usage")
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

            val channel = ChannelsManager.channelsCache.keys
                .firstOrNull { it.name.equals(channelName, ignoreCase = true) }
                ?: return@forEach

            focusWorlds[world] = channel
        }
    }

    fun unload() {
        if (SlateChat.instance.config.getBoolean("modules.focus")) {
            cmdManager?.unregister()
        }
    }
}
