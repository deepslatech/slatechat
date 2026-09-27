package ch.deepslate.slatechat.velocity.network

import ch.deepslate.slatechat.common.network.NetworkMessage
import ch.deepslate.slatechat.common.network.PayloadReader
import ch.deepslate.slatechat.common.network.PayloadWriter
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Velocity-side half of cross-server /tell (see paper's `TellRelay`/`TellRelayClient` for the
 * other end of every hop here). Three subchannels, one request/response round trip that may
 * fan out an extra hop of its own:
 *
 * 1. `tell-relay-request` (sender's backend -> here): resolve the target anywhere on the
 *    network, ask their own backend whether they're vanished (`tell-relay-vanish-query`), wait
 *    for `tell-relay-vanish-reply` -- or a 2s fail-open timeout, see [onRequest] -- then either
 *    deliver (`tell-relay-deliver` to the target's backend + `tell-relay-result` back to the
 *    sender's) or report not-found. `tell-relay-deliver` also carries the plain message text and
 *    the already-rendered spy line plus a pre-computed `exempt` flag, so the target's own backend
 *    can console-log the delivery and fan out to *its own* local socialspy watchers without a
 *    live sender `Player` to re-render anything from -- see `TellRelayClient.kt`'s own handler.
 * 2. `tell-spy-broadcast` (any backend -> here, fire-and-forget): a same-server tell that just
 *    happened locally, fanned out to any network-scope watcher elsewhere.
 *
 * No identity fields travel in `tell-relay-request` beyond what's needed -- the sender is
 * always `connection.player`, never restated in the payload.
 */
class TellRelayHandler(
    private val server: ProxyServer,
    private val messenger: ProxyMessenger,
    private val subscriptions: SocialSpySubscriptions,
    private val logger: Logger,
    private val plugin: Any,
) {

    private data class Inflight(
        val senderConnection: ServerConnection,
        val target: Player,
        val senderVanishBypass: Boolean,
        val senderSpyBypass: Boolean,
        val recipientLineJson: String,
        val spyLineJson: String,
        val messageText: String,
    )

    private val inflight = ConcurrentHashMap<UUID, Inflight>()

    fun install() {
        messenger.onMessage(REQUEST, ::onRequest)
        messenger.onMessage(VANISH_REPLY, ::onVanishReply)
        messenger.onMessage(SPY_BROADCAST, ::onSpyBroadcast)
    }

    private fun onRequest(connection: ServerConnection, message: NetworkMessage) {
        val sender = connection.player
        val r = PayloadReader(message.payload)
        val requestId = r.readUuid()
        val targetQuery = r.readString()
        val targetUuid = r.readOptionalUuid()
        val senderVanishBypass = r.readBoolean()
        val senderSpyBypass = r.readBoolean()
        val recipientLineJson = r.readString()
        val spyLineJson = r.readString()
        val messageText = r.readString()

        val target = (targetUuid?.let { server.getPlayer(it).orElse(null) }
            ?: targetQuery.takeIf { it.isNotBlank() }?.let { server.getPlayer(it).orElse(null) })
        if (target == null || target.uniqueId == sender.uniqueId) {
            replyNotFound(connection, requestId)
            return
        }

        val targetConnection = target.currentServer.orElse(null)
        if (targetConnection == null) {
            // Resolved via getPlayer() but has no live backend connection -- shouldn't really
            // happen (a connected proxy Player always has a current server), but treat as
            // not-found defensively rather than assuming.
            replyNotFound(connection, requestId)
            return
        }

        inflight[requestId] = Inflight(connection, target, senderVanishBypass, senderSpyBypass, recipientLineJson, spyLineJson, messageText)
        messenger.sendTo(targetConnection, PayloadWriter().writeUuid(requestId).build(VANISH_QUERY))

        // Fail-open fallback: if the target's own backend never answers (offline SlateChat,
        // slow tick, etc.), treat them as not vanished rather than blocking an ordinary
        // cross-server tell indefinitely. See the design's own trade-off note -- one-line flip
        // to `vanished = true` here for fail-closed instead, if that's ever preferred.
        // Runnable, not the bare lambda -- buildTask is overloaded on Runnable vs.
        // Consumer<ScheduledTask>, both SAM-convertible from a no-op-looking Kotlin lambda, so
        // the wrapper disambiguates which one this is.
        server.scheduler.buildTask(plugin, Runnable {
            val entry = inflight.remove(requestId) ?: return@Runnable
            logger.warn(
                "No vanish-check reply from '{}' within {}s for a tell targeting '{}' -- delivering anyway (fail-open).",
                targetConnection.serverInfo.name, VANISH_TIMEOUT_SECONDS, target.username,
            )
            resolve(requestId, entry, vanished = false)
        }).delay(VANISH_TIMEOUT_SECONDS, TimeUnit.SECONDS).schedule()
    }

    private fun onVanishReply(connection: ServerConnection, message: NetworkMessage) {
        val r = PayloadReader(message.payload)
        val requestId = r.readUuid()
        val vanished = r.readBoolean()
        val entry = inflight.remove(requestId) ?: return // already resolved by the timeout
        resolve(requestId, entry, vanished)
    }

    private fun resolve(requestId: UUID, entry: Inflight, vanished: Boolean) {
        val deliverable = !vanished || entry.senderVanishBypass
        if (!deliverable) {
            replyNotFound(entry.senderConnection, requestId)
            return
        }

        val targetConnection = entry.target.currentServer.orElse(null)
        if (targetConnection == null) {
            // Target disconnected (or changed servers, same net effect for this purpose)
            // mid-flight -- same outcome as not-found.
            replyNotFound(entry.senderConnection, requestId)
            return
        }

        // Computed before DELIVER goes out -- the target's own backend needs it too, to decide
        // whether to fan out to *its* local socialspy watchers (see TellRelayClient.kt's own
        // handler), same permission check this handler already used for the network fan-out
        // below.
        val exempt = entry.senderSpyBypass || entry.target.hasPermission(SPY_EXEMPT_PERMISSION)

        messenger.sendTo(
            targetConnection,
            PayloadWriter()
                .writeUuid(entry.senderConnection.player.uniqueId)
                .writeString(entry.senderConnection.player.username)
                .writeString(entry.recipientLineJson)
                .writeString(entry.messageText)
                .writeString(entry.spyLineJson)
                .writeBoolean(exempt)
                .build(DELIVER),
        )
        messenger.sendTo(
            entry.senderConnection,
            PayloadWriter()
                .writeUuid(requestId)
                .writeBoolean(true)
                .writeOptionalUuid(entry.target.uniqueId)
                .writeString(entry.target.username)
                .build(RESULT),
        )

        if (!exempt) {
            fanOutSpy(
                entry.spyLineJson,
                entry.senderConnection.serverInfo.name,
                targetConnection.serverInfo.name,
                exclude = setOf(entry.senderConnection.player.uniqueId, entry.target.uniqueId),
            )
        }
    }

    private fun onSpyBroadcast(connection: ServerConnection, message: NetworkMessage) {
        val r = PayloadReader(message.payload)
        val senderUuid = r.readUuid()
        val recipientUuid = r.readUuid()
        val spyLineJson = r.readString()
        // Both identities come from the payload, not connection.player -- the conduit for this
        // particular subchannel is genuinely ambiguous (paper's TellRelayClient.notifyLocalSpy
        // sends via whichever of sender/recipient happens to already own the thread), so
        // connection.player can't be trusted to mean "the sender" here the way it can for
        // every other subchannel in this handler.
        fanOutSpy(spyLineJson, connection.serverInfo.name, null, exclude = setOf(senderUuid, recipientUuid))
    }

    private fun fanOutSpy(spyLineJson: String, serverA: String, serverB: String?, exclude: Set<UUID>) {
        val component = GsonComponentSerializer.gson().deserialize(spyLineJson)
        subscriptions.watchersFor(serverA, serverB, exclude).forEach { uuid ->
            server.getPlayer(uuid).ifPresent { it.sendMessage(component) }
        }
    }

    private fun replyNotFound(connection: ServerConnection, requestId: UUID) {
        val payload = PayloadWriter()
            .writeUuid(requestId)
            .writeBoolean(false)
            .writeOptionalUuid(null)
            .writeString("")
            .build(RESULT)
        messenger.sendTo(connection, payload)
    }

    private companion object {
        const val REQUEST = "tell-relay-request"
        const val VANISH_QUERY = "tell-relay-vanish-query"
        const val VANISH_REPLY = "tell-relay-vanish-reply"
        const val DELIVER = "tell-relay-deliver"
        const val RESULT = "tell-relay-result"
        const val SPY_BROADCAST = "tell-spy-broadcast"
        const val SPY_EXEMPT_PERMISSION = "chat.tell.spy.bypass"
        const val VANISH_TIMEOUT_SECONDS = 2L
    }
}
