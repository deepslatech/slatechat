package me.luucx7.simplexchat.core.display

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.entity.Player
import java.time.Duration

/**
 * Modern display utilities using the official Adventure API.
 * Works in Paper/Spigot 1.16+ and future versions without NMS.
 */
object ActionBar {

    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    /**
     * Sends an action bar message to the player.
     */
    fun sendActionBar(player: Player, message: String) {
        player.sendActionBar(legacy.deserialize(message))
    }

    /**
     * Sends a title and subtitle to the player.
     *
     * @param fadeIn  ticks
     * @param stay    ticks
     * @param fadeOut ticks
     */
    fun sendTitle(
        player: Player,
        title: String,
        subtitle: String,
        fadeIn: Int,
        stay: Int,
        fadeOut: Int
    ) {
        val titleComponent = legacy.deserialize(title)
        val subtitleComponent = legacy.deserialize(subtitle)

        val times = Title.Times.times(
            Duration.ofMillis(fadeIn * 50L),
            Duration.ofMillis(stay * 50L),
            Duration.ofMillis(fadeOut * 50L)
        )

        player.showTitle(Title.title(titleComponent, subtitleComponent, times))
    }

    /**
     * Clears the player's current title.
     */
    fun clearTitle(player: Player) {
        player.clearTitle()
    }
}
