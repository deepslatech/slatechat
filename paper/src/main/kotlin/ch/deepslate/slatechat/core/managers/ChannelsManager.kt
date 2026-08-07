package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Channel
import ch.deepslate.slatechat.core.api.Local
import ch.deepslate.slatechat.core.model.channels.ChannelImpl
import ch.deepslate.slatechat.core.model.channels.LocalImpl
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns channel data loaded from channels.yml. Command registration is a separate concern
 * now (see [CommandRegistrar]) -- Paper's Brigadier commands don't need registering or
 * unregistering per reload the way the old reflection-backed PluginCommand did, so this
 * object only tracks the channels themselves.
 */
object ChannelsManager {

    private val channels = ConcurrentHashMap<String, Channel>()
    val channelsCache: Map<Channel, String>
        get() = channels.values.associateWith { it.command }

    private lateinit var _local: Local

    val local: Local
        get() = _local

    fun load() {
        channels.clear()

        // LOCAL
        _local = LocalImpl()
        channels[_local.name.lowercase()] = _local

        // OTHERS
        SlateChat.cConfig.getKeys(false)
            .filter { !it.equals("local", ignoreCase = true) }
            .forEach { key ->
                val channel = ChannelImpl(key)
                channels[channel.name.lowercase()] = channel
            }
    }

    fun disable() {
        channels.clear()
    }

    fun getChannel(name: String): Channel? =
        channels[name.lowercase()]

    fun getAllChannels(): Collection<Channel> =
        channels.values
}
