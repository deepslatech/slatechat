package ch.deepslate.slatechat.commands

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Channel
import ch.deepslate.slatechat.core.api.blocksSender
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.integrations.EmojiSuggestions
import ch.deepslate.slatechat.core.managers.ChannelsManager
import ch.deepslate.slatechat.core.model.Message
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Bukkit
import org.bukkit.command.Command as BukkitCommand
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/** Per-channel command (`/g`, `/l`, ...), one instance registered per configured channel via
 *  [node] (the normal path -- see [ch.deepslate.slatechat.core.managers.CommandRegistrar]) or,
 *  for a channel command that's new since boot, [legacyExecutor] (the reflection fallback --
 *  see [ch.deepslate.slatechat.core.managers.LegacyCommandRegistrar]). Both share the exact
 *  same [sendUsage]/[sendMessage] logic below (and both defer emoji tab-completion to
 *  [ch.deepslate.slatechat.core.integrations.EmojiSuggestions]); the only difference is how
 *  Bukkit hands the command off. Looks its [Channel] up by `commandName` at execution time
 *  rather than closing over one, so it keeps working across `/slatechat reload` rebuilding
 *  every Channel as a new object, and quietly no-ops if the channel is later removed from
 *  channels.yml. */
object ChannelCommand {

    fun node(commandName: String): LiteralCommandNode<CommandSourceStack> =
        Commands.literal(commandName)
            // Hides the command from tab-complete/help for senders it blocks (also gates
            // execution -- Brigadier checks requires() before executes() runs, so there's no
            // need for a second blocksSender() check inside sendUsage/sendMessage below).
            // Covers the "restrict with no permission node" OP-only case too, which the old
            // PluginCommand.permission field had no way to express.
            .requires { source -> channelFor(commandName)?.let { !it.blocksSender(source.sender) } ?: true }
            .executes { ctx -> sendUsage(ctx.source.sender, commandName); Command.SINGLE_SUCCESS }
            .then(
                Commands.argument("message", StringArgumentType.greedyString())
                    .suggests { _, builder -> EmojiSuggestions.suggest(builder) }
                    .executes { ctx ->
                        sendMessage(ctx.source.sender, commandName, StringArgumentType.getString(ctx, "message"))
                        Command.SINGLE_SUCCESS
                    }
            )
            .build()

    /**
     * Legacy `CommandExecutor`/`TabCompleter` adapter for
     * [ch.deepslate.slatechat.core.managers.LegacyCommandRegistrar] -- delegates to the exact
     * same [sendUsage]/[sendMessage] logic [node] uses. Unlike [node]'s own
     * `.requires()`, this path can't hide the command from tab-complete/help via a native
     * permission field the same way -- `blocksSender()` inside sendUsage/sendMessage still
     * enforces it correctly either way, this only affects discoverability for a restricted
     * channel, and only until the next restart re-registers it through the normal path.
     */
    fun legacyExecutor(commandName: String): CommandExecutor = LegacyAdapter(commandName)

    private class LegacyAdapter(private val commandName: String) : CommandExecutor, TabCompleter {

        override fun onCommand(
            sender: CommandSender,
            command: BukkitCommand,
            label: String,
            args: Array<out String>
        ): Boolean {
            if (args.isEmpty()) {
                sendUsage(sender, commandName)
            } else {
                sendMessage(sender, commandName, args.joinToString(" "))
            }
            return true
        }

        override fun onTabComplete(
            sender: CommandSender,
            command: BukkitCommand,
            alias: String,
            args: Array<out String>
        ): List<String> {
            if (!SlateChat.isItemsAdder || args.isEmpty()) return emptyList()

            val current = args.last()
            if (!current.startsWith(":")) return emptyList()

            val prefix = current.removePrefix(":")
            return EmojiSuggestions.names()
                .filter { it.startsWith(prefix, ignoreCase = true) }
                .sorted()
                .map { ":$it:" }
        }
    }

    private fun channelFor(commandName: String): Channel? =
        ChannelsManager.channelsCache.keys.firstOrNull { it.command.equals(commandName, true) }

    private fun sendUsage(sender: CommandSender, commandName: String) {
        if (sender !is Player) {
            sender.sendMessage(msg("players_only"))
            return
        }

        channelFor(commandName) ?: return

        val usage = Messages.raw(SlateChat.instance.config, "channel_usage_message", "<red>Usage message missing")
            .replace("<command>", commandName.lowercase())

        sender.sendMessage(Messages.deserialize(usage))
    }

    private fun sendMessage(sender: CommandSender, commandName: String, text: String) {
        if (sender !is Player) {
            sender.sendMessage(msg("players_only"))
            return
        }

        val channel = channelFor(commandName) ?: return

        // Run message processing async (Paper's Folia-aware scheduler)
        Bukkit.getAsyncScheduler().runNow(SlateChat.instance) {
            Message(sender, text, channel)
                .validate()
                .prepare()
                .send()
        }
    }

    private fun msg(path: String) =
        Messages.resolve(SlateChat.instance.config, path, "<red>Missing message: $path")
}
