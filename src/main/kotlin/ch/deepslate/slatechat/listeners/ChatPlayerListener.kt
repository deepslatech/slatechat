package ch.deepslate.slatechat.listeners

import ch.deepslate.slatechat.core.managers.FocusManager
import ch.deepslate.slatechat.core.managers.JogadorManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class ChatPlayerListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val chatPlayer = JogadorManager.get(player)

        // If the world has a forced channel, apply it
        FocusManager.focusWorlds[player.world.name]?.let { worldChannel ->
            chatPlayer.channel = worldChannel
        }

        JogadorManager.add(chatPlayer)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        JogadorManager.remove(event.player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player

        // Apply world channel automatically when changing worlds
        FocusManager.focusWorlds[player.world.name]?.let { worldChannel ->
            JogadorManager.get(player).channel = worldChannel
        }
    }
}
