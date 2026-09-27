package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.SlateChat
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

/**
 * Per-player /socialspy on/off state. Stored directly in the player's own
 * PersistentDataContainer instead of a separate file or in-memory map -- it survives
 * relogin/restart for free via vanilla playerdata NBT, with no cache to keep in sync and
 * nothing to clean up on quit.
 *
 * Folia note: like `hasPermission`/location, a player's PDC is only safe to read on the
 * thread that currently owns their region -- callers (Tell.kt's spy fan-out) already read
 * this on each watching player's own EntityScheduler, never on the sender's.
 */
object SocialSpyManager {

    private val key = NamespacedKey(SlateChat.instance, "socialspy")

    fun isEnabled(player: Player): Boolean =
        player.persistentDataContainer.get(key, PersistentDataType.BOOLEAN) ?: false

    /** Flips the flag and returns the new state. */
    fun toggle(player: Player): Boolean {
        val enabled = !isEnabled(player)
        player.persistentDataContainer.set(key, PersistentDataType.BOOLEAN, enabled)
        return enabled
    }

    /** Explicit set-to-off, distinct from [toggle] -- used when a network socialspy scope
     *  command needs to turn local mode off unconditionally (the two are mutually exclusive,
     *  one active mode at a time), where calling [toggle] would incorrectly flip it *on* if it
     *  happened to already be off. */
    fun disable(player: Player) {
        player.persistentDataContainer.set(key, PersistentDataType.BOOLEAN, false)
    }
}
