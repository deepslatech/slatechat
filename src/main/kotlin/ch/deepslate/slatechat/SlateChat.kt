package ch.deepslate.slatechat

import ch.deepslate.slatechat.commands.ChatColorCommand
import ch.deepslate.slatechat.commands.SlateCmd
import ch.deepslate.slatechat.core.managers.ChannelsManager
import ch.deepslate.slatechat.core.managers.CustomConfigs
import ch.deepslate.slatechat.core.managers.FocusManager
import ch.deepslate.slatechat.core.managers.UpdateChecker
import ch.deepslate.slatechat.core.placeholders.ChatPlaceholder
import ch.deepslate.slatechat.listeners.ChatPlayerListener
import ch.deepslate.slatechat.listeners.LocalListener
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class SlateChat : JavaPlugin() {

    companion object {
        lateinit var instance: SlateChat
            private set

        lateinit var cConfig: FileConfiguration
        lateinit var colorsConfig: FileConfiguration
        lateinit var fConfig: FileConfiguration
        lateinit var filterConfig: FileConfiguration

        var useFilter: Boolean = false

        var isDiscordSRV: Boolean = false
        var isItemsAdder: Boolean = false
    }

    override fun onEnable() {
        instance = this
        isDiscordSRV = false
        isItemsAdder = false

        saveDefaultConfig()

        // Load channel configuration
        cConfig = CustomConfigs.createCustomConfig("channels")

        // Register PlaceholderAPI expansion
        ChatPlaceholder(this).register()

        // Commands
        getCommand("slatechat")?.setExecutor(SlateCmd())

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
            getCommand("chatcolor")?.setExecutor(ChatColorCommand())
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

        // ItemsAdder hook (resolves :emoji: font images in chat)
        if (config.getBoolean("modules.itemsadder")) {
            val itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder")
            if (itemsAdder != null && itemsAdder.isEnabled) {
                isItemsAdder = true
            }
        }

        // Events
        server.pluginManager.registerEvents(LocalListener(), this)
        server.pluginManager.registerEvents(ChatPlayerListener(), this)

        // Update checker (async, fail-silent)
        UpdateChecker.check()
    }

    override fun onDisable() {
        ChannelsManager.disable()
        FocusManager.unload()
    }
}
