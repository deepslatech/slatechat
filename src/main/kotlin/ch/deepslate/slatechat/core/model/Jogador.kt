package ch.deepslate.slatechat.core.model

import ch.deepslate.slatechat.core.api.Channel
import ch.deepslate.slatechat.core.api.ChatPlayer
import ch.deepslate.slatechat.core.managers.JogadorManager
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
