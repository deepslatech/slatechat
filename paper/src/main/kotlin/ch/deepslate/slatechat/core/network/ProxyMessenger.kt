package ch.deepslate.slatechat.core.network

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.common.network.NetworkMessage
import ch.deepslate.slatechat.common.network.ProxyChannel
import ch.deepslate.slatechat.core.model.DebugLog
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Paper-side half of the [ProxyChannel.ID] plugin-messaging transport -- mirrors velocity's own
 * `ProxyMessenger` almost exactly. Every proxy-bound feature (tell relay, socialspy, vanish
 * sync, ...) registers a subchannel handler here via [onMessage] instead of implementing
 * [PluginMessageListener] itself, so decoding/dispatch/Folia-redispatch only has to be gotten
 * right in one place.
 *
 * Sending *to* the proxy fundamentally needs a connected [Player] to ride along on -- there is
 * no Bukkit/Paper API for a plugin to open its own connection to the proxy. This isn't a
 * SlateChat limitation, it's how Velocity's own plugin-messaging API works: `Player#
 * sendPluginMessage` on a backend server writes to that specific player's client-bound
 * connection, and since that "client" is actually the proxy standing in for it, the proxy
 * receives the message and fires `PluginMessageEvent` on its own side instead. No player online
 * on this server means no way to reach the proxy at all, by design of the platform, not
 * something a change here could work around.
 */
object ProxyMessenger : PluginMessageListener {

    private val handlers = ConcurrentHashMap<String, (Player, NetworkMessage) -> Unit>()

    fun install(plugin: SlateChat) {
        plugin.server.messenger.registerOutgoingPluginChannel(plugin, ProxyChannel.ID)
        plugin.server.messenger.registerIncomingPluginChannel(plugin, ProxyChannel.ID, this)
    }

    fun uninstall(plugin: SlateChat) {
        plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, ProxyChannel.ID)
        plugin.server.messenger.unregisterIncomingPluginChannel(plugin, ProxyChannel.ID, this)
    }

    /** Registers [handler] to run for every incoming message whose subchannel is [subchannel].
     *  Only one handler per subchannel -- a second registration replaces the first, so a future
     *  reload path can re-register without leaking the previous closure. */
    fun onMessage(subchannel: String, handler: (Player, NetworkMessage) -> Unit) {
        handlers[subchannel] = handler
    }

    /** [via] only needs to be *a* connected player, not any particular one -- see the class doc
     *  for why the proxy receives this regardless of who's carrying it. */
    fun send(via: Player, message: NetworkMessage) {
        via.sendPluginMessage(SlateChat.instance, ProxyChannel.ID, message.encode())
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != ProxyChannel.ID) return

        val decoded = try {
            NetworkMessage.decode(message)
        } catch (e: Exception) {
            SlateChat.instance.logger.warning("Discarding a malformed ${ProxyChannel.ID} message: ${e.message}")
            return
        }

        val handler = handlers[decoded.subchannel]
        if (handler == null) {
            SlateChat.instance.logger.warning(
                "No handler registered for '${ProxyChannel.ID}' subchannel '${decoded.subchannel}'"
            )
            return
        }

        // Same Folia rule as everywhere else in this codebase a player is touched (see
        // Tell.kt/MessageDelivery.kt's own doc comments) -- redispatch onto the receiving
        // player's own EntityScheduler before handing off, so no downstream handler has to
        // think about which thread Paper delivered the packet on.
        val scheduled = player.scheduler.execute(
            SlateChat.instance,
            { handler(player, decoded) },
            {}, // player disconnected before we could dispatch to them -- nothing to do
            1L
        )
        if (!scheduled) DebugLog.log { "${player.name} was already offline before a '${decoded.subchannel}' message could be dispatched to them" }
    }
}
