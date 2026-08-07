package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Channel
import org.bukkit.configuration.file.FileConfiguration

object FocusManager {

    val focusWorlds: MutableMap<String, Channel> = linkedMapOf()

    private val fConfig: FileConfiguration
        get() = SlateChat.fConfig

    fun prepare() {
        loadWorldFocus()
    }

    private fun loadWorldFocus() {
        focusWorlds.clear()

        fConfig.getStringList("worlds").forEach { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size < 2) return@forEach

            val world = parts[0]
            val channelName = parts[1]

            val channel = ChannelsManager.channelsCache.keys
                .firstOrNull { it.name.equals(channelName, ignoreCase = true) }
                ?: return@forEach

            focusWorlds[world] = channel
        }
    }

    /**
     * The `ch` command used to be registered/unregistered here alongside the focus module --
     * that's gone now that it's a Brigadier command gated live on `modules.focus` (see
     * [ch.deepslate.slatechat.commands.Ch]), so this just clears world/channel state on
     * disable. Note: only [prepare] (i.e. `modules.focus` being enabled) repopulates
     * `focusWorlds` -- same as before the Brigadier move, if the module gets turned off via
     * `/slatechat reload`, the map is cleared here and simply stays empty.
     */
    fun unload() {
        focusWorlds.clear()
    }
}
