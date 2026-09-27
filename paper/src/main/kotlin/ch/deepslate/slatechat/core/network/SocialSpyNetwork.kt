package ch.deepslate.slatechat.core.network

import ch.deepslate.slatechat.common.network.PayloadReader
import ch.deepslate.slatechat.common.network.PayloadWriter
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Paper-side half of network-scope `/socialspy` -- talks to Velocity's `SocialSpySubscriptions`
 * (the actual source of truth) over [ProxyMessenger]. Deliberately keeps **no local cache** of
 * a player's active network scope: Velocity's own reply always echoes the resulting state back,
 * so there's nothing for this side to track between calls -- a Paper-side cache here would just
 * be a second copy of Velocity's own state with a drift risk, for no benefit.
 */
object SocialSpyNetwork {

    private const val SET_SCOPE = "socialspy-set-scope"
    private const val SET_SCOPE_RESULT = "socialspy-set-scope-result"

    data class ScopeResult(val ok: Boolean, val activeScope: String, val serverName: String)

    fun install() {
        ProxyMessenger.onMessage(SET_SCOPE_RESULT) { _, message ->
            val requestId = PayloadReader(message.payload).readUuid()
            PendingRequests.complete(requestId, message)
        }
    }

    /** Unconditional clear -- fire-and-forget, no response awaited. Structurally can't fail on
     *  Velocity's side (removing a possibly-absent map entry always succeeds), so there's
     *  nothing worth waiting on here. Called whenever local socialspy mode turns on, per the
     *  single-active-mode exclusivity rule -- see [ch.deepslate.slatechat.commands.SocialSpyCommand]. */
    fun clearScope(player: Player) {
        val payload = PayloadWriter()
            .writeUuid(UUID.randomUUID())
            .writeString("OFF")
            .writeString("")
            .build(SET_SCOPE)
        ProxyMessenger.send(player, payload)
    }

    /** [onResult] fires for both a successful scope change and a rejected one (unknown server
     *  name -- [ScopeResult.ok] is false); [onTimeout] fires only if the proxy never answers at
     *  all. Kept as two separate callbacks rather than folding timeout into a fake
     *  [ScopeResult] -- unlike tell's vanish-ambiguity requirement, there's no reason to hide
     *  "you typo'd a server name" from "the proxy didn't answer" here. */
    fun setScope(player: Player, scopeType: String, serverName: String, onResult: (ScopeResult) -> Unit, onTimeout: () -> Unit) {
        val requestId = UUID.randomUUID()
        PendingRequests.await(
            requestId,
            player,
            onResult = { reply ->
                val r = PayloadReader(reply.payload)
                r.readUuid() // requestId -- already correlated
                onResult(ScopeResult(r.readBoolean(), r.readString(), r.readString()))
            },
            onTimeout = onTimeout,
        )
        val payload = PayloadWriter()
            .writeUuid(requestId)
            .writeString(scopeType)
            .writeString(serverName)
            .build(SET_SCOPE)
        ProxyMessenger.send(player, payload)
    }
}
