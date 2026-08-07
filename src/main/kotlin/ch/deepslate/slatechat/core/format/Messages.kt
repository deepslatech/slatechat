package ch.deepslate.slatechat.core.format

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.FileConfiguration

/**
 * The "look up a MiniMessage string in config, falling back to a hardcoded default" shape
 * every command file (and a couple of listeners) used to reimplement on its own, each with
 * its own private `msg()` helper and its own `MiniMessage.miniMessage()` instance. One place
 * for it instead.
 *
 * Deliberately its own `MiniMessage` instance, not [MiniMessageRenderer]'s -- that one runs
 * the hex/legacy/URL transform pipeline built for player-authored chat text, which a plain
 * config-authored system message (permission denied, usage strings, ...) was never passed
 * through and shouldn't start being now.
 */
object Messages {

    private val mm = MiniMessage.miniMessage()

    /** Raw templated string from config, e.g. for callers that still need to substitute
     *  placeholders (`.replace("<name>", ...)`) before deserializing themselves. */
    fun raw(config: FileConfiguration, path: String, default: String): String =
        config.getString(path) ?: default

    /** [raw] already deserialized -- the common case, no placeholders involved. */
    fun resolve(config: FileConfiguration, path: String, default: String): Component =
        deserialize(raw(config, path, default))

    fun deserialize(text: String): Component = mm.deserialize(text)
}
