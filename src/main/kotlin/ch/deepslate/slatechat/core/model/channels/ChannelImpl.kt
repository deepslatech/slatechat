package ch.deepslate.slatechat.core.model.channels

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Channel
import net.md_5.bungee.api.ChatColor
import org.bukkit.configuration.file.FileConfiguration

class ChannelImpl(override val name: String) : Channel {

    private val config: FileConfiguration
        get() = SlateChat.cConfig

    override var command: String = ""
        private set
    override var format: String = ""
        private set
    override var broadcast: Boolean = false
        private set
    override var restrict: Boolean = false
        private set
    override var permission: String? = null
        private set
    override var radius: Int = 0
        private set
    override var actionbar: Boolean = false
        private set

    init {
        load()
    }

    override fun load() {
        command = config.getString("$name.command") ?: name
        broadcast = config.getBoolean("$name.broadcast")
        restrict = config.getBoolean("$name.restrict")
        radius = config.getInt("$name.radius")
        permission = config.getString("$name.permission")
        actionbar = config.getBoolean("$name.actionbar")

        format = ChatColor.translateAlternateColorCodes(
            '&',
            config.getString("$name.format") ?: "<player>: <message>"
        )
    }
}
