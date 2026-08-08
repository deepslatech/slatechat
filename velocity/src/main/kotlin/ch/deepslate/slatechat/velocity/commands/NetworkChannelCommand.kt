package ch.deepslate.slatechat.velocity.commands

import ch.deepslate.slatechat.common.format.MiniMessageRenderer
import ch.deepslate.slatechat.velocity.config.NetworkChannel
import ch.deepslate.slatechat.velocity.integrations.PapiProxyBridge
import com.velocitypowered.api.command.RawCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger

// One instance per configured channel -- permission is checked live on every call via
// channel.permission (derived from the channel's config key, see NetworkChannel.kt), not cached,
// so there's no login/server-switch staleness window at all.
class NetworkChannelCommand(
    private val server: ProxyServer,
    private val logger: Logger,
    private val channel: NetworkChannel,
) : RawCommand {

    override fun hasPermission(invocation: RawCommand.Invocation): Boolean =
        invocation.source().hasPermission(channel.permission)

    override fun execute(invocation: RawCommand.Invocation) {
        val sender = invocation.source()
        if (sender !is Player) {
            logger.info("Only players can use the '${channel.key}' network channel.")
            return
        }

        val message = invocation.arguments()
        if (message.isBlank()) return

        val serverName = sender.currentServer.map { it.serverInfo.name }.orElse("?")
        val line = channel.format
            .replace("<player>", sender.username)
            .replace("<server>", serverName)
            .replace("<message>", message)

        // Resolving PAPI-style placeholders (if PAPIProxyBridge is installed) is inherently
        // async -- see PapiProxyBridge -- so delivery has to happen in the completion callback
        // rather than inline. Without the soft dependency present, PapiProxyBridge.resolve()
        // hands back an already-completed future with the line unchanged, so this callback still
        // runs effectively synchronously in that (default) case.
        PapiProxyBridge.resolve(line, sender.uniqueId).thenAccept { resolved ->
            val component = MiniMessageRenderer.render(resolved)
            logger.info("[{}] {} ({}): {}", channel.key, sender.username, serverName, message)

            server.allPlayers
                .filter { it.hasPermission(channel.permission) }
                .forEach { it.sendMessage(component) }
        }
    }
}
