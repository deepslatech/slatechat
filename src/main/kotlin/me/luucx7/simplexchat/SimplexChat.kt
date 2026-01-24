package me.luucx7.simplexchat

import me.luucx7.simplexchat.commands.Cores
import me.luucx7.simplexchat.commands.SimplexCmd
import me.luucx7.simplexchat.core.managers.ChannelsManager
import me.luucx7.simplexchat.core.managers.CustomConfigs
import me.luucx7.simplexchat.core.managers.FocusManager
import me.luucx7.simplexchat.core.placeholders.ChatPlaceholder
import me.luucx7.simplexchat.listeners.ChatPlayerListener
import me.luucx7.simplexchat.listeners.LocalListener
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class SimplexChat : JavaPlugin() {

    companion object {
        lateinit var instance: SimplexChat
            private set

        lateinit var cConfig: FileConfiguration
        lateinit var colorsConfig: FileConfiguration
        lateinit var fConfig: FileConfiguration
        lateinit var filterConfig: FileConfiguration

        var useFilter: Boolean = false

        var isDiscordSRV: Boolean = false
    }

    override fun onEnable() {
        instance = this
        isDiscordSRV = false

        saveDefaultConfig()

        // Load channel configuration
        cConfig = CustomConfigs.createCustomConfig("channels")

        // Register PlaceholderAPI expansion
        ChatPlaceholder(this).register()

        // Commands
        getCommand("simplexchat")?.setExecutor(SimplexCmd())

        // Load channels system
        ChannelsManager.load()

        // Focus module
        if (config.getBoolean("modules.focus")) {
            fConfig = CustomConfigs.createCustomConfig("focus")
            FocusManager.prepare()
        }

        // Chat color module
        if (config.getBoolean("modules.chatcolor")) {
            colorsConfig = CustomConfigs.createCustomConfig("color")
            getCommand("chatcor")?.setExecutor(Cores())
        }

        // Filter module
        if (config.getBoolean("modules.filter")) {
            useFilter = true
            filterConfig = CustomConfigs.createCustomConfig("filter")
        }

        // DiscordSRV hook
        if (config.getBoolean("modules.discordsrv")) {
            val discord = Bukkit.getPluginManager().getPlugin("DiscordSRV")
            if (discord != null && discord.isEnabled) {
                isDiscordSRV = true
            }
        }

        // Events
        server.pluginManager.registerEvents(LocalListener(), this)
        server.pluginManager.registerEvents(ChatPlayerListener(), this)
    }

    override fun onDisable() {
        ChannelsManager.disable()
        FocusManager.unload()
    }
}
