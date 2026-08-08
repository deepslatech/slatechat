package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.common.update.GitLabReleaseCheck
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Checks GitLab's releases/permalink/latest endpoint (via [GitLabReleaseCheck], shared with the
 * velocity module) for a newer SlateChat version.
 *
 * Fully async and fail-silent: offline servers, GitLab downtime, or a forked/mirrored
 * project must never affect startup or chat. All player/console-facing text comes from
 * config.yml, same as every other message in the plugin -- nothing hardcoded.
 */
object UpdateChecker {

    private const val RELEASE_PAGE_URL = GitLabReleaseCheck.RELEASE_PAGE_URL

    @Volatile
    private var latestVersion: String? = null

    /**
     * Step 1 — check once on startup, off the main/region thread, and always print a
     * banner with the result (up to date / update found / couldn't check). The banner
     * frame itself is fixed (just branding, like ItemsAdder/LuckPerms do), but every
     * status line comes from config.yml, same as everything else in the plugin.
     */
    fun check() {
        if (!SlateChat.instance.config.getBoolean("modules.update_checker")) return

        Bukkit.getAsyncScheduler().runNow(SlateChat.instance) {
            val current = SlateChat.instance.pluginMeta.version
            val remote = GitLabReleaseCheck.fetchLatestTag()

            val status = when {
                remote == null ->
                    msg("update_console_check_failed", "<red>Could not check for updates.")

                GitLabReleaseCheck.isNewer(remote, current) -> {
                    latestVersion = remote
                    msg("update_console_log", "<yellow>A new version (<version>) is available! You are running <current>. Download: <url>")
                        .replace("<version>", remote)
                        .replace("<url>", RELEASE_PAGE_URL)
                }

                else ->
                    msg("update_console_up_to_date", "<green>You are running the latest version (<current>).")
            }.replace("<current>", current)

            printBanner(current, status)
        }
    }

    /**
     * A plain java.util.logging.Logger (plugin.logger) never translates color codes --
     * legacy or MiniMessage -- it just prints the raw string. The console sender, on the
     * other hand, is a real Adventure Audience and renders MiniMessage properly as ANSI,
     * same as sending a message to a player. Route the banner through it for that reason.
     */
    private fun printBanner(current: String, status: String) {
        val console = Bukkit.getConsoleSender()
        val border = Messages.deserialize("<gold>=======================================</gold>")
        console.sendMessage(border)
        console.sendMessage(Messages.deserialize("<white>SlateChat <aqua>v$current</aqua> by Deepslate Studios</white>"))
        console.sendMessage(Messages.deserialize(status))
        console.sendMessage(border)
    }

    /** Step 2 — notify players with chat.update on join, if an update was found. */
    fun notify(player: Player) {
        val remote = latestVersion ?: return
        if (!player.hasPermission("chat.update")) return

        player.sendMessage(Messages.deserialize(availableMsg(remote)))
    }

    /** Step 3 — /slatechat update: check on demand and report straight back to whoever asked. */
    fun checkNow(sender: CommandSender) {
        Bukkit.getAsyncScheduler().runNow(SlateChat.instance) {
            val current = SlateChat.instance.pluginMeta.version
            val remote = GitLabReleaseCheck.fetchLatestTag()

            if (remote == null) {
                sender.sendMessage(Messages.deserialize(msg("update_check_failed", "<red>Could not reach GitLab to check for updates. Try again later.")))
                return@runNow
            }

            if (GitLabReleaseCheck.isNewer(remote, current)) {
                latestVersion = remote
                sender.sendMessage(Messages.deserialize(availableMsg(remote)))
            } else {
                val upToDate = msg("update_up_to_date", "<green>You're already running the latest version (<current>).")
                    .replace("<current>", current)
                sender.sendMessage(Messages.deserialize(upToDate))
            }
        }
    }

    private fun availableMsg(remote: String): String {
        val current = SlateChat.instance.pluginMeta.version
        return msg(
            "update_available",
            "<yellow>A new SlateChat version (<green><version></green>) is available! You are running <red><current></red>."
        )
            .replace("<version>", remote)
            .replace("<current>", current)
    }

    private fun msg(path: String, default: String): String =
        Messages.raw(SlateChat.instance.config, path, default)
}
