package me.luucx7.simplexchat.core.model

import me.clip.placeholderapi.PlaceholderAPI
import me.luucx7.simplexchat.SimplexChat
import me.luucx7.simplexchat.core.api.Channel
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.regex.Pattern
import java.util.regex.Matcher
import me.luucx7.simplexchat.core.model.MineDownConverter

class Mensagem(
    private val sender: Player,
    rawMessage: String,
    private val channel: Channel
) {

    private var message: String = rawMessage
    private val legacy = LegacyComponentSerializer.legacyAmpersand()
    private val mm = MiniMessage.miniMessage()
    
    companion object {
        private val HEX_PATTERN: Pattern = Pattern.compile("#([0-9a-fA-F]{6})")
    }

    /** Step 1 — validation (filter, caps, etc) */
    fun validar(): Mensagem {
        if (!SimplexChat.useFilter) return this

        val original = message

        // Process blocked words (censor with asterisks)
        val blockedWords = SimplexChat.filterConfig.getStringList("blocked_words")
        for (word in blockedWords) {
            if (message.contains(word, ignoreCase = true)) {
                message = message.replace(word, "*".repeat(word.length), true)
                logFilter("blocked", word, original)
            }
        }
        
        // Process replacements (replace word A with word B)
        val replacements = SimplexChat.filterConfig.getStringList("replace")
        for (entry in replacements) {
            val parts = entry.split("->", limit = 2)
            if (parts.size == 2) {
                val from = parts[0]
                val to = parts[1]
                if (message.contains(from, ignoreCase = true)) {
                    message = message.replace(from, to, true)
                    logFilter("replace", "$from->$to", original)
                }
            }
        }
        
        return this
    }

    /** Step 2 — formatting + placeholders */
    fun preparar(): Mensagem {
        message = PlaceholderAPI.setPlaceholders(sender, message)
        return this
    }

    /** Step 3 — send */
    fun enviar() {
        // Main thread only for Bukkit actions
        Bukkit.getScheduler().runTask(SimplexChat.instance, Runnable {
            // Strip styles (bold/italic/etc.) without wiping colors to preserve channel/chat colors
            val safeMessage = "<bold:false><italic:false><underlined:false><strikethrough:false><obfuscated:false>$message"
            val formatted = channel.format
                .replace("<player>", sender.name)
                .replace("<message>", safeMessage)
                .replace("<channel>", channel.name)

            // Apply PlaceholderAPI to the entire formatted string
            var withPlaceholders = PlaceholderAPI.setPlaceholders(sender, formatted)
            
            // Convert MineDown syntax to MiniMessage
            withPlaceholders = MineDownConverter.convert(withPlaceholders)
            // Support line breaks using <br> or literal \n from configs
            withPlaceholders = convertBreaks(withPlaceholders)
            // Convert hex colors (#RRGGBB) to hex MiniMessage format <#RRGGBB>
            withPlaceholders = convertHexToMiniMessage(withPlaceholders)
            // Convert ALL legacy codes to MiniMessage BEFORE deserializing
            withPlaceholders = convertLegacyToMiniMessage(withPlaceholders)
            // Now deserialize with MiniMessage (all tags are MiniMessage format)
            val component = mm.deserialize(withPlaceholders)

            // Log to console if enabled
            if (SimplexChat.instance.config.getBoolean("log_to_console", true)) {
                val consoleFormat = SimplexChat.instance.config.getString("console_log") 
                    ?: "[<channel>] <player>: <message>"
                
                val consoleMsg = consoleFormat
                    .replace("<channel>", channel.name)
                    .replace("<channelCmd>", channel.command)
                    .replace("<player>", sender.name)
                    .replace("<message>", message)
                
                // Apply PlaceholderAPI for console log
                val consoleFinal = PlaceholderAPI.setPlaceholders(sender, consoleMsg)
                
                // Strip color codes for cleaner console output
                val stripped = consoleFinal.replace(Regex("&[0-9a-fk-or]"), "")
                Bukkit.getLogger().info(stripped)

                // Send to DiscordSRV main channel if enabled explicitly to avoid duplicates
                if (SimplexChat.isDiscordSRV && SimplexChat.instance.config.getBoolean("modules.discordsrv_send", false)) {
                    sendToDiscord(stripped)
                }
            }

            if (channel.broadcast) {
                Bukkit.getOnlinePlayers().forEach { sendTo(it, component) }
            } else {
                val radius = channel.radius
                Bukkit.getOnlinePlayers()
                    .filter { it.world == sender.world }
                    .filter { it.location.distance(sender.location) <= radius }
                    .forEach { sendTo(it, component) }
            }
        })
    }
    
    private fun convertHexToMiniMessage(text: String): String {
        val matcher = HEX_PATTERN.matcher(text)
        val buffer = StringBuffer()
        
        while (matcher.find()) {
            val hexCode = matcher.group(1)
            val miniHex = "<#$hexCode>"
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(miniHex))
        }
        
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    private fun convertBreaks(text: String): String {
        // Replace literal \n or HTML-style <br> with actual newline
        return text
            .replace("\\n", "\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
    }

    private fun convertLegacyToMiniMessage(text: String): String {
        // Convert legacy &X codes to MiniMessage tags
        var result = text
        // Support both & and § legacy prefixes
        val codes = listOf('&', '§')
        codes.forEach { p ->
            result = result
                .replace("${p}0", "<black>")
                .replace("${p}1", "<dark_blue>")
                .replace("${p}2", "<dark_green>")
                .replace("${p}3", "<dark_aqua>")
                .replace("${p}4", "<dark_red>")
                .replace("${p}5", "<dark_purple>")
                .replace("${p}6", "<gold>")
                .replace("${p}7", "<gray>")
                .replace("${p}8", "<dark_gray>")
                .replace("${p}9", "<blue>")
                .replace("${p}a", "<green>")
                .replace("${p}b", "<aqua>")
                .replace("${p}c", "<red>")
                .replace("${p}d", "<light_purple>")
                .replace("${p}e", "<yellow>")
                .replace("${p}f", "<white>")
                .replace("${p}l", "<bold>")
                .replace("${p}o", "<italic>")
                .replace("${p}n", "<underlined>")
                .replace("${p}m", "<strikethrough>")
                .replace("${p}k", "<obfuscated>")
                .replace("${p}r", "<reset>")
        }
        return result
    }

    private fun logFilter(reason: String, detail: String, original: String) {
        if (!SimplexChat.instance.config.getBoolean("log_to_console", true)) return
        Bukkit.getLogger().info("[SimplexChat][$reason] ${sender.name}: '$original' (detail: $detail)")
    }

    private fun sendTo(player: Player, component: Component) {
        val audience: Audience = player
        audience.sendMessage(component)

        // Action bar shows how many players received the message (not the message itself)
        if (channel.actionbar && player.uniqueId == sender.uniqueId) {
            val count = if (channel.broadcast) {
                Bukkit.getOnlinePlayers().size - 1
            } else {
                Bukkit.getOnlinePlayers()
                    .count { it.world == sender.world && it.location.distance(sender.location) <= channel.radius && it.uniqueId != sender.uniqueId }
            }
            
            val actionbarMsg = SimplexChat.instance.config.getString("amount_readed")
                ?.replace("<amount>", count.toString()) ?: ""
            
            if (actionbarMsg.isNotBlank()) {
                audience.sendActionBar(mm.deserialize(actionbarMsg))
            }
        }
    }

    private fun sendToDiscord(content: String) {
        try {
            val cls = Class.forName("github.scarsz.discordsrv.DiscordSRV")
            val getPlugin = cls.getMethod("getPlugin")
            val discordSrv = getPlugin.invoke(null)
            val mainChannelMethod = cls.getMethod("getMainTextChannel")
            val mainChannel = mainChannelMethod.invoke(discordSrv)
            if (mainChannel == null) return

            val sendMethod = mainChannel.javaClass.getMethod("sendMessage", CharSequence::class.java)
            val action = sendMethod.invoke(mainChannel, content)
            val queueMethod = action.javaClass.getMethod("queue")
            queueMethod.invoke(action)
        } catch (_: ClassNotFoundException) {
            // DiscordSRV not present
        } catch (_: Exception) {
            // Silently ignore DiscordSRV send failures to avoid impacting chat
        }
    }

}
