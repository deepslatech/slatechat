package me.luucx7.simplexchat.core.managers

import me.luucx7.simplexchat.core.api.ChatPlayer
import me.luucx7.simplexchat.core.model.Jogador
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object JogadorManager {

    private val chatPlayers: MutableMap<UUID, ChatPlayer> = ConcurrentHashMap()

    fun get(player: Player): ChatPlayer {
        return chatPlayers.computeIfAbsent(player.uniqueId) {
            Jogador(player)
        }
    }

    fun add(chatPlayer: ChatPlayer) {
        chatPlayers[chatPlayer.uuid] = chatPlayer
    }

    fun remove(player: Player) {
        chatPlayers.remove(player.uniqueId)
    }

    fun clear() {
        chatPlayers.clear()
    }

    fun getDefaultChannel() = ChannelsManager.local
}
