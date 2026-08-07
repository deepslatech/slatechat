package ch.deepslate.slatechat.listeners

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.managers.FocusManager
import ch.deepslate.slatechat.core.managers.ChatPlayerManager
import ch.deepslate.slatechat.core.managers.TellManager
import ch.deepslate.slatechat.core.managers.UpdateChecker
import ch.deepslate.slatechat.core.model.JoinQuitMessages
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

        if (SlateChat.instance.config.getBoolean("modules.joinquit.enable")) {
            event.joinMessage(JoinQuitMessages.join(player))
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        if (SlateChat.instance.config.getBoolean("modules.joinquit.enable")) {
            event.quitMessage(JoinQuitMessages.quit(event.player))
        }

        ChatPlayerManager.remove(event.player)
        // Socialspy's own state lives in the player's PDC (see SocialSpyManager), nothing to
        // clean up here for it -- only the /r reply-target entry is in-memory.
        TellManager.remove(event.player)
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
