package ch.deepslate.slatechat.core.model

import me.clip.placeholderapi.PlaceholderAPI
import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.api.Channel
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.permissions.Permissible
import java.util.regex.Pattern
import java.util.regex.Matcher
import ch.deepslate.slatechat.core.model.MineDownConverter

class Message(
    private val sender: Player,
    rawMessage: String,
    private val channel: Channel
) {

    private var message: String = rawMessage
    private val mm = MiniMessage.miniMessage()

    companion object {
        private val HEX_PATTERN: Pattern = Pattern.compile("#([0-9a-fA-F]{6})")
        private val LEGACY_CODE_PATTERN: Pattern = Pattern.compile("[&§][0-9A-Fa-fK-Ok-oRr]")

        // Splits a string into alternating <...> tag chunks and plain-text chunks, so
        // convertHexToMiniMessage can leave already-tagged content (e.g. a gradient's own
        // hex color arguments) untouched and only rewrite bare hex in plain text.
        private val TAG_OR_TEXT_PATTERN: Pattern = Pattern.compile("<[^<>]*>|[^<>]+")

        // Names of every MiniMessage tag that produces a *color* effect (solid colors, hex,
        // gradient/rainbow/shadow/transition/pride). Used to strip raw tags a player without
        // chat.colored types directly -- MineDownConverter already gates [text](args) syntax,
        // but a raw <red>/<gradient:...>/<shadow:...> tag used to sail straight through to
        // mm.deserialize() untouched. Formatting (bold/italic/...), click, hover, keybind,
        // insert, font, lang and head are NOT color tags and stay ungated here on purpose --
        // see docs/permissions.md.
        private val COLOR_TAG_NAMES = listOf(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white",
            "color", "colour", "c",
            "gradient", "rainbow",
            "shadow",
            "transition", "pride"
        )
        // "!" is matched alongside "/" so <!shadow> (Adventure's own alias for disabling the
        // shadow, distinct from a closing </shadow> tag) doesn't sail through ungated.
        private val MINIMESSAGE_COLOR_TAG_PATTERN: Pattern = Pattern.compile(
            "<[/!]?(?:" + COLOR_TAG_NAMES.joinToString("|") + ")(:[^<>]*)?>|<[/!]?#[0-9a-fA-F]{3,8}>",
            Pattern.CASE_INSENSITIVE
        )

        // Cosmetic tags: purely client-rendered convenience/decoration, no server-state
        // leak -- gated behind chat.cosmetic rather than chat.colored since they're not
        // color effects. Includes documented aliases (tr/translate, tr_or/translate_or).
        private val COSMETIC_TAG_NAMES = listOf(
            "key",
            "insert",
            "font",
            "lang", "lang_or", "tr", "tr_or", "translate", "translate_or",
            "head", "sprite"
        )
        private val MINIMESSAGE_COSMETIC_TAG_PATTERN: Pattern = Pattern.compile(
            "</?(?:" + COSMETIC_TAG_NAMES.joinToString("|") + ")(:[^<>]*)?>",
            Pattern.CASE_INSENSITIVE
        )

        // Advanced/data tags: resolved server-side and can expose entity/block/scoreboard
        // state to whoever reads the message (the same reason vanilla restricts raw JSON
        // text components like these to permission level 2+). Gated behind chat.advanced,
        // which -- like chat.reload/chat.update -- has no permissions: entry in plugin.yml,
        // so it defaults to OP-only until a permission plugin grants it.
        private val ADVANCED_TAG_NAMES = listOf("selector", "sel", "score", "nbt", "data")
        private val MINIMESSAGE_ADVANCED_TAG_PATTERN: Pattern = Pattern.compile(
            "</?(?:" + ADVANCED_TAG_NAMES.joinToString("|") + ")(:[^<>]*)?>",
            Pattern.CASE_INSENSITIVE
        )
    }

    /** Step 1 — validation (filter, caps, etc) */
    fun validate(): Message {
        if (!SlateChat.useFilter) return this

        val original = message

        // Process blocked words (censor with asterisks)
        val blockedWords = SlateChat.filterConfig.getStringList("blocked_words")
        for (word in blockedWords) {
            if (message.contains(word, ignoreCase = true)) {
                message = message.replace(word, "*".repeat(word.length), true)
                logFilter("blocked", word, original)
            }
        }
        
        // Process replacements (replace word A with word B)
        val replacements = SlateChat.filterConfig.getStringList("replace")
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
    fun prepare(): Message {
        if (!sender.hasPermission("chat.colored")) {
            message = stripColorCodes(message)
        }
        if (!sender.hasPermission("chat.cosmetic")) {
            message = MINIMESSAGE_COSMETIC_TAG_PATTERN.matcher(message).replaceAll("")
        }
        if (!sender.hasPermission("chat.advanced")) {
            message = MINIMESSAGE_ADVANCED_TAG_PATTERN.matcher(message).replaceAll("")
        }
        message = PlaceholderAPI.setPlaceholders(sender, message)
        return this
    }

    /**
     * Removes raw legacy (&c), HEX (#RRGGBB), and raw MiniMessage color-family tags
     * (<red>, <gradient:...>, <rainbow>, <shadow:...>, <transition:...>, <pride:...>, <#fff>)
     * typed by the player. MineDownConverter already gates its own [text](args) syntax on
     * chat.colored, but a player typing MiniMessage tags directly used to skip that check
     * entirely and sail through to mm.deserialize() untouched -- this closes that gap.
     */
    private fun stripColorCodes(text: String): String {
        val withoutHex = HEX_PATTERN.matcher(text).replaceAll("")
        val withoutLegacy = LEGACY_CODE_PATTERN.matcher(withoutHex).replaceAll("")
        return MINIMESSAGE_COLOR_TAG_PATTERN.matcher(withoutLegacy).replaceAll("")
    }

    /** Step 3 — send */
    fun send() {
        // Global region thread only for Bukkit actions (Paper's Folia-aware scheduler)
        Bukkit.getGlobalRegionScheduler().execute(SlateChat.instance) {
            // Strip styles (bold/italic/etc.) without wiping colors to preserve channel/chat colors
            val safeMessage = "<bold:false><italic:false><underlined:false><strikethrough:false><obfuscated:false>$message"
            val formatted = channel.format
                .replace("<player>", sender.name)
                .replace("<message>", safeMessage)
                .replace("<channel>", channel.name)

            // Apply PlaceholderAPI to the entire formatted string
            var withPlaceholders = PlaceholderAPI.setPlaceholders(sender, formatted)
            
            // Convert MineDown syntax to MiniMessage (pass player for permission checks)
            withPlaceholders = MineDownConverter.convert(withPlaceholders, sender)
            // Resolve ItemsAdder :emoji: font images, if ItemsAdder is installed
            withPlaceholders = applyItemsAdderFontImages(withPlaceholders)
            // Support line breaks using <br> or literal \n from configs
            withPlaceholders = convertBreaks(withPlaceholders)
            // Convert hex colors (#RRGGBB) to hex MiniMessage format <#RRGGBB>
            withPlaceholders = convertHexToMiniMessage(withPlaceholders)
            // Convert ALL legacy codes to MiniMessage BEFORE deserializing
            withPlaceholders = convertLegacyToMiniMessage(withPlaceholders)
            // Now deserialize with MiniMessage (all tags are MiniMessage format)
            val component = mm.deserialize(withPlaceholders)
            // <selector>/<score>/<nbt> come out of deserialize() as real components, but per
            // Adventure's own docs they're "rendered serverside" against "platform-defined
            // context" -- sent as-is via a plain Audience#sendMessage(), a client just falls
            // back to showing the literal pattern (e.g. "@e[limit=5]") instead of resolved
            // names, because nothing ever told the platform who/where to resolve against. This
            // resolves them against the message's sender before anything goes out.
            val resolvedComponent = resolveSelectorsAndScores(component)

            // Verbose diagnostic logging -- off by default, noisy (dumps the full component tree
            // as JSON for every message). Toggle with `debug: true` in config.yml.
            if (SlateChat.instance.config.getBoolean("debug", false)) {
                SlateChat.instance.logger.info(
                    "[DEBUG] withPlaceholders=$withPlaceholders | gson=" +
                        GsonComponentSerializer.gson().serialize(resolvedComponent)
                )
            }

            // Log to console if enabled
            if (SlateChat.instance.config.getBoolean("log_to_console", true)) {
                val consoleFormat = SlateChat.instance.config.getString("console_log")
                    ?: "[<channel>] <player>: <message>"

                val consoleMsg = consoleFormat
                    .replace("<channel>", channel.name)
                    .replace("<channelCmd>", channel.command)
                    .replace("<player>", sender.name)
                    .replace("<message>", message)

                // Apply PlaceholderAPI for console log
                val consoleFinal = PlaceholderAPI.setPlaceholders(sender, consoleMsg)

                // Same conversions the chat message itself gets, so &codes/#hex/raw MiniMessage
                // tags in console_log or the player's message render consistently here too,
                // instead of only legacy codes being stripped like before.
                val consoleMiniMessage = convertLegacyToMiniMessage(convertHexToMiniMessage(consoleFinal))
                val consoleComponent = mm.deserialize(consoleMiniMessage)
                // Hover/click never reach the console either way: PlainTextComponentSerializer
                // only extracts visible text, and ComponentLogger doesn't render interactive
                // metadata to a terminal -- so there's nothing extra to gate for those specifically.
                val plainConsoleMsg = PlainTextComponentSerializer.plainText().serialize(consoleComponent)

                if (SlateChat.instance.config.getBoolean("console_format_colors", false)) {
                    // Real ANSI colors in the console/log file (auto-disabled if the terminal
                    // doesn't support it, e.g. output redirected to a file).
                    SlateChat.instance.componentLogger.info(consoleComponent)
                } else {
                    SlateChat.instance.logger.info(plainConsoleMsg)
                }

                // Send to the DiscordSRV channel mapped to this SlateChat channel (if any) if enabled explicitly to avoid duplicates
                if (SlateChat.isDiscordSRV && SlateChat.instance.config.getBoolean("modules.discordsrv_send", false)) {
                    sendToDiscord(plainConsoleMsg, channel.name)
                }
            }

            if (channel.broadcast) {
                Bukkit.getOnlinePlayers().forEach { sendTo(it, resolvedComponent) }
            } else {
                val radius = channel.radius
                Bukkit.getOnlinePlayers()
                    .filter { it.world == sender.world }
                    .filter { it.location.distance(sender.location) <= radius }
                    .forEach { sendTo(it, resolvedComponent) }
            }
        }
    }
    
    /**
     * Resolves <selector>/<score>/<nbt> components against the message sender, via Paper's
     * `PaperComponents.resolveWithContext(component, sender, sender, bypassPermissions)`.
     * Without this, those components reach the client unresolved -- Adventure's own docs note
     * they're "rendered serverside" against "platform-defined context", and a plain
     * Audience#sendMessage() never supplies one, so the client just shows the literal pattern
     * (e.g. "@e[limit=5]") instead of resolved names.
     *
     * `bypassPermissions=true` because chat.advanced is SlateChat's own gate for these tags
     * (see prepare()) -- we don't want Paper's internal op-level selector check adding a second,
     * conflicting gate that only true server operators could ever pass, defeating the point of
     * being able to grant chat.advanced to non-op staff via a permissions plugin.
     *
     * Uses reflection because `PaperComponents` only exists from Paper 1.21.4 onward (our
     * compileOnly target is 1.21.1) -- on an older server this just no-ops and the component
     * goes out unresolved, same as before.
     */
    private fun resolveSelectorsAndScores(input: Component): Component {
        return try {
            val cls = Class.forName("io.papermc.paper.text.PaperComponents")
            val resolveWithContext = cls.getMethod(
                "resolveWithContext",
                Component::class.java,
                org.bukkit.command.CommandSender::class.java,
                org.bukkit.entity.Entity::class.java,
                Boolean::class.javaPrimitiveType
            )
            resolveWithContext.invoke(null, input, sender, sender, true) as Component
        } catch (_: ClassNotFoundException) {
            // Paper < 1.21.4 -- selector/score/nbt just won't resolve to live values there
            input
        } catch (_: Exception) {
            // Resolution failure (bad selector syntax, IOException, etc.) -- fall back to the
            // unresolved component rather than breaking the whole message
            input
        }
    }

    /**
     * Resolves ItemsAdder's `:emoji_name:` font-image shorthand into the actual rendered
     * text, respecting each font image's own permission (via ItemsAdder's own API).
     * Uses reflection (soft dependency) so this is a no-op when ItemsAdder isn't installed.
     */
    private fun applyItemsAdderFontImages(text: String): String {
        if (!SlateChat.isItemsAdder) return text

        return try {
            val cls = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper")
            val replaceFontImages = cls.getMethod(
                "replaceFontImages",
                Permissible::class.java,
                String::class.java
            )
            replaceFontImages.invoke(null, sender, text) as String
        } catch (_: ClassNotFoundException) {
            // ItemsAdder not present
            text
        } catch (_: Exception) {
            // Silently ignore ItemsAdder API failures to avoid impacting chat
            text
        }
    }

    /**
     * Converts bare #RRGGBB literals in plain text into <#RRGGBB> MiniMessage tags.
     * Deliberately skips anything already inside a <...> tag -- otherwise a color
     * argument that's already hex, like <gradient:#FF0000:#0000FF> or a MineDown-generated
     * gradient/shadow, gets rewritten into <gradient:<#FF0000>:<#0000FF>>, which isn't a
     * valid color argument and makes MiniMessage fall back to raw, unparsed text.
     */
    private fun convertHexToMiniMessage(text: String): String {
        val tagOrText = TAG_OR_TEXT_PATTERN.matcher(text)
        val result = StringBuilder()

        while (tagOrText.find()) {
            val chunk = tagOrText.group()
            if (chunk.startsWith("<")) {
                result.append(chunk)
                continue
            }

            val matcher = HEX_PATTERN.matcher(chunk)
            val buffer = StringBuffer()
            while (matcher.find()) {
                val hexCode = matcher.group(1)
                matcher.appendReplacement(buffer, Matcher.quoteReplacement("<#$hexCode>"))
            }
            matcher.appendTail(buffer)
            result.append(buffer)
        }

        return result.toString()
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
        if (!SlateChat.instance.config.getBoolean("log_to_console", true)) return
        Bukkit.getLogger().info("[SlateChat][$reason] ${sender.name}: '$original' (detail: $detail)")
    }

    private fun sendTo(player: Player, component: Component) {
        val audience: Audience = player
        audience.sendMessage(component)

        // Action bar shows how many players received the message (not the message itself)
        if (channel.actionbar && player.uniqueId == sender.uniqueId) {
            val count = if (channel.broadcast) {
                Bukkit.getOnlinePlayers()
                    .count { it.uniqueId != sender.uniqueId && !it.hasPermission("chat.count.bypass") }
            } else {
                Bukkit.getOnlinePlayers()
                    .count {
                        it.world == sender.world &&
                            it.location.distance(sender.location) <= channel.radius &&
                            it.uniqueId != sender.uniqueId &&
                            !it.hasPermission("chat.count.bypass")
                    }
            }

            val actionbarMsg = if (count == 0) {
                SlateChat.instance.config.getString("no_one") ?: ""
            } else {
                SlateChat.instance.config.getString("amount_readed")
                    ?.replace("<amount>", count.toString()) ?: ""
            }

            if (actionbarMsg.isNotBlank()) {
                audience.sendActionBar(mm.deserialize(actionbarMsg))
            }
        }
    }

    private fun sendToDiscord(content: String, channelName: String) {
        try {
            val cls = Class.forName("github.scarsz.discordsrv.DiscordSRV")
            val getPlugin = cls.getMethod("getPlugin")
            val discordSrv = getPlugin.invoke(null)

            // Only send if this in-game channel is explicitly mapped in DiscordSRV's
            // config.yml "Channels" option, so channels left out of that map (e.g. "local")
            // are never forwarded instead of always falling back to the main channel.
            val destinationMethod = cls.getMethod(
                "getDestinationTextChannelForGameChannelName",
                String::class.java
            )
            val destinationChannel = destinationMethod.invoke(discordSrv, channelName) ?: return

            val sendMethod = destinationChannel.javaClass.getMethod("sendMessage", CharSequence::class.java)
            val action = sendMethod.invoke(destinationChannel, content)
            val queueMethod = action.javaClass.getMethod("queue")
            queueMethod.invoke(action)
        } catch (_: ClassNotFoundException) {
            // DiscordSRV not present
        } catch (_: Exception) {
            // Silently ignore DiscordSRV send failures to avoid impacting chat
        }
    }

}
