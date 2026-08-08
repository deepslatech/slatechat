package ch.deepslate.slatechat.velocity

import ch.deepslate.slatechat.velocity.commands.NetworkChannelCommand
import ch.deepslate.slatechat.velocity.commands.SlateCommand
import ch.deepslate.slatechat.velocity.config.loadNetworkChannels
import ch.deepslate.slatechat.velocity.network.ProxyMessenger
import ch.deepslate.slatechat.velocity.network.ProxyPing
import ch.deepslate.slatechat.velocity.update.UpdateCheck
import com.google.inject.Inject
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.nio.file.Path

// version omitted: @Plugin's fields must be compile-time constants, so unlike paper-plugin.yml's
// ${project.version} resource filtering, this can't be templated from gradle.properties without
// a generated-constants step. Velocity reads a missing version as "unknown". version.txt (see
// build.gradle.kts) covers the one place this module actually needs its own version at runtime:
// the update checker below.
@Plugin(
    id = "slatechat",
    name = "SlateChat",
    authors = ["Deepslate Studios"],
    url = "https://gitlab.com/deepslatech/slatechat",
)
class SlateChatVelocity @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path,
) {
    // Tracks what reloadNetworkChannels() last registered, so a later reload can unregister the
    // previous set before loading+registering the new one -- see SlateCommand's console-only
    // "reload" subcommand.
    private val registeredChannelAliases = mutableSetOf<String>()

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        reloadNetworkChannels()

        val slateMeta = server.commandManager.metaBuilder("slatechat").plugin(this).build()
        server.commandManager.register(slateMeta, BrigadierCommand(SlateCommand(::reloadNetworkChannels).node()))

        // slatechat:main plugin-messaging transport (see velocity/.../network/ProxyMessenger.kt)
        // -- registered as its own listener object, not a @Subscribe method on this class
        // directly, so it can be unit-testable/reusable independent of the plugin's own
        // lifecycle wiring. Must be registered with the event manager explicitly: unlike this
        // class's own @Subscribe methods (auto-registered by Velocity's plugin loader for the
        // @Plugin main class), a separate object needs `eventManager.register` called on it.
        val proxyMessenger = ProxyMessenger(server, logger)
        server.eventManager.register(this, proxyMessenger)
        proxyMessenger.install()
        ProxyPing.install(proxyMessenger)

        UpdateCheck.check(readBundledVersion(), logger)

        logger.info("SlateChat (Velocity) enabled.")
    }

    private fun reloadNetworkChannels() {
        registeredChannelAliases.forEach { server.commandManager.unregister(it) }
        registeredChannelAliases.clear()

        val seenAliases = mutableSetOf<String>()
        loadNetworkChannels(dataDirectory, logger).forEach { channel ->
            val alias = channel.command.lowercase()
            if (!seenAliases.add(alias)) {
                logger.error(
                    "Skipping network channel '{}': its command alias '{}' is already used by another channel",
                    channel.key,
                    channel.command,
                )
                return@forEach
            }

            val meta = server.commandManager.metaBuilder(channel.command)
                .plugin(this)
                .build()
            server.commandManager.register(meta, NetworkChannelCommand(server, logger, channel))
            registeredChannelAliases += alias
        }
    }

    private fun readBundledVersion(): String =
        javaClass.getResourceAsStream("/version.txt")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?.trim()
            ?: "unknown"
}
