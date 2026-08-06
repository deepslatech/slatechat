package ch.deepslate.slatechat.core.format

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * The pure string->Component render pipeline shared by the chat message itself and the
 * console log: bare hex -> <#hex>, legacy &/§ codes -> MiniMessage tags, <br>/\n handling,
 * and the final MiniMessage deserialize. No Bukkit/Player dependency at all -- everything
 * here is a plain string transform, which is what makes it unit-testable without a live
 * server.
 */
object MiniMessageRenderer {

    private val mm = MiniMessage.miniMessage()

    private val HEX_PATTERN: Pattern = Pattern.compile("#([0-9a-fA-F]{6})")

    // Splits a string into alternating <...> tag chunks and plain-text chunks, so
    // convertHexToMiniMessage can leave already-tagged content (e.g. a gradient's own
    // hex color arguments) untouched and only rewrite bare hex in plain text.
    //
    // The text alternative excludes only "<", not ">" -- a bare ">" outside a tag (e.g. a
    // channel format's own "player>" separator) needs to still count as plain text. Excluding
    // both used to mean a lone ">" matched *neither* alternative, so Matcher.find() silently
    // skipped over it and it never made it into the rebuilt string -- deleted without a trace.
    private val TAG_OR_TEXT_PATTERN: Pattern = Pattern.compile("<[^<>]*>|[^<]+")

    /**
     * Converts bare #RRGGBB literals in plain text into <#RRGGBB> MiniMessage tags.
     * Deliberately skips anything already inside a <...> tag -- otherwise a color
     * argument that's already hex, like <gradient:#FF0000:#0000FF> or a MineDown-generated
     * gradient/shadow, gets rewritten into <gradient:<#FF0000>:<#0000FF>>, which isn't a
     * valid color argument and makes MiniMessage fall back to raw, unparsed text.
     */
    fun convertHexToMiniMessage(text: String): String {
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

    // Every legacy formatting character -> its MiniMessage tag: colors and decorations from
    // their respective single sources, plus "reset" ('r'), the one code that's neither --
    // Adventure tracks it separately from both NamedTextColor and TextDecoration.
    private val LEGACY_CODE_TO_TAG: Map<Char, String> =
        MinecraftColors.LEGACY_CODE_TO_TAG + MinecraftDecorations.LEGACY_CODE_TO_TAG + mapOf('r' to "reset")

    fun convertLegacyToMiniMessage(text: String): String {
        var result = text
        // Support both & and § legacy prefixes
        listOf('&', '§').forEach { prefix ->
            LEGACY_CODE_TO_TAG.forEach { (code, tag) -> result = result.replace("$prefix$code", "<$tag>") }
        }
        return result
    }

    fun convertBreaks(text: String): String {
        // Replace literal \n or HTML-style <br> with actual newline
        return text
            .replace("\\n", "\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
    }

    fun deserialize(text: String): Component = mm.deserialize(text)

    /**
     * Full pipeline for the chat message itself: <br>/\n, then hex, then legacy, then
     * deserialize. The console log reuses the individual steps directly instead (it
     * doesn't want <br> handling) -- see ConsoleWriter.
     */
    fun render(text: String): Component {
        var result = convertBreaks(text)
        result = convertHexToMiniMessage(result)
        result = convertLegacyToMiniMessage(result)
        return deserialize(result)
    }
}
