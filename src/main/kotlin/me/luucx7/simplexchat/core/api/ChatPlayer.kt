package me.luucx7.simplexchat.core.api

import org.bukkit.entity.Player
import java.util.*

interface ChatPlayer {

    val player: Player
    
    val uuid: UUID
        get() = player.uniqueId

    val name: String
        get() = player.name

    var channel: Channel?
    var color: String
}
