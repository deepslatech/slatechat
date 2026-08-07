package ch.deepslate.slatechat.core.managers

import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandMap
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Constructor

/**
 * Reflection into `PluginCommand`'s private constructor and the server's private `commandMap`
 * field -- the technique this whole plugin moved off of when [CommandRegistrar] replaced it
 * with Paper's Brigadier `LifecycleEvents.COMMANDS` API. It's back here for exactly one
 * caller ([CommandRegistrar.registerNewChannelCommands]) and exactly one case: a channel
 * command whose name didn't exist yet when the server booted, added via `/slatechat reload`
 * without a full restart.
 *
 * That case has no safe alternative: Paper's Brigadier dispatcher can only be mutated while
 * `LifecycleEvents.COMMANDS` is actively dispatching (at boot, or on an actual `/reload
 * confirm`) -- calling `Commands#register` from a live command's own execution throws
 * `IllegalStateException: cannot access the dispatcher in this context`, found the hard way
 * (see [CommandRegistrar]'s doc comment). This is the deliberate exception, not a default:
 * reflection because there's no other way, kept as small and as isolated as it can be.
 *
 * A command registered this way is still fully functional under Brigadier -- Paper bridges
 * every legacy `CommandMap` entry into its own dispatcher for dispatch and tab-complete (it
 * has to: that's the only reason a plugin that only ever implemented the old
 * `CommandExecutor` still works at all on a Brigadier-based server). This was this plugin's
 * *only* registration path before the Brigadier rewrite, and it worked in production.
 */
object LegacyCommandRegistrar {

    fun registerDynamically(
        plugin: JavaPlugin,
        name: String,
        executor: CommandExecutor,
        permission: String? = null
    ) {
        val ctor: Constructor<PluginCommand> =
            PluginCommand::class.java.getDeclaredConstructor(String::class.java, Plugin::class.java)
        ctor.isAccessible = true
        val command = ctor.newInstance(name, plugin)

        val commandMapField = plugin.server.javaClass.getDeclaredField("commandMap")
        commandMapField.isAccessible = true
        val commandMap = commandMapField.get(plugin.server) as CommandMap
        commandMap.register(plugin.pluginMeta.name.lowercase(), command)

        command.setExecutor(executor)
        if (executor is TabCompleter) command.tabCompleter = executor
        // Same trade-off ChannelsManager's Brigadier-era comment already covers for the
        // "restrict with no permission node" case: this native field can't express OP-only,
        // only "hide unless granted this permission" -- blocksSender() inside
        // ChannelCommand's own sendUsage/sendMessage still enforces that case correctly
        // either way, this only affects tab-complete/help discoverability.
        if (permission != null) command.permission = permission
    }
}
