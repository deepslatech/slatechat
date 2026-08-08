package ch.deepslate.slatechat.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger

// version omitted: @Plugin's fields must be compile-time constants, so unlike paper-plugin.yml's
// ${project.version} resource filtering, this can't be templated from gradle.properties without
// a generated-constants step. Velocity reads a missing version as "unknown".
@Plugin(
    id = "slatechat",
    name = "SlateChat",
    authors = ["Deepslate Studios"],
    url = "https://gitlab.com/deepslatech/slatechat",
)
class SlateChatVelocity @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
) {
    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        logger.info("SlateChat (Velocity) enabled.")
    }
}
