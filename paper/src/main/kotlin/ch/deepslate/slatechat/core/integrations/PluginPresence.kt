package ch.deepslate.slatechat.core.integrations

import org.bukkit.Bukkit

/**
 * Whether a plugin by this name is installed *and* currently enabled -- `getPlugin` alone
 * isn't enough, it happily returns a disabled instance. Every soft-dependency presence check
 * in this codebase (DiscordSRV, ItemsAdder) needs exactly this two-step check; one place for
 * it instead of copy-pasting the same `plugin != null && plugin.isEnabled` at each call site.
 */
object PluginPresence {
    fun isEnabled(name: String): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin(name)
        return plugin != null && plugin.isEnabled
    }
}
