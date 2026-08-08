package ch.deepslate.slatechat.velocity.network

import ch.deepslate.slatechat.common.network.NetworkMessage
import ch.deepslate.slatechat.common.network.ProxyChannel
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Velocity-side half of the [ProxyChannel.ID] plugin-messaging transport -- mirrors paper's own
 * `ProxyMessenger` almost exactly. Every backend-bound feature (tell relay, socialspy, vanish
 * sync, ...) registers a subchannel handler here via [onMessage] instead of subscribing to
 * `PluginMessageEvent` itself, so channel-matching/decoding/dispatch only has to be gotten right
 * in one place.
 *
 * Backend-originated traffic only for now (`event.source()` must be a [ServerConnection]) --
 * nothing on this channel is meant to come from a player's own client, and a real client could
 * in principle send arbitrary bytes on any channel it likes, so this isn't just an
 * optimization, it's the trust boundary: only a backend server we're proxying can reach these
 * handlers.
 */
class ProxyMessenger(private val server: ProxyServer, private val logger: Logger) {

    private val channelId: MinecraftChannelIdentifier = MinecraftChannelIdentifier.from(ProxyChannel.ID)
    private val handlers = ConcurrentHashMap<String, (ServerConnection, NetworkMessage) -> Unit>()

    fun install() {
        server.channelRegistrar.register(channelId)
    }

    /** Registers [handler] to run for every incoming message whose subchannel is [subchannel].
     *  Only one handler per subchannel -- see paper's ProxyMessenger.onMessage for why. */
    fun onMessage(subchannel: String, handler: (ServerConnection, NetworkMessage) -> Unit) {
        handlers[subchannel] = handler
    }

    fun sendTo(connection: ServerConnection, message: NetworkMessage) {
        connection.sendPluginMessage(channelId, message.encode())
    }

    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        if (event.identifier != channelId) return

        val source = event.source
        if (source !is ServerConnection) return

        // Namespaced channels aren't forwarded by default the way legacy ones sometimes were --
        // marking this handled either way keeps that behavior explicit rather than relying on
        // whatever Velocity's own default happens to be, and stops a malformed message below
        // from leaking anywhere else regardless.
        event.result = PluginMessageEvent.ForwardResult.handled()

        val message = try {
            NetworkMessage.decode(event.data)
        } catch (e: Exception) {
            logger.warn("Discarding a malformed {} message from '{}': {}", ProxyChannel.ID, source.serverInfo.name, e.message)
            return
        }

        val handler = handlers[message.subchannel]
        if (handler == null) {
            logger.warn("No handler registered for '{}' subchannel '{}'", ProxyChannel.ID, message.subchannel)
            return
        }
        handler(source, message)
    }
}
