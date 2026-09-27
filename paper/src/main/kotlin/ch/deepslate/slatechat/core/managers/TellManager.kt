package ch.deepslate.slatechat.core.managers

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks who each player last exchanged a /tell with, so /r knows who to reply to. Recorded
 * in both directions on every successful same-server tell -- either side of a conversation can
 * /r back, same convention as every other plugin's tell/reply pair. A cross-server tell only
 * ever records its own local half (see [ch.deepslate.slatechat.core.network.TellRelayClient]'s
 * `tell-relay-deliver` handler and [ch.deepslate.slatechat.core.model.TellRelay]'s own result
 * handler) -- each backend only ever learns about the side of the conversation that happened on
 * it.
 *
 * [ReplyTarget] carries a name alongside the UUID because a cross-server `/r`'s pre-rendered
 * `<target>` placeholder has no live [Player] to read `.name` from at render time -- see
 * [ch.deepslate.slatechat.core.model.TellRelay]'s own doc for why that's cosmetic-only risk
 * (delivery routing itself always stays UUID-based).
 *
 * ConcurrentHashMap, not a plain HashMap -- read/written from Tell.deliver()/TellRelay.deliver(),
 * which (like Message.kt's chat pipeline) runs off the main thread via the sender's own
 * EntityScheduler.
 */
object TellManager {

    data class ReplyTarget(val uuid: UUID, val name: String)

    private val lastPartner: MutableMap<UUID, ReplyTarget> = ConcurrentHashMap()

    fun recordConversation(a: Player, b: Player) {
        lastPartner[a.uniqueId] = ReplyTarget(b.uniqueId, b.name)
        lastPartner[b.uniqueId] = ReplyTarget(a.uniqueId, a.name)
    }

    /** Cross-server variant -- only ever knows the partner's uuid+name, never a live [Player].
     *  One-directional by nature: each backend only ever has its own half of the conversation
     *  to record. */
    fun recordConversation(self: UUID, partner: ReplyTarget) {
        lastPartner[self] = partner
    }

    fun replyTargetOf(player: Player): ReplyTarget? = lastPartner[player.uniqueId]

    fun remove(player: Player) {
        lastPartner.remove(player.uniqueId)
    }

    fun clear() = lastPartner.clear()
}
