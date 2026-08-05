package ch.deepslate.slatechat.core.placeholders

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.commands.ChatColorCommand
import ch.deepslate.slatechat.core.managers.ChatPlayerManager
import org.bukkit.entity.Player

/**
 * Internal PlaceholderAPI expansion for SlateChat.
 */
class ChatPlaceholder(private val plugin: SlateChat) : PlaceholderExpansion() {

    override fun persist() = true
    override fun canRegister() = true

    override fun getAuthor(): String =
        plugin.description.authors.joinToString()

    override fun getIdentifier(): String = "chat"

    override fun getVersion(): String =
        plugin.description.version

    override fun onPlaceholderRequest(player: Player?, identifier: String): String? {
        player ?: return null

        val chatPlayer = ChatPlayerManager.get(player)

        return when (identifier.lowercase()) {

            "color" -> {
                val color = chatPlayer.color
                if (color.isBlank()) return ""
                
                // Return the color code itself so it can be applied to the message
                color
            }

            "channel" -> {
                val channel = chatPlayer.channel ?: return ""
                channel.name.replaceFirstChar { it.uppercase() }
            }

            else -> null
        }
    }

    private fun validateRGB(input: String): Boolean {
        val matcher = ChatColorCommand.RGB.matcher(input)
        return SlateChat.colorsConfig.getBoolean("enable_rgb") && matcher.matches()
    }
}
