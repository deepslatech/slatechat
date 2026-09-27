package ch.deepslate.slatechat.commands

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.integrations.EmojiSuggestions
import ch.deepslate.slatechat.core.managers.TellManager
import ch.deepslate.slatechat.core.model.Tell
import ch.deepslate.slatechat.core.model.TellRelay
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.permissions.Permissible
import java.util.concurrent.CompletableFuture

/**
 * `/tell` (registered with `msg`/`w` as aliases -- see CommandRegistrar) and `/r`.
 *
 * Both permission nodes below are hardcoded rather than read from tell.yml, on purpose:
 * unlike a channel's `permission:` field (expected to vary per-server), a typo'd node here
 * would silently break /tell for an entire server with no obvious cause. tell.yml only
 * controls *whether* a node is required at all ([tellRestricted]), never *which* node.
 */
object TellCommand {

    private const val USE_PERMISSION = "chat.tell"
    private const val VANISH_BYPASS_PERMISSION = "chat.tell.vanish.bypass"

    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("tell")
            .requires { source -> tellEnabled() && canUse(source.sender) }
            .executes { ctx -> sendUsage(ctx.source.sender); Command.SINGLE_SUCCESS }
            .then(
                Commands.argument("target", StringArgumentType.word())
                    .suggests { ctx, builder -> suggestTargets(ctx.source.sender, builder) }
                    .executes { ctx -> sendUsage(ctx.source.sender); Command.SINGLE_SUCCESS }
                    .then(
                        Commands.argument("message", StringArgumentType.greedyString())
                            .suggests { _, builder -> EmojiSuggestions.suggest(builder) }
                            .executes { ctx ->
                                sendTell(
                                    ctx.source.sender,
                                    StringArgumentType.getString(ctx, "target"),
                                    StringArgumentType.getString(ctx, "message")
                                )
                                Command.SINGLE_SUCCESS
                            }
                    )
            )
            .build()

    fun replyNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("r")
            .requires { source -> tellEnabled() && canUse(source.sender) }
            .executes { ctx -> sendReplyUsage(ctx.source.sender); Command.SINGLE_SUCCESS }
            .then(
                Commands.argument("message", StringArgumentType.greedyString())
                    .suggests { _, builder -> EmojiSuggestions.suggest(builder) }
                    .executes { ctx ->
                        sendReply(ctx.source.sender, StringArgumentType.getString(ctx, "message"))
                        Command.SINGLE_SUCCESS
                    }
            )
            .build()

    private fun tellEnabled(): Boolean = SlateChat.instance.config.getBoolean("modules.tell.enable")

    // Short-circuits before ever touching SlateChat.tellConfig -- tellEnabled() is checked
    // first in every .requires{} above, so this never runs while the module (and its
    // lateinit config) is disabled/unloaded.
    private fun canUse(sender: Permissible): Boolean {
        if (!SlateChat.tellConfig.getBoolean("tell.restrict", false)) return true
        return sender.hasPermission(USE_PERMISSION)
    }

    private fun sendTell(sender: CommandSender, targetName: String, text: String) {
        if (sender !is Player) {
            sender.sendMessage(playersOnly())
            return
        }

        // Exact-name match against this server only (not the partial-match Bukkit.getPlayer
        // overload) -- a vanished player being reachable off a lucky partial match would defeat
        // the point of hiding them at all.
        val local = Bukkit.getPlayerExact(targetName)
        when {
            local != null && visibleTo(sender, local) -> {
                if (local.uniqueId == sender.uniqueId) {
                    sender.sendMessage(msg("self_tell_blocked"))
                    return
                }
                Bukkit.getAsyncScheduler().runNow(SlateChat.instance) {
                    Tell(sender, local, text).validate().prepare().send()
                }
            }
            // Known to be on this server but vanished-from-sender with no bypass -- stays
            // fully local, same target_offline wording a relay attempt would end up showing
            // anyway (Velocity would just re-discover them here and re-run this same vanish
            // check), so there's no reason to pay a network round trip for it.
            local != null ->
                sender.sendMessage(msg("target_offline"))
            // No exact-name match on this server at all -- try the network.
            else ->
                Bukkit.getAsyncScheduler().runNow(SlateChat.instance) {
                    TellRelay(sender, targetQuery = targetName, targetUuid = null, displayTargetName = targetName, text, notFoundMessageKey = "target_offline")
                        .validate().prepare().send()
                }
        }
    }

    private fun sendReply(sender: CommandSender, text: String) {
        if (sender !is Player) {
            sender.sendMessage(playersOnly())
            return
        }

        val partner = TellManager.replyTargetOf(sender)
        if (partner == null) {
            sender.sendMessage(msg("no_reply_target"))
            return
        }

        val local = Bukkit.getPlayer(partner.uuid)
        when {
            local != null && visibleTo(sender, local) ->
                Bukkit.getAsyncScheduler().runNow(SlateChat.instance) {
                    Tell(sender, local, text).validate().prepare().send()
                }
            // Known locally but vanished-from-sender with no bypass -- stays fully local,
            // preserving today's exact behavior (no_reply_target, not a relay attempt).
            local != null ->
                sender.sendMessage(msg("no_reply_target"))
            // Partner isn't on this server at all -- try the network, using TellManager's
            // cached uuid+name since there's no live Player to resolve a fresh query from.
            else ->
                Bukkit.getAsyncScheduler().runNow(SlateChat.instance) {
                    TellRelay(sender, targetQuery = "", targetUuid = partner.uuid, displayTargetName = partner.name, text, notFoundMessageKey = "no_reply_target")
                        .validate().prepare().send()
                }
        }
    }

    /** [Player.canSee] is the native Bukkit/Paper visibility check -- respects any vanish
     *  plugin's `hidePlayer(plugin, player)` calls regardless of which one is installed. */
    private fun visibleTo(sender: Player, target: Player): Boolean =
        sender.canSee(target) || sender.hasPermission(VANISH_BYPASS_PERMISSION)

    // Local-only, deliberately not extended to the network: suggesting a cross-server name would
    // mean either a live per-keystroke round trip to Velocity for every remote player's identity
    // + vanish state, or a locally cached copy of it going stale between updates -- both worse
    // than just not suggesting them. Typing the exact name still works fine (see TellRelay),
    // this only affects tab-complete.
    private fun suggestTargets(sender: CommandSender, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        if (sender !is Player) return builder.buildFuture()

        Bukkit.getOnlinePlayers().asSequence()
            .filter { it.uniqueId != sender.uniqueId }
            .filter { visibleTo(sender, it) }
            .map { it.name }
            .filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }

    private fun sendUsage(sender: CommandSender) = sender.sendMessage(msg("usage"))
    private fun sendReplyUsage(sender: CommandSender) = sender.sendMessage(msg("reply_usage"))

    private fun playersOnly() =
        Messages.resolve(SlateChat.instance.config, "players_only", "<red>Only players can execute this command!")

    // tell.yml nests everything under a top-level "tell:" key, same shape as a channels.yml
    // entry -- see the file itself.
    private fun msg(path: String) =
        Messages.resolve(SlateChat.tellConfig, "tell.$path", "<red>Missing message: $path")
}
