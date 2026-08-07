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

    // Splits a string into alternating <...> tag chunks and plain-text chunks, so the plain-
    // text-only steps below (hex, legacy codes, URL extraction) can leave already-tagged
    // content (e.g. a gradient's own hex color arguments) untouched.
    //
    // The text alternative excludes only "<", not ">" -- a bare ">" outside a tag (e.g. a
    // channel format's own "player>" separator) needs to still count as plain text. Excluding
    // both used to mean a lone ">" matched *neither* alternative, so Matcher.find() silently
    // skipped over it and it never made it into the rebuilt string -- deleted without a trace.
    private val TAG_OR_TEXT_PATTERN: Pattern = Pattern.compile("<[^<>]*>|[^<]+")

    /**
     * Runs `transform` over every plain-text chunk of `text`, passing existing `<...>` tags
     * through untouched -- shared by every step below that must never rewrite content inside
     * an existing tag's arguments (a hex color that's already a `<gradient:...>` argument, a
     * legacy-looking `&b` inside a URL's own query string, etc).
     */
    private fun mapPlainText(text: String, transform: (String) -> String): String {
        val tagOrText = TAG_OR_TEXT_PATTERN.matcher(text)
        val result = StringBuilder()
        while (tagOrText.find()) {
            val chunk = tagOrText.group()
            result.append(if (chunk.startsWith("<")) chunk else transform(chunk))
        }
        return result.toString()
    }

    /**
     * Converts bare #RRGGBB literals in plain text into <#RRGGBB> MiniMessage tags.
     * Deliberately skips anything already inside a <...> tag -- otherwise a color
     * argument that's already hex, like <gradient:#FF0000:#0000FF> or a MineDown-generated
     * gradient/shadow, gets rewritten into <gradient:<#FF0000>:<#0000FF>>, which isn't a
     * valid color argument and makes MiniMessage fall back to raw, unparsed text.
     */
    fun convertHexToMiniMessage(text: String): String = mapPlainText(text) { chunk ->
        val matcher = HEX_PATTERN.matcher(chunk)
        val buffer = StringBuffer()
        while (matcher.find()) {
            val hexCode = matcher.group(1)
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("<#$hexCode>"))
        }
        matcher.appendTail(buffer)
        buffer.toString()
    }

    // Every legacy formatting character -> its MiniMessage tag: colors and decorations from
    // their respective single sources, plus "reset" ('r'), the one code that's neither --
    // Adventure tracks it separately from both NamedTextColor and TextDecoration.
    private val LEGACY_CODE_TO_TAG: Map<Char, String> =
        MinecraftColors.LEGACY_CODE_TO_TAG + MinecraftDecorations.LEGACY_CODE_TO_TAG + mapOf('r' to "reset")

    /**
     * Same tag-skipping as [convertHexToMiniMessage], for the same reason: this used to be a
     * blind whole-string replace, so e.g. an auto-linked URL's own query string containing
     * "&background=..." got "&b" silently rewritten into a color tag mid-URL.
     */
    fun convertLegacyToMiniMessage(text: String): String = mapPlainText(text) { chunk ->
        var result = chunk
        // Support both & and § legacy prefixes
        listOf('&', '§').forEach { prefix ->
            LEGACY_CODE_TO_TAG.forEach { (code, tag) -> result = result.replace("$prefix$code", "<$tag>") }
        }
        result
    }

    fun convertBreaks(text: String): String {
        // Replace literal \n or HTML-style <br> with actual newline
        return text
            .replace("\\n", "\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
    }

    private val TEXTURE_TAG_PATTERN: Pattern = Pattern.compile("<(?:head|sprite)(:[^<>]*)?>")

    // Minecraft renders a font glyph by multiplying its texture by the current text color --
    // white (1,1,1) is the multiplicative identity, i.e. the actual "no tint" color, not
    // <reset>. Decorations are neutralized too (belt and suspenders): Message.send() already
    // forces them off for the player's own message text via its own <bold:false>... prefix,
    // but a <head>/<sprite> placed anywhere that prefix doesn't reach (e.g. a channel format
    // itself) would still want the same treatment.
    private const val NEUTRAL_OPEN =
        "<white><bold:false><italic:false><underlined:false><strikethrough:false><obfuscated:false>"
    private const val NEUTRAL_CLOSE =
        "</obfuscated></strikethrough></underlined></italic></bold></white>"

    /**
     * The `<head>`/`<sprite>` tags render a texture (a player skin's face, or a resource pack
     * sprite) -- like any other inline MiniMessage content, they inherit whatever color is
     * active where they sit (a channel's own color, or a sender's chosen chat color via the
     * `%chat_color%` placeholder, which channels.yml prefixes right before `<message>` as an
     * open, unclosed tag), which visibly tints the texture.
     *
     * `<reset>` is deliberately *not* used here, even though it reads like the obvious tool:
     * unlike a real color/decoration tag, MiniMessage's `<reset>` isn't scoped by its closing
     * tag -- it resets forward from that point regardless of a paired `</reset>` (see
     * CHANGELOG.md [1.4.0] on why a bare `<reset>` in a player's message had to be *stripped*
     * outright rather than just left to fail: it leaks past its own `</reset>` into whatever
     * comes next, exactly the "message after the head falls back to Minecraft's default" bug
     * this used to cause here). `<white>` and the decoration tags with an explicit `:false`
     * argument are real, scoped tags -- once wrapped, everything before and after keeps
     * whatever color/decoration was already active.
     */
    fun neutralizeTextureTags(text: String): String {
        val matcher = TEXTURE_TAG_PATTERN.matcher(text)
        val buffer = StringBuffer()
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("$NEUTRAL_OPEN${matcher.group()}$NEUTRAL_CLOSE"))
        }
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    fun deserialize(text: String): Component = mm.deserialize(text)

    private val BARE_URL_PATTERN: Pattern = Pattern.compile("(?i)\\bhttps?://[^\\s<>]+")
    // Trailing characters that are almost always sentence punctuation rather than part of the
    // URL itself -- "check this out: https://example.com!" shouldn't make the "!" clickable.
    private val URL_TRAILING_PUNCTUATION: Pattern = Pattern.compile("[.,!?;:'\")\\]}]+$")

    // A control character (SOH) no chat client can actually type, so it can't collide with
    // real message content -- used as a placeholder while a URL is out of the string (see
    // [extractLinks]/[reinsertLinks]).
    private const val LINK_MARKER = '\u0001'

    /** Result of [extractLinks]: `text` with every bare URL swapped out for an inert
     *  placeholder, and `urls` the URLs that were pulled out, in placeholder order. */
    data class LinkExtraction(val text: String, val urls: List<String>)

    /**
     * Finds bare `http(s)://` URLs a player just types straight into chat -- no need for the
     * `[text](url)` MineDown syntax -- and swaps each one out for a placeholder token,
     * remembering the real URL for [reinsertLinks] to put back at the very end of [render].
     *
     * Pulling the URL out entirely (rather than wrapping it in a `<click:open_url:...>` tag
     * immediately) is what protects it from [convertHexToMiniMessage]/
     * [convertLegacyToMiniMessage], which both run in between: a URL fragment can easily be 6
     * hex digits after a `#`, and a query string is *very* likely to have `&` immediately
     * followed by a color/decoration letter (`&background=`, `&color=`, `&ref=`, ...) -- both
     * of those would otherwise get silently mangled, including in the URL's own *visible*
     * text (it doubles as its own link label), not just the click target.
     *
     * Only considers plain text, same as the hex/legacy steps -- a URL already wrapped in an
     * explicit MineDown `[text](url)` link (already a real `<click:...>` tag by the time
     * [render] gets here) is left alone, not double-linked.
     */
    fun extractLinks(text: String): LinkExtraction {
        val urls = mutableListOf<String>()
        val replaced = mapPlainText(text) { chunk ->
            val matcher = BARE_URL_PATTERN.matcher(chunk)
            val buffer = StringBuffer()
            while (matcher.find()) {
                var url = matcher.group()
                val trailingMatcher = URL_TRAILING_PUNCTUATION.matcher(url)
                val trailing = if (trailingMatcher.find()) trailingMatcher.group() else ""
                if (trailing.isNotEmpty()) url = url.removeSuffix(trailing)

                urls += url
                val placeholder = "$LINK_MARKER${urls.size - 1}$LINK_MARKER"
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(placeholder + trailing))
            }
            matcher.appendTail(buffer)
            buffer.toString()
        }
        return LinkExtraction(replaced, urls)
    }

    /**
     * Puts URLs [extractLinks] pulled out back in, each as a real MiniMessage link: same
     * click(open_url) + hover(show the URL) shape [MineDownConverter]'s own `[text](url)`
     * syntax builds, and just as ungated -- MineDown links don't require any permission
     * today either (see `MineDownConverter.parseMineDown`).
     */
    fun reinsertLinks(text: String, urls: List<String>): String {
        var result = text
        urls.forEachIndexed { index, url ->
            val placeholder = "$LINK_MARKER$index$LINK_MARKER"
            val hover = url.replace("'", "\\'")
            result = result.replace(placeholder, "<click:open_url:$url><hover:show_text:'$hover'>$url</hover></click>")
        }
        return result
    }

    /**
     * Full pipeline for the chat message itself: <br>/\n, extract bare URLs, then hex, then
     * legacy, then neutralize <head>/<sprite> color, then put the URLs back as real links,
     * then deserialize. The console log reuses the individual steps directly instead (it
     * doesn't want <br> handling or clickable links) -- see ConsoleWriter.
     */
    fun render(text: String): Component {
        var result = convertBreaks(text)
        val links = extractLinks(result)
        result = convertHexToMiniMessage(links.text)
        result = convertLegacyToMiniMessage(result)
        result = neutralizeTextureTags(result)
        result = reinsertLinks(result, links.urls)
        return deserialize(result)
    }
}
