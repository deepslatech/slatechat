package ch.deepslate.slatechat.core.api

import org.bukkit.permissions.Permissible

interface Channel {

    val name: String
    val command: String
    val format: String
    val broadcast: Boolean
    val restrict: Boolean
    val permission: String?
    val radius: Int
    val actionbar: Boolean

    fun load()
}

/**
 * Whether a restricted channel refuses this sender. Was duplicated three times across
 * Ch.kt/ChannelCommand.kt/LocalListener.kt, each with its own `permission!!` -- pulled out
 * here as the one place that owns the "no permission configured on a restricted channel
 * means nobody qualifies" rule, with no force-unwrap needed.
 *
 * OP still bypasses that misconfigured-channel case, same as every other permission check in
 * the plugin (undeclared nodes fall back to OP-only by default) -- this is the one path that
 * used to special-case its way around that and lock out the server owner along with everyone
 * else over a missing `permission:` in channels.yml.
 */
fun Channel.blocksSender(sender: Permissible): Boolean {
    if (!restrict) return false
    val perm = permission ?: return !sender.isOp
    return !sender.hasPermission(perm)
}
