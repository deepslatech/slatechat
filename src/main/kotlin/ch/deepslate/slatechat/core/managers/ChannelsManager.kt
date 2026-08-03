package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.commands.ChannelCommand
import ch.deepslate.slatechat.core.api.Channel
import ch.deepslate.slatechat.core.api.Local
import ch.deepslate.slatechat.core.model.channels.ChannelImpl
import ch.deepslate.slatechat.core.model.channels.LocalImpl
import java.util.concurrent.ConcurrentHashMap

object ChannelsManager {

    private val channels = ConcurrentHashMap<String, Channel>()
    private val commands = ConcurrentHashMap<String, CommandManager>()
    val canaisCache: Map<Channel, String>
        get() = channels.values.associateWith { it.command }

    private lateinit var _local: Local

    val local: Local
        get() = _local

    fun load() {
        disable() // avoid duplicating commands on reload

        // LOCAL
        _local = LocalImpl()
        registerChannel(_local)

        // OTHERS
        SlateChat.cConfig.getKeys(false)
            .filter { !it.equals("local", ignoreCase = true) }
            .forEach { key ->
                val channel = ChannelImpl(key)
                registerChannel(channel)
            }
    }

    fun disable() {
        commands.forEach { (_, cmdManager) ->
            cmdManager.unregister()
        }
        commands.clear()
        channels.clear()
    }

    fun getChannel(name: String): Channel? =
        channels[name.lowercase()]

    fun getAllChannels(): Collection<Channel> =
        channels.values

    private fun registerChannel(channel: Channel) {
        val name = channel.name.lowercase()
        channels[name] = channel

        // Register command executor for this channel (dynamic command creation if absent)
        val cmdName = channel.command.lowercase()
        val cmdManager = CommandManager(SlateChat.instance, cmdName)
        cmdManager.register(ChannelCommand(cmdName))
        commands[cmdName] = cmdManager
    }
}
