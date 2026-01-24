package me.luucx7.simplexchat.core.managers

import me.luucx7.simplexchat.SimplexChat
import me.luucx7.simplexchat.commands.ChannelCommand
import me.luucx7.simplexchat.core.api.Channel
import me.luucx7.simplexchat.core.api.Local
import me.luucx7.simplexchat.core.model.channels.ChannelImpl
import me.luucx7.simplexchat.core.model.channels.LocalImpl
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
        SimplexChat.cConfig.getKeys(false)
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
        val cmdManager = CommandManager(SimplexChat.instance, cmdName)
        cmdManager.register(ChannelCommand(cmdName))
        commands[cmdName] = cmdManager
    }
}
