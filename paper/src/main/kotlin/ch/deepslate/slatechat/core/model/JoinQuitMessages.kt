package ch.deepslate.slatechat.core.model

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.format.MineDownConverter
import ch.deepslate.slatechat.common.format.MiniMessageRenderer
import ch.deepslate.slatechat.core.integrations.ItemsAdderBridge
import ch.deepslate.slatechat.core.integrations.PaperSelectorResolver
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Builds the join/quit/first-join broadcast messages ChatPlayerListener hands to
 * `PlayerJoinEvent#joinMessage`/`PlayerQuitEvent#quitMessage` -- or null, to suppress one
 * entirely (chat.join.bypass, or the format itself left blank in joinquit.yml).
 *
 * These are entirely admin-authored template text (joinquit.yml), same as a channel's own
 * `format` or tell.yml's -- there's no player-typed content anywhere in them, so unlike
 * Message/Tell there's nothing here for ChatFilter/ChatPermissionGate to gate. Otherwise the
 * same PlaceholderAPI -> MineDown -> ItemsAdder -> MiniMessageRenderer -> PaperSelectorResolver
 * pipeline every other message in this plugin goes through.
 *
 * No Folia scheduling needed either, unlike Message/Tell's cross-player fan-out: this only
 * ever reads the joining/leaving player's own state, and PlayerJoinEvent/PlayerQuitEvent
 * already fire on the thread that owns them. Bukkit/Paper's own engine code broadcasts the
 * resulting Component to everyone once the event finishes, the same as it would a plain
 * String -- not something this plugin has to do by hand.
 */
object JoinQuitMessages {

    /** Hardcoded, like every other protocol-level permission in this plugin -- see
     *  TellCommand.kt's class doc for why. Named ".bypass", matching every other permission
     *  in this plugin that lets you skip something (chat.hover.bypass, chat.count.bypass,
     *  chat.tell.spy.bypass, chat.tell.vanish.bypass) -- one consistent suffix for "this
     *  permission bypasses X" rather than a different word per feature. Covers join, quit,
     *  and first_join alike; staff joining vanished (from some other plugin) almost always
     *  have this too, so there's no separate vanish-plugin detection needed on top of it. */
    private const val BYPASS_PERMISSION = "chat.join.bypass"

    fun join(player: Player): Component? {
        if (player.hasPermission(BYPASS_PERMISSION)) return null
        val path = if (player.hasPlayedBefore()) "join" else "first_join"
        return render(player, path)
    }

    fun quit(player: Player): Component? {
        if (player.hasPermission(BYPASS_PERMISSION)) return null
        return render(player, "quit")
    }

    private fun render(player: Player, path: String): Component? {
        val template = SlateChat.joinQuitConfig.getString("joinquit.$path") ?: return null
        if (template.isBlank()) return null

        val formatted = template
            .replace("<player>", player.name)
            .replace("<online>", Bukkit.getOnlinePlayers().size.toString())
            .replace("<max>", Bukkit.getMaxPlayers().toString())

        var withPlaceholders = PlaceholderAPI.setPlaceholders(player, formatted)
        withPlaceholders = MineDownConverter.convert(withPlaceholders, player)
        withPlaceholders = ItemsAdderBridge.applyFontImages(player, withPlaceholders)
        val component = MiniMessageRenderer.render(withPlaceholders)
        return PaperSelectorResolver.resolve(component, player)
    }
}
