package ch.deepslate.slatechat.core.managers

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks who each player last exchanged a /tell with, so /r knows who to reply to. Recorded
 * in both directions on every successful tell -- either side of a conversation can /r back,
 * same convention as every other plugin's tell/reply pair.
 *
 * ConcurrentHashMap, not a plain HashMap -- read/written from Tell.deliver(), which (like
 * Message.kt's chat pipeline) runs off the main thread via the sender's own EntityScheduler.
 */
object TellManager {

    private val lastPartner: MutableMap<UUID, UUID> = ConcurrentHashMap()

    fun recordConversation(a: Player, b: Player) {
        lastPartner[a.uniqueId] = b.uniqueId
        lastPartner[b.uniqueId] = a.uniqueId
    }

    fun replyTargetOf(player: Player): UUID? = lastPartner[player.uniqueId]

    fun remove(player: Player) {
        lastPartner.remove(player.uniqueId)
    }

    fun clear() = lastPartner.clear()
}
