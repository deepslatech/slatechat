package ch.deepslate.slatechat

import ch.deepslate.slatechat.core.integrations.PluginPresence
import ch.deepslate.slatechat.core.managers.ChannelsManager
import ch.deepslate.slatechat.core.managers.CommandRegistrar
import ch.deepslate.slatechat.core.managers.CustomConfigs
import ch.deepslate.slatechat.core.managers.FocusManager
import ch.deepslate.slatechat.core.managers.UpdateChecker
import ch.deepslate.slatechat.core.placeholders.ChatPlaceholder
import ch.deepslate.slatechat.gui.ChatColorMenuListener
import ch.deepslate.slatechat.gui.HexColorMenuListener
import ch.deepslate.slatechat.listeners.ChatPlayerListener
import ch.deepslate.slatechat.listeners.LocalListener
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
        lateinit var tellConfig: FileConfiguration
        lateinit var joinQuitConfig: FileConfiguration

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

        // Load channels system (before registering commands -- CommandRegistrar reads the
        // channel list to register one Brigadier command per channel)
        ChannelsManager.load()

        // Commands (Brigadier, via LifecycleEvents.COMMANDS -- see CommandRegistrar)
        CommandRegistrar.install(this)

        // Focus module
        if (config.getBoolean("modules.focus")) {
            fConfig = CustomConfigs.createCustomConfig("focus")
            FocusManager.prepare()
        }

        // Chat color module
        if (config.getBoolean("modules.chatcolor")) {
            colorsConfig = CustomConfigs.createCustomConfig("color")
        }

        // Filter module
        if (config.getBoolean("modules.filter")) {
            useFilter = true
            filterConfig = CustomConfigs.createCustomConfig("filter")
        }

        // Tell (/tell, /msg, /w, /r, /socialspy) module
        if (config.getBoolean("modules.tell.enable")) {
            tellConfig = CustomConfigs.createCustomConfig("tell")
        }

        // Join/quit/first-join messages module
        if (config.getBoolean("modules.joinquit.enable")) {
            joinQuitConfig = CustomConfigs.createCustomConfig("joinquit")
        }

        // DiscordSRV / ItemsAdder hooks
        detectSoftDependencies()

        // Events
        server.pluginManager.registerEvents(LocalListener(), this)
        server.pluginManager.registerEvents(ChatPlayerListener(), this)
        server.pluginManager.registerEvents(ChatColorMenuListener(), this)
        server.pluginManager.registerEvents(HexColorMenuListener(), this)

        // Update checker (async, fail-silent)
        UpdateChecker.check()
    }

    override fun onDisable() {
        ChannelsManager.disable()
        FocusManager.unload()
    }

    /**
     * Re-evaluates DiscordSRV/ItemsAdder presence against the current config + installed
     * plugins. Called from [onEnable] and, since neither soft dependency can appear or
     * disappear without a full restart but their modules can be toggled off in config.yml,
     * from `/slatechat reload` (see `SlateCmd.reload`) as well -- one place for the check
     * both need instead of two copies drifting apart.
     */
    fun detectSoftDependencies() {
        isDiscordSRV = config.getBoolean("modules.discordsrv") && PluginPresence.isEnabled("DiscordSRV")
        isItemsAdder = config.getBoolean("modules.itemsadder") && PluginPresence.isEnabled("ItemsAdder")
    }
}
