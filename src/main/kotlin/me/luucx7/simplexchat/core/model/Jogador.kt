package me.luucx7.simplexchat.core.model

import me.luucx7.simplexchat.core.api.Channel
import me.luucx7.simplexchat.core.api.ChatPlayer
import me.luucx7.simplexchat.core.managers.JogadorManager
import org.bukkit.entity.Player

class Jogador(
    override val player: Player,
    override var channel: Channel? = null,
    override var color: String = ""
) : ChatPlayer {

    init {
        channel ?: let { channel = JogadorManager.getDefaultChannel() }
    }
}
