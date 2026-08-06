package ch.deepslate.slatechat.commands

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.managers.ChannelsManager
import ch.deepslate.slatechat.core.model.Message
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ChannelCommand(private val commandName: String) : CommandExecutor, TabCompleter {

    private val mm: MiniMessage = MiniMessage.miniMessage()

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (sender !is Player) {
            sender.sendMessage(msg("players_only"))
            return true
        }

        val channel = ChannelsManager.channelsCache.keys
            .firstOrNull { it.command.equals(commandName, true) }
            ?: return true

        if (channel.restrict && (channel.permission == null || !sender.hasPermission(channel.permission!!))) {
            sender.sendMessage(msg("no_permission"))
            return true
        }

        if (args.isEmpty()) {
            val usage = SlateChat.instance.config
                .getString("channel_usage_message")
                ?.replace("<command>", commandName.lowercase())
                ?: "<red>Usage message missing"

            sender.sendMessage(mm.deserialize(usage))
            return true
        }

        // Run message processing async (Paper's Folia-aware scheduler)
        Bukkit.getAsyncScheduler().runNow(SlateChat.instance) {
            Message(sender, args.joinToString(" "), channel)
                .validate()
                .prepare()
                .send()
        }

        return true
    }

    /**
     * Suggests ItemsAdder `:emoji_name:` completions for the word currently being typed,
     * matching the same suggestions the vanilla chat box already offers via ItemsAdder's
     * own client-side chat completions -- those don't reach command arguments on their own,
     * since commands use Brigadier's separate tab-complete protocol.
     */
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!SlateChat.isItemsAdder || args.isEmpty()) return emptyList()

        val current = args.last()
        if (!current.startsWith(":")) return emptyList()

        val prefix = current.removePrefix(":")
        return itemsAdderEmojiNames()
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .sorted()
            .map { ":$it:" }
    }

    /** Bare (non-namespaced) font image names registered in ItemsAdder, via reflection. */
    private fun itemsAdderEmojiNames(): Set<String> {
        return try {
            val cls = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper")
            val getIds = cls.getMethod("getNamespacedIdsInRegistry")
            @Suppress("UNCHECKED_CAST")
            val namespacedIds = getIds.invoke(null) as Set<String>
            namespacedIds.mapNotNullTo(HashSet()) { id ->
                id.substringAfter(':', missingDelimiterValue = "").ifEmpty { null }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun msg(path: String) =
        mm.deserialize(
            SlateChat.instance.config.getString(path)
                ?: "<red>Missing message: $path"
        )
}
