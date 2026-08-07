package ch.deepslate.slatechat.core.model

import java.util.UUID
import kotlin.math.sqrt

/**
 * Whether a candidate location counts as "in range" of a sender's, for local/radius channels.
 * Pure data in, `Boolean` out -- no `Location`/`World`/`Player` involved on purpose: those are
 * live Bukkit objects tied to whichever region thread currently owns them, and under Folia a
 * thread may only read an entity's location while it owns that entity's region. [Position] is
 * an inert snapshot instead, safe to capture on one thread and hand to another (e.g. captured
 * on the sender's own thread, then compared against each recipient's own snapshot taken on
 * *their* thread -- see [MessageDelivery]).
 *
 * Also collapses what used to be the same radius check written out twice in `Message.kt` (once
 * for delivery, once for the actionbar read-receipt count) into the one place it lives.
 */
object RecipientEligibility {

    data class Position(val worldId: UUID, val x: Double, val y: Double, val z: Double)

    /** Matches `Location.distance()`'s exact semantics (real distance, not squared) so this is
     *  a drop-in replacement for the old `it.location.distance(sender.location) <= radius`. */
    fun isWithinRadius(sender: Position, candidate: Position, radius: Int): Boolean {
        if (sender.worldId != candidate.worldId) return false

        val dx = sender.x - candidate.x
        val dy = sender.y - candidate.y
        val dz = sender.z - candidate.z
        return sqrt(dx * dx + dy * dy + dz * dz) <= radius
    }
}
