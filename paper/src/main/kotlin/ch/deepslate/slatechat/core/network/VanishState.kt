package ch.deepslate.slatechat.core.network

import org.bukkit.entity.Player

/**
 * Whether [player] is vanished, per the de-facto metadata-flag convention every major vanish
 * plugin already sets (Essentials, PremiumVanish, SuperVanish, CMI all use `"vanished"`) --
 * there's no unified vanish API to hook into instead, so this is the same coarse check the
 * approved cross-server design settled on. Scoped narrowly to tell-relay's own target
 * resolution (see [TellRelayHandler]/[TellRelayClient]) -- not the fuller cross-server
 * vanish-sync feature described elsewhere in the design, which stays a separate future stage.
 *
 * Deliberately not the same thing as [ch.deepslate.slatechat.commands.TellCommand]'s local
 * `Player.canSee()` check -- that's Bukkit's own native per-viewer visibility, unavailable
 * across servers at all, hence this coarser per-player flag instead. See the design memo for
 * why that loss of per-viewer nuance was an accepted trade-off, not an oversight.
 */
object VanishState {
    private const val METADATA_KEY = "vanished"

    fun isVanished(player: Player): Boolean = player.getMetadata(METADATA_KEY).any { it.asBoolean() }
}
