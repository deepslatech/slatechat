package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.SlateChat
import org.bukkit.entity.Player
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object MessageManager {

    // Was a plain HashMap -- read/written from LocalListener.onChat, which reacts to
    // AsyncChatEvent and so already runs off the main thread today (not just after Folia
    // work). Every other per-player map in the codebase already uses ConcurrentHashMap
    // (ChatPlayerManager, ChannelsManager); this one had fallen behind.
    private val lastMessage: MutableMap<UUID, String> = ConcurrentHashMap()

    /* ================= FLOOD ================= */

    fun setLastMessage(player: Player, message: String) {
        lastMessage[player.uniqueId] = message.lowercase()
    }

    fun isFlood(player: Player, message: String): Boolean {
        return lastMessage[player.uniqueId]?.equals(message, ignoreCase = true) == true
    }

    /* ================= SPAM (REPEATED CHARACTERS) ================= */

    fun isSpam(args: Array<String>): Boolean {
        val limit = SlateChat.instance.config.getInt("modules.chatformat.spam.limit")
        val message = args.joinToString(" ").lowercase()

        var currentStreak = 1
        var maxStreak = 1

        for (i in 1 until message.length) {
            if (message[i] == message[i - 1]) {
                currentStreak++
                maxStreak = max(maxStreak, currentStreak)
                if (maxStreak > limit) return true
            } else {
                currentStreak = 1
            }
        }

        return false
    }

    /* ================= FORMATTING ================= */

    fun formatLowerCase(args: Array<String>): Array<String> {
        return args.joinToString(" ").lowercase().split(" ").toTypedArray()
    }

    fun formatTitle(args: Array<String>): Array<String> {
        val msg = args.joinToString(" ")
        if (msg.isEmpty()) return args

        val formatted = msg.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

        return formatted.split(" ").toTypedArray()
    }

    /* ================= CLEANUP (OPTIONAL) ================= */

    fun remove(player: Player) {
        lastMessage.remove(player.uniqueId)
    }

    fun clear() = lastMessage.clear()
}
