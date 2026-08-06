package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.SlateChat
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.regex.Pattern

/**
 * Checks GitLab's releases/permalink/latest endpoint for a newer SlateChat version.
 * The CI pipeline already creates a GitLab Release (tag vX.Y.Z) on every version bump,
 * so this endpoint always reflects the latest published release for free.
 *
 * Fully async and fail-silent: offline servers, GitLab downtime, or a forked/mirrored
 * project must never affect startup or chat. All player/console-facing text comes from
 * config.yml, same as every other message in the plugin -- nothing hardcoded.
 */
object UpdateChecker {

    private const val RELEASE_API_URL =
        "https://gitlab.com/api/v4/projects/deepslatech%2Fslatechat/releases/permalink/latest"
    private const val RELEASE_PAGE_URL =
        "https://gitlab.com/deepslatech/slatechat/-/releases/permalink/latest"

    private val TAG_PATTERN: Pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([0-9.]+)\"")
    private val mm = MiniMessage.miniMessage()

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
            val remote = fetchLatestTag()

            val status = when {
                remote == null ->
                    msg("update_console_check_failed", "<red>Could not check for updates.")

                isNewer(remote, current) -> {
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
        val border = mm.deserialize("<gold>=======================================</gold>")
        console.sendMessage(border)
        console.sendMessage(mm.deserialize("<white>SlateChat <aqua>v$current</aqua> by Deepslate Studios</white>"))
        console.sendMessage(mm.deserialize(status))
        console.sendMessage(border)
    }

    /** Step 2 — notify players with chat.update on join, if an update was found. */
    fun notify(player: Player) {
        val remote = latestVersion ?: return
        if (!player.hasPermission("chat.update")) return

        player.sendMessage(mm.deserialize(availableMsg(remote)))
    }

    /** Step 3 — /slatechat update: check on demand and report straight back to whoever asked. */
    fun checkNow(sender: CommandSender) {
        Bukkit.getAsyncScheduler().runNow(SlateChat.instance) {
            val current = SlateChat.instance.pluginMeta.version
            val remote = fetchLatestTag()

            if (remote == null) {
                sender.sendMessage(mm.deserialize(msg("update_check_failed", "<red>Could not reach GitLab to check for updates. Try again later.")))
                return@runNow
            }

            if (isNewer(remote, current)) {
                latestVersion = remote
                sender.sendMessage(mm.deserialize(availableMsg(remote)))
            } else {
                val upToDate = msg("update_up_to_date", "<green>You're already running the latest version (<current>).")
                    .replace("<current>", current)
                sender.sendMessage(mm.deserialize(upToDate))
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
        SlateChat.instance.config.getString(path) ?: default

    /** GET the release API and pull out tag_name, or null on any failure. Never throws. */
    private fun fetchLatestTag(): String? {
        return try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // GitLab's permalink/latest endpoint answers with a 302 to the actual
                // release resource, not a direct 200 -- HttpClient doesn't follow
                // redirects by default, so this has to be explicit.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(RELEASE_API_URL))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) return null

            val matcher = TAG_PATTERN.matcher(response.body())
            if (!matcher.find()) return null

            matcher.group(1)
        } catch (_: Exception) {
            // Offline server, GitLab unreachable, forked project, etc.
            null
        }
    }

    /** Simple dot-separated numeric comparison — no need for full semver here. */
    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
