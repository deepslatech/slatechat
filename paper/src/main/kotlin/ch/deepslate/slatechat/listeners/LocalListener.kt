package ch.deepslate.slatechat.listeners

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.blocksSender
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.managers.ChannelsManager
import ch.deepslate.slatechat.core.managers.ChatPlayerManager
import ch.deepslate.slatechat.core.managers.MessageManager
import ch.deepslate.slatechat.core.model.Message
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import io.papermc.paper.event.player.AsyncChatEvent

class LocalListener : Listener {

    // Used only to flatten the player's raw chat Component into plain text for spam/flood checks
    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onChat(event: AsyncChatEvent) {
        if (event.isCancelled) return

        val localChannel = ChannelsManager.local

        // Check if local channel is enabled
        if (!localChannel.isEnabled()) return

        var channel: ch.deepslate.slatechat.core.api.Channel = localChannel

        val player = event.player
        val rawText = legacy.serialize(event.message())
        val args = rawText.split(" ").toTypedArray()

        // Formatting options: lowercase or title case
        val processedText = when {
            SlateChat.instance.config.getBoolean("modules.chatformat.lowercase") ->
                MessageManager.formatLowerCase(args).joinToString(" ")
            SlateChat.instance.config.getBoolean("modules.chatformat.title_format") ->
                MessageManager.formatTitle(args).joinToString(" ")
            else -> rawText
        }

        // Spam check
        if (SlateChat.instance.config.getBoolean("modules.chatformat.spam.enable")) {
            if (MessageManager.isSpam(args)) {
                player.sendMessage(Messages.resolve(SlateChat.instance.config, "modules.chatformat.spam.message", ""))
                SlateChat.instance.logger.info("Spam blocked from ${player.name}: $rawText")
                event.isCancelled = true
                return
            }
        }

        // Flood check (repeat)
        if (SlateChat.instance.config.getBoolean("modules.chatformat.flood.enable")) {
            if (MessageManager.isFlood(player, processedText)) {
                player.sendMessage(Messages.resolve(SlateChat.instance.config, "modules.chatformat.flood.message", ""))
                SlateChat.instance.logger.info("Flood blocked from ${player.name}: $processedText")
                event.isCancelled = true
                return
            }
        }

        // Cancel vanilla chat
        event.isCancelled = true

        // If focus module is enabled, use player's focused channel
        if (SlateChat.instance.config.getBoolean("modules.focus")) {
            ChatPlayerManager.get(player).channel?.let {
                channel = it
            }
        }

        // Permission check
        if (channel.blocksSender(player)) {
            player.sendMessage(Messages.resolve(SlateChat.instance.config, "no_permission", ""))
            return
        }

        // Process message through your system
        Message(player, processedText, channel)
            .validate()
            .prepare()
            .send()

        // Track last message for flood detection
        MessageManager.setLastMessage(player, processedText)
    }
}
