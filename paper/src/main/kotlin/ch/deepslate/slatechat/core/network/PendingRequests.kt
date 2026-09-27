package ch.deepslate.slatechat.core.network

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.common.network.NetworkMessage
import ch.deepslate.slatechat.core.model.DebugLog
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Correlation + timeout for the request/response pairs built on top of [ProxyMessenger] --
 * tell-relay's outcome and socialspy's scope-set acknowledgement both need this (a fire-and-
 * forget message alone can't tell a caller whether the proxy ever actually answered), so it's
 * one shared piece instead of two copies of the same correlation-id/timeout bookkeeping.
 *
 * [ProxyPing] doesn't use this -- its round trip has no player-facing outcome riding on whether
 * a reply ever arrives, so it was never worth the extra bookkeeping. Anything that *does* need
 * to tell a player "delivered" vs "nothing came back" should go through here instead of
 * reinventing its own timeout.
 */
object PendingRequests {

    private data class Pending(val playerId: UUID, val onResult: (NetworkMessage) -> Unit, val onTimeout: () -> Unit)

    private val pending = ConcurrentHashMap<UUID, Pending>()

    /**
     * Registers [onResult] to run when [complete] is called with this [requestId], or
     * [onTimeout] if nothing arrives within [timeoutTicks] (default 5s @ 20 TPS -- not
     * config-driven yet, this is a first cut, not a tuned value). Only one of the two ever
     * fires, whichever [ConcurrentHashMap.remove] wins the race -- see [complete].
     */
    fun await(
        requestId: UUID,
        player: Player,
        onResult: (NetworkMessage) -> Unit,
        onTimeout: () -> Unit,
        timeoutTicks: Long = 100L,
    ) {
        pending[requestId] = Pending(player.uniqueId, onResult, onTimeout)
        Bukkit.getAsyncScheduler().runDelayed(
            SlateChat.instance,
            {
                val entry = pending.remove(requestId) ?: return@runDelayed // already completed
                val target = Bukkit.getPlayer(entry.playerId) ?: return@runDelayed // logged off, nobody to notify
                val scheduled = target.scheduler.execute(SlateChat.instance, { entry.onTimeout() }, {}, 1L)
                if (!scheduled) DebugLog.log { "${target.name} went offline between a pending request timing out and dispatch" }
            },
            timeoutTicks * 50L,
            TimeUnit.MILLISECONDS,
        )
    }

    /**
     * Called from inside a [ProxyMessenger]-registered handler, which has already redispatched
     * onto the correlated player's own `EntityScheduler` before invoking it -- so [onResult]
     * runs on the right thread here with no redispatch of its own needed. [await]'s timeout
     * path has no incoming plugin message to piggyback that redispatch on, so it's the one
     * place that does it explicitly instead.
     */
    fun complete(requestId: UUID, message: NetworkMessage) {
        val entry = pending.remove(requestId) ?: return // unknown or already-timed-out id -- drop silently
        entry.onResult(message)
    }
}
