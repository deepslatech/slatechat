package ch.deepslate.slatechat.commands

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.managers.ChannelsManager
import ch.deepslate.slatechat.core.managers.ChatPlayerManager
import ch.deepslate.slatechat.core.managers.CommandRegistrar
import ch.deepslate.slatechat.core.managers.CustomConfigs
import ch.deepslate.slatechat.core.managers.FocusManager
import ch.deepslate.slatechat.core.managers.UpdateChecker
import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit

object SlateCmd {

    private val mm = MiniMessage.miniMessage()

    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("slatechat")
            .executes { ctx -> showVersion(ctx.source); Command.SINGLE_SUCCESS }
            .then(Commands.literal("reload").executes { ctx -> reload(ctx.source); Command.SINGLE_SUCCESS })
            .then(Commands.literal("update").executes { ctx -> update(ctx.source); Command.SINGLE_SUCCESS })
            .then(Commands.literal("wiki").executes { ctx -> wiki(ctx.source); Command.SINGLE_SUCCESS })
            .build()

    private fun showVersion(source: CommandSourceStack) {
        val version = SlateChat.instance.pluginMeta.version

        val message: Component = Component.text("SlateChat ", NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .append(Component.text("v$version by Deepslate Studios", NamedTextColor.AQUA))

        source.sender.sendMessage(message)
    }

    private fun reload(source: CommandSourceStack) {
        val sender = source.sender

        if (!sender.hasPermission("chat.reload")) {
            sender.sendMessage(noPermissionMessage())
            return
        }

        val plugin = SlateChat.instance

        // Disable current state
        ChannelsManager.disable()
        FocusManager.unload()

        // Reload base config
        plugin.reloadConfig()

        // Reload channel config
        SlateChat.cConfig = CustomConfigs.createCustomConfig("channels")

        // Reload modules configs/flags
        SlateChat.useFilter = false
        if (plugin.config.getBoolean("modules.focus")) {
            SlateChat.fConfig = CustomConfigs.createCustomConfig("focus")
        }
        if (plugin.config.getBoolean("modules.chatcolor")) {
            SlateChat.colorsConfig = CustomConfigs.createCustomConfig("color")
        }
        if (plugin.config.getBoolean("modules.filter")) {
            SlateChat.useFilter = true
            SlateChat.filterConfig = CustomConfigs.createCustomConfig("filter")
        }

        // Re-evaluate DiscordSRV presence
        SlateChat.isDiscordSRV = false
        if (plugin.config.getBoolean("modules.discordsrv")) {
            val discord = Bukkit.getPluginManager().getPlugin("DiscordSRV")
            if (discord != null && discord.isEnabled) {
                SlateChat.isDiscordSRV = true
            }
        }

        // Re-evaluate ItemsAdder presence
        SlateChat.isItemsAdder = false
        if (plugin.config.getBoolean("modules.itemsadder")) {
            val itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder")
            if (itemsAdder != null && itemsAdder.isEnabled) {
                SlateChat.isItemsAdder = true
            }
        }

        // Re-load channels and focus. Channel *data* is fully live after this -- a channel
        // whose command already existed at boot picks up its new format/permission/etc.
        // immediately (ChannelCommand looks the Channel up by name at execution time). A
        // channel whose command name is brand new since boot needs the reflection fallback
        // below -- Paper doesn't allow registering new Brigadier commands from a live
        // command's own execution, only during the COMMANDS lifecycle event itself. See
        // CommandRegistrar's doc comment.
        ChannelsManager.load()
        CommandRegistrar.registerNewChannelCommands(plugin)
        if (plugin.config.getBoolean("modules.focus")) {
            FocusManager.prepare()
        }

        // ChannelsManager.load() just rebuilt every Channel as a brand new object, but
        // already-connected players are still holding a reference to the old ones (with
        // stale format/settings) -- re-point each by name at the freshly loaded channel,
        // falling back to the default if it no longer exists.
        ChatPlayerManager.getAll().forEach { chatPlayer ->
            val currentName = chatPlayer.channel?.name
            chatPlayer.channel = currentName
                ?.let { ChannelsManager.getChannel(it) }
                ?: ChannelsManager.local
        }

        val msg = plugin.config.getString("plugin_reloaded") ?: "<green>Plugin reloaded."
        sender.sendMessage(mm.deserialize(msg))
    }

    private fun update(source: CommandSourceStack) {
        val sender = source.sender

        if (!sender.hasPermission("chat.update")) {
            sender.sendMessage(noPermissionMessage())
            return
        }

        UpdateChecker.checkNow(sender)
    }

    /** Points whoever ran the command to the plugin's documentation. The URL is config-
     *  editable (`wiki_url`), defaulting to deepslate.ch's own docs site. No permission
     *  required, purely informational. */
    private fun wiki(source: CommandSourceStack) {
        val url = SlateChat.instance.config.getString("wiki_url")
            ?: "https://deepslate.ch/docs/plugins/slatechat"
        val link = "<click:open_url:$url><hover:show_text:'Click to open'><white><underlined>$url</underlined></white></hover></click>"

        val message = (SlateChat.instance.config.getString("wiki_message") ?: "<green>Documentation: <url>")
            .replace("<url>", link)

        source.sender.sendMessage(mm.deserialize(message))
    }

    private fun noPermissionMessage() =
        mm.deserialize(
            SlateChat.instance.config.getString("no_permission") ?: "<red>You do not have permission!"
        )
}
