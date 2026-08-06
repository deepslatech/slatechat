package ch.deepslate.slatechat.listeners

import ch.deepslate.slatechat.core.managers.FocusManager
import ch.deepslate.slatechat.core.managers.ChatPlayerManager
import ch.deepslate.slatechat.core.managers.UpdateChecker
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class ChatPlayerListener : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val chatPlayer = ChatPlayerManager.get(player)

        // If the world has a forced channel, apply it
        FocusManager.focusWorlds[player.world.name]?.let { worldChannel ->
            chatPlayer.channel = worldChannel
        }

        ChatPlayerManager.add(chatPlayer)

        // Let admins with chat.update know if a new version was found on startup
        UpdateChecker.notify(player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        ChatPlayerManager.remove(event.player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player

        // Apply world channel automatically when changing worlds
        FocusManager.focusWorlds[player.world.name]?.let { worldChannel ->
            ChatPlayerManager.get(player).channel = worldChannel
        }
    }
}
