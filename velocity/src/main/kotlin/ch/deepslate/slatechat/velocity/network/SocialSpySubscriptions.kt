package ch.deepslate.slatechat.velocity.network

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The authoritative registry of who's currently spying on the network, network-wide -- lives
 * only here, in Velocity's own memory, never persisted or cached on the Paper side (see paper's
 * `SocialSpyNetwork`'s own doc for why). Not durable across relog by design, unlike the local
 * PDC-backed toggle, per the approved design memo.
 */
class SocialSpySubscriptions {

    private val scopes = ConcurrentHashMap<UUID, SpyScope>()

    /** [requested] == `null` means unconditional off (used when local mode just turned on, and
     *  always succeeds even if there was nothing to clear). Otherwise applies the "run the same
     *  subcommand again to turn it off" toggle rule: setting the scope that's already active
     *  turns it off instead of leaving it unchanged. Returns the resulting scope (`null` = off). */
    fun setScope(uuid: UUID, requested: SpyScope?): SpyScope? {
        if (requested == null) {
            scopes.remove(uuid)
            return null
        }
        val next = if (scopes[uuid] == requested) null else requested
        if (next == null) scopes.remove(uuid) else scopes[uuid] = next
        return next
    }

    /** Every subscriber whose scope covers [serverA] (the tell's sender's server) or [serverB]
     *  (the recipient's, `null` for the same-server-broadcast case where there's only one
     *  server involved), minus [exclude] (always the tell's own sender+recipient -- they never
     *  spy on their own conversation). */
    fun watchersFor(serverA: String, serverB: String?, exclude: Set<UUID>): List<UUID> =
        scopes.entries
            .filter { (uuid, scope) ->
                uuid !in exclude && (scope is SpyScope.All || (scope is SpyScope.Server && (scope.name == serverA || scope.name == serverB)))
            }
            .map { it.key }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        scopes.remove(event.player.uniqueId)
    }
}
