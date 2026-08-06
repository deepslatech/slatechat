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
    val channelsCache: Map<Channel, String>
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

        // Wire the permission natively so Bukkit hides the command from tab-complete/help
        // for senders who lack it -- a nicer UX than just letting them discover it and get
        // denied. Only when there's an actual node to check: `restrict: true` with no
        // `permission` set means "nobody qualifies", which Bukkit has no way to express via
        // command.permission (null there means "unrestricted") -- that case is still caught
        // by Channel.blocksSender() inside ChannelCommand.onCommand(), same as before.
        //
        // Trade-off worth knowing: Bukkit checks this *before* onCommand() runs, so a sender
        // without permission sees the server's generic denial message instead of SlateChat's
        // own configurable `no_permission` -- only ChannelCommand's own blocksSender() check
        // (unreachable via this path, but still hit for e.g. /ch) shows that message.
        if (channel.restrict && channel.permission != null) {
            cmdManager.permission = channel.permission
        }

        commands[cmdName] = cmdManager
    }
}
