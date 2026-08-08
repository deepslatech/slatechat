package ch.deepslate.slatechat.velocity.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.command.CommandSource
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

// Proxy-direct admin commands like reload run from the proxy's own console, not in-game --
// confirmed with the user rather than assumed. Gated on ConsoleCommandSource itself, not a
// permission: no LuckPerms grant should be able to bypass "console only".
class SlateCommand(private val onReload: () -> Unit) {

    // Named for consistency/documentation with every other hardcoded permission node in this
    // project -- not wired into an actual hasPermission() check, since the real gate below is
    // "console only", which no permission grant overrides.
    @Suppress("unused")
    private val permissionName = "chat.network.reload"

    fun node(): LiteralCommandNode<CommandSource> =
        LiteralArgumentBuilder.literal<CommandSource>("slatechat")
            .requires { it is ConsoleCommandSource }
            .then(
                LiteralArgumentBuilder.literal<CommandSource>("reload")
                    .executes { ctx ->
                        onReload()
                        ctx.source.sendMessage(Component.text("Network channels reloaded.", NamedTextColor.GREEN))
                        Command.SINGLE_SUCCESS
                    }
            )
            .build()
}
