package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.commands.Ch
import ch.deepslate.slatechat.commands.ChannelCommand
import ch.deepslate.slatechat.commands.ChatColorCommand
import ch.deepslate.slatechat.commands.SlateCmd
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

/**
 * Registers SlateChat's commands through Paper's Brigadier lifecycle API
 * (`LifecycleEvents.COMMANDS`, available since Paper 1.20.6) instead of the old approach:
 * reflecting into `PluginCommand`'s private constructor and the server's private
 * `commandMap` field to fake a plugin.yml-declared command for channels configured at
 * runtime. No reflection, no plugin.yml entries needed for the three static commands or any
 * channel command that already exists at boot.
 *
 * Hard platform constraint, found the hard way (twice): the dispatcher this hands you can
 * *only* be mutated while the COMMANDS event is actively dispatching to a handler --
 * `/slatechat reload` calling `register()` again later, from its own already-running command,
 * first NPE'd on the implicit-PluginMeta overload (fixed by passing the plugin's `PluginMeta`
 * explicitly), and then, with that fixed, hit Paper's own guard head-on: `IllegalStateException:
 * cannot access the dispatcher in this context`. There's no documented way around that from
 * inside a live command -- registration through this API genuinely only happens once, here,
 * during [install].
 *
 * For a channel that already existed at boot, that's not a problem: `/slatechat reload`
 * rebuilds *data* only, and every channel command looks its `Channel` up by name at execution
 * time (see [ChannelCommand]) rather than closing over one, so edits to an existing channel's
 * format/permission/etc. take effect immediately regardless. A channel whose *command name* is
 * new since boot is the one real gap -- [registerNewChannelCommands] closes it via
 * [LegacyCommandRegistrar]'s reflection fallback, the one place in this plugin that still
 * needs it, because there genuinely isn't another way to make a brand-new command reachable
 * without a full server restart.
 *
 * Channels removed from channels.yml keep a dead command registered until the next restart
 * (nothing to "unregister" through either API) -- same limitation the old
 * `CommandManager.unregister()` had: it only cleared the executor, it never removed the entry
 * from commandMap either.
 */
object CommandRegistrar {

    private var registrar: Commands? = null

    // Channel command names known to the Brigadier dispatcher (registered at boot, or since
    // added via the legacy fallback this session) -- see [registerNewChannelCommands].
    private val knownChannelCommands = mutableSetOf<String>()

    fun install(plugin: JavaPlugin) {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            registrar = event.registrar()
            val commands = registrar ?: return@registerEventHandler
            val pluginMeta = plugin.pluginMeta

            commands.register(pluginMeta, SlateCmd.node(), null, emptyList())
            commands.register(pluginMeta, ChatColorCommand.node(), null, emptyList())
            commands.register(pluginMeta, Ch.node(), null, emptyList())
            ChannelsManager.getAllChannels().forEach { channel ->
                val name = channel.command.lowercase()
                commands.register(pluginMeta, ChannelCommand.node(name), null, emptyList())
                knownChannelCommands += name
            }
        }
    }

    /**
     * Call after `ChannelsManager.load()` inside `/slatechat reload`: registers, via
     * [LegacyCommandRegistrar]'s reflection fallback, any channel command that isn't already
     * known to the Brigadier dispatcher -- i.e. a channel added to channels.yml since boot
     * (or since the last call to this). Channel commands that already exist need nothing
     * here; see class doc.
     */
    fun registerNewChannelCommands(plugin: JavaPlugin) {
        ChannelsManager.getAllChannels()
            .map { it.command.lowercase() }
            .filter { it !in knownChannelCommands }
            .forEach { name ->
                val channel = ChannelsManager.getAllChannels().firstOrNull { it.command.lowercase() == name }
                val permission = channel?.takeIf { it.restrict }?.permission

                LegacyCommandRegistrar.registerDynamically(plugin, name, ChannelCommand.legacyExecutor(name), permission)
                knownChannelCommands += name
            }
    }
}
