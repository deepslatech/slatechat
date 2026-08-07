package ch.deepslate.slatechat.commands

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.blocksSender
import ch.deepslate.slatechat.core.managers.ChannelsManager
import ch.deepslate.slatechat.core.managers.ChatPlayerManager
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/** `/ch <channel>` -- switches the sender's active channel. Gated on the focus module being
 *  enabled purely for historical reasons (it's registered/unregistered alongside FocusManager
 *  in the pre-Brigadier code); nothing about its own logic is focus-specific. */
object Ch {

    private val mm = MiniMessage.miniMessage()

    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("ch")
            .requires { SlateChat.instance.config.getBoolean("modules.focus") }
            .executes { ctx -> switchChannel(ctx.source, null); Command.SINGLE_SUCCESS }
            .then(
                Commands.argument("channel", StringArgumentType.word())
                    .suggests { ctx, builder -> suggestChannels(ctx.source, builder) }
                    .executes { ctx ->
                        switchChannel(ctx.source, StringArgumentType.getString(ctx, "channel"))
                        Command.SINGLE_SUCCESS
                    }
            )
            .build()

    private fun switchChannel(source: CommandSourceStack, arg: String?) {
        val sender = source.sender
        if (sender !is Player) {
            sender.sendMessage(msg("players_only"))
            return
        }

        if (arg == null) {
            sender.sendMessage(msg("channel_command_usage"))
            return
        }

        val channel = ChannelsManager.channelsCache.keys.firstOrNull {
            it.name.equals(arg, true) || it.command.equals(arg, true)
        }

        if (channel == null || channel.blocksSender(sender)) {
            sender.sendMessage(msg("invalid_channel"))
            return
        }

        ChatPlayerManager.get(sender).channel = channel

        val message = (SlateChat.instance.config.getString("channel_changed")
            ?: "<green>Channel changed to <channelName> - <channelCommand>")
            .replace("<channelName>", channel.name)
            .replace("<channelCommand>", channel.command)

        sender.sendMessage(mm.deserialize(message))
    }

    private fun suggestChannels(
        source: CommandSourceStack,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        ChannelsManager.channelsCache.keys
            .filter { !it.blocksSender(source.sender) }
            .map { it.name }
            .filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }

    private fun msg(path: String) =
        mm.deserialize(
            SlateChat.instance.config.getString(path) ?: "<red>Missing message: $path"
        )
}
