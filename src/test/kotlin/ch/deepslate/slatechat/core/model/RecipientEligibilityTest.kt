package ch.deepslate.slatechat.core.model

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipientEligibilityTest {

    private val worldA = UUID.randomUUID()
    private val worldB = UUID.randomUUID()

    @Test
    fun `same world, within radius is eligible`() {
        val sender = RecipientEligibility.Position(worldA, 0.0, 64.0, 0.0)
        val candidate = RecipientEligibility.Position(worldA, 3.0, 64.0, 4.0) // distance 5
        assertTrue(RecipientEligibility.isWithinRadius(sender, candidate, radius = 5))
    }

    @Test
    fun `same world, outside radius is not eligible`() {
        val sender = RecipientEligibility.Position(worldA, 0.0, 64.0, 0.0)
        val candidate = RecipientEligibility.Position(worldA, 3.0, 64.0, 4.0) // distance 5
        assertFalse(RecipientEligibility.isWithinRadius(sender, candidate, radius = 4))
    }

    @Test
    fun `different world is never eligible, regardless of coordinates`() {
        val sender = RecipientEligibility.Position(worldA, 0.0, 64.0, 0.0)
        val candidate = RecipientEligibility.Position(worldB, 0.0, 64.0, 0.0) // same coords, other world
        assertFalse(RecipientEligibility.isWithinRadius(sender, candidate, radius = 1000))
    }

    @Test
    fun `distance exactly equal to radius is eligible (boundary is inclusive)`() {
        val sender = RecipientEligibility.Position(worldA, 0.0, 64.0, 0.0)
        val candidate = RecipientEligibility.Position(worldA, 3.0, 64.0, 4.0) // distance 5
        assertTrue(RecipientEligibility.isWithinRadius(sender, candidate, radius = 5))
    }

    @Test
    fun `zero distance is always eligible, even with a zero radius`() {
        val sender = RecipientEligibility.Position(worldA, 10.0, 64.0, -3.0)
        assertTrue(RecipientEligibility.isWithinRadius(sender, sender, radius = 0))
    }
}
