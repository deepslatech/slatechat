package ch.deepslate.slatechat.core.managers

import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandMap
import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Constructor

class CommandManager(plugin: JavaPlugin, name: String) {

    private val command: PluginCommand =
        plugin.getCommand(name) ?: registerDynamically(plugin, name)

    fun register(executor: CommandExecutor) {
        command.setExecutor(executor)
    }

    fun unregister() {
        command.setExecutor(null)
        command.tabCompleter = null
    }

    fun getCommand(): PluginCommand = command

    var description: String?
        get() = command.description
        set(value) { command.description = value ?: "" }

    var permission: String?
        get() = command.permission
        set(value) { command.permission = value }

    var usage: String?
        get() = command.usage
        set(value) { command.usage = value ?: "" }

    var aliases: List<String>
        get() = command.aliases
        set(value) { command.aliases = value }

    /**
     * Dynamically create and register a command when it is not declared in plugin.yml
     * (useful for channel commands loaded from configuration).
     */
    private fun registerDynamically(plugin: JavaPlugin, name: String): PluginCommand {
        val ctor: Constructor<PluginCommand> = PluginCommand::class.java.getDeclaredConstructor(String::class.java, org.bukkit.plugin.Plugin::class.java)
        ctor.isAccessible = true
        val dyn = ctor.newInstance(name, plugin)

        val commandMapField = plugin.server.javaClass.getDeclaredField("commandMap")
        commandMapField.isAccessible = true
        val commandMap = commandMapField.get(plugin.server) as CommandMap
        commandMap.register(plugin.pluginMeta.name.lowercase(), dyn)
        return dyn
    }
}
