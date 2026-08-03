package ch.deepslate.slatechat.core.model.channels

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Local
import net.md_5.bungee.api.ChatColor
import org.bukkit.configuration.file.FileConfiguration

class LocalImpl : Local {

    private val config: FileConfiguration
        get() = SlateChat.cConfig

    override val name: String = "local"
    override var enabled: Boolean = false

    override var command: String = "l"
    override var format: String = "<player>: <message>"
    override var broadcast: Boolean = true
    override var restrict: Boolean = false
    override var permission: String? = null
    override var radius: Int = 0
    override var actionbar: Boolean = false

    init {
        enabled = config.getBoolean("local.enable", true)
        if (enabled) load()
    }

    override fun isEnabled(): Boolean = enabled

    override fun load() {
        broadcast = config.getBoolean("local.broadcast", true)
        restrict = config.getBoolean("local.restrict", false)
        radius = config.getInt("local.radius", 0)
        permission = config.getString("local.permission")
        actionbar = config.getBoolean("local.actionbar", false)

        command = config.getString("local.command") ?: "l"

        format = ChatColor.translateAlternateColorCodes(
            '&',
            config.getString("local.format") ?: "<player>: <message>"
        )
    }
}
