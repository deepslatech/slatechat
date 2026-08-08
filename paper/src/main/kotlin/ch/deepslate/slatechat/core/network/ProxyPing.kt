package ch.deepslate.slatechat.core.network

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.common.network.NetworkMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/slatechat network` -- a live round-trip check of the [ProxyMessenger] transport, for
 * confirming this server is actually behind a Velocity proxy running SlateChat and that the
 * plugin-messaging channel is wired up correctly, without digging through either side's
 * console. Subchannels "ping"/"pong" carry no payload at all -- this only proves the pipe is
 * open, nothing about what travels over it once real features (tell relay, socialspy, vanish
 * sync) are built on top of the same transport.
 *
 * No timeout/failure path yet: if the proxy never replies (SlateChat-Velocity isn't installed,
 * or this server genuinely isn't behind a proxy), the command just never gets a second message.
 * Good enough for a first diagnostic; worth revisiting once there's a real feature whose own
 * absence-of-a-reply needs to fail loudly instead of silently.
 */
object ProxyPing {

    private const val PING = "ping"
    private const val PONG = "pong"

    fun install() {
        ProxyMessenger.onMessage(PONG) { player, _ -> onPong(player) }
    }

    fun send(sender: CommandSender) {
        // Console has no connection of its own to ride a plugin message on (see ProxyMessenger's
        // class doc) -- fall back to any online player as the conduit. The pong then lands on
        // whichever player that was, not necessarily back at the console; onPong logs it either
        // way so a console caller still sees confirmation.
        val via = sender as? Player ?: Bukkit.getOnlinePlayers().firstOrNull()
        if (via == null) {
            sender.sendMessage(Component.text("No online player available to reach the proxy through.", NamedTextColor.RED))
            return
        }

        ProxyMessenger.send(via, NetworkMessage(PING, ByteArray(0)))
        sender.sendMessage(Component.text("Ping sent to the proxy -- watch for a reply.", NamedTextColor.YELLOW))
    }

    /** Runs on [player]'s own owning thread already -- see ProxyMessenger.onPluginMessageReceived's
     *  Folia redispatch, this handler is registered through it, not called directly. */
    private fun onPong(player: Player) {
        SlateChat.instance.logger.info("Received a pong from the proxy via ${player.name} -- the slatechat:main transport is wired up correctly.")
        player.sendMessage(Component.text("Proxy connection OK (round trip confirmed).", NamedTextColor.GREEN))
    }
}
