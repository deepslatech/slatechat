package me.luucx7.simplexchat.listeners

import me.luucx7.simplexchat.SimplexChat
import me.luucx7.simplexchat.core.managers.ChannelsManager
import me.luucx7.simplexchat.core.managers.JogadorManager
import me.luucx7.simplexchat.core.managers.MessageManager
import me.luucx7.simplexchat.core.model.Mensagem
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import io.papermc.paper.event.player.AsyncChatEvent

class LocalListener : Listener {

    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onChat(event: AsyncChatEvent) {
        if (event.isCancelled) return

        val localChannel = ChannelsManager.local

        // Check if local channel is enabled
        if (!localChannel.isEnabled()) return

        var channel: me.luucx7.simplexchat.core.api.Channel = localChannel

        val player = event.player
        val rawText = legacy.serialize(event.message())
        val args = rawText.split(" ").toTypedArray()

        // Formatting options: lowercase or title case
        val processedText = when {
            SimplexChat.instance.config.getBoolean("modules.chatformat.lowercase") ->
                MessageManager.formatLowerCase(args).joinToString(" ")
            SimplexChat.instance.config.getBoolean("modules.chatformat.title_format") ->
                MessageManager.formatTitle(args).joinToString(" ")
            else -> rawText
        }

        // Spam check
        if (SimplexChat.instance.config.getBoolean("modules.chatformat.spam.enable")) {
            if (MessageManager.isSpam(args)) {
                val msg = SimplexChat.instance.config.getString("modules.chatformat.spam.message") ?: ""
                player.sendMessage(legacy.deserialize(msg))
                Bukkit.getLogger().info("[SimplexChat] Spam blocked from ${player.name}: $rawText")
                event.isCancelled = true
                return
            }
        }

        // Flood check (repeat)
        if (SimplexChat.instance.config.getBoolean("modules.chatformat.flood.enable")) {
            if (MessageManager.isFlood(player, processedText)) {
                val msg = SimplexChat.instance.config.getString("modules.chatformat.flood.message") ?: ""
                player.sendMessage(legacy.deserialize(msg))
                Bukkit.getLogger().info("[SimplexChat] Flood blocked from ${player.name}: $processedText")
                event.isCancelled = true
                return
            }
        }

        // Cancel vanilla chat
        event.isCancelled = true

        // If focus module is enabled, use player's focused channel
        if (SimplexChat.instance.config.getBoolean("modules.focus")) {
            JogadorManager.get(player).channel?.let {
                channel = it
            }
        }

        // Permission check
        if (channel.restrict && (channel.permission == null || !player.hasPermission(channel.permission!!))) {
            val msg = SimplexChat.instance.config.getString("no_permission") ?: ""
            player.sendMessage(legacy.deserialize(msg))
            return
        }

        // Process message through your system
        Mensagem(player, processedText, channel)
            .validar()
            .preparar()
            .enviar()

        // Track last message for flood detection
        MessageManager.setLastMessage(player, processedText)
    }
}
