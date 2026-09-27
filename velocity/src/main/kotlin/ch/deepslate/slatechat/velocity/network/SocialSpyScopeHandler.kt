package ch.deepslate.slatechat.velocity.network

import ch.deepslate.slatechat.common.network.NetworkMessage
import ch.deepslate.slatechat.common.network.PayloadReader
import ch.deepslate.slatechat.common.network.PayloadWriter
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import java.util.UUID

/** Handles `socialspy-set-scope` (paper's [ch.deepslate.slatechat.core.network.SocialSpyNetwork])
 *  -- validates the request, applies it to [subscriptions], replies with the resulting state. */
class SocialSpyScopeHandler(
    private val server: ProxyServer,
    private val messenger: ProxyMessenger,
    private val subscriptions: SocialSpySubscriptions,
) {

    fun install() {
        messenger.onMessage(SET_SCOPE, ::onSetScope)
    }

    private fun onSetScope(connection: ServerConnection, message: NetworkMessage) {
        val r = PayloadReader(message.payload)
        val requestId = r.readUuid()
        val scopeType = r.readString()
        val serverName = r.readString()

        val requested: SpyScope? = when (scopeType) {
            "OFF" -> null
            "ALL" -> SpyScope.All
            "SERVER" -> {
                if (server.getServer(serverName).isEmpty) {
                    reply(connection, requestId, ok = false, active = "OFF", name = "")
                    return
                }
                SpyScope.Server(serverName)
            }
            else -> {
                reply(connection, requestId, ok = false, active = "OFF", name = "")
                return
            }
        }

        val result = subscriptions.setScope(connection.player.uniqueId, requested)
        val activeType = when (result) {
            null -> "OFF"
            is SpyScope.All -> "ALL"
            is SpyScope.Server -> "SERVER"
        }
        val activeName = (result as? SpyScope.Server)?.name ?: ""
        reply(connection, requestId, ok = true, active = activeType, name = activeName)
    }

    private fun reply(connection: ServerConnection, requestId: UUID, ok: Boolean, active: String, name: String) {
        val payload = PayloadWriter()
            .writeUuid(requestId)
            .writeBoolean(ok)
            .writeString(active)
            .writeString(name)
            .build(SET_SCOPE_RESULT)
        messenger.sendTo(connection, payload)
    }

    private companion object {
        const val SET_SCOPE = "socialspy-set-scope"
        const val SET_SCOPE_RESULT = "socialspy-set-scope-result"
    }
}
