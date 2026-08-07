package ch.deepslate.slatechat.common.format

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import kotlin.test.Test
import kotlin.test.assertEquals

class MiniMessageRendererTest {

    // --- convertHexToMiniMessage ---

    @Test
    fun `bare hex in plain text converts to a MiniMessage tag`() {
        assertEquals(
            "Hello <#FF0000>world",
            MiniMessageRenderer.convertHexToMiniMessage("Hello #FF0000world")
        )
    }

    @Test
    fun `hex already inside a tag is left untouched (regression for the gradient-mangling bug)`() {
        // Historically this got rewritten into <gradient:<#FF0000>:<#0000FF>>, which
        // MiniMessage can't parse as a color argument -- the whole tag fell back to
        // literal text. See CHANGELOG.md [1.3.0].
        val input = "<gradient:#FF0000:#0000FF>text</gradient>"
        assertEquals(input, MiniMessageRenderer.convertHexToMiniMessage(input))
    }

    @Test
    fun `hex inside a shadow tag argument is left untouched`() {
        val input = "<shadow:#000000>text</shadow>"
        assertEquals(input, MiniMessageRenderer.convertHexToMiniMessage(input))
    }

    @Test
    fun `a bare closing-bracket character outside a tag is preserved, not silently dropped`() {
        // Regression: TAG_OR_TEXT_PATTERN used to exclude ">" from the plain-text alternative
        // too, so a lone ">" (e.g. a channel format's own "player>" separator) matched
        // *neither* alternative -- Matcher.find() just skipped over it, deleting it with no
        // trace. Channel formats like "<player><blue><bold>> <message>" were silently
        // corrupted into "<player><blue><bold> <message>" (the ">" gone) by this step alone.
        assertEquals(
            "<blue><bold>> plain",
            MiniMessageRenderer.convertHexToMiniMessage("<blue><bold>> plain")
        )
    }

    @Test
    fun `hex outside a tag still converts even when a tag with hex sits right next to it`() {
        assertEquals(
            "<gradient:#fff:#000>x</gradient> <#ABCDEF> plain",
            MiniMessageRenderer.convertHexToMiniMessage("<gradient:#fff:#000>x</gradient> #ABCDEF plain")
        )
    }

    // --- convertLegacyToMiniMessage ---

    @Test
    fun `legacy ampersand codes convert to MiniMessage tags`() {
        assertEquals("<red>hi<bold>!", MiniMessageRenderer.convertLegacyToMiniMessage("&chi&l!"))
    }

    @Test
    fun `legacy section-sign codes convert too`() {
        assertEquals("<green>ok", MiniMessageRenderer.convertLegacyToMiniMessage("§aok"))
    }

    @Test
    fun `every legacy decoration code converts to its real MiniMessage tag name`() {
        // Covers the MinecraftDecorations-driven codes this used to hand-type directly.
        assertEquals(
            "<obfuscated>k<bold>l<strikethrough>m<underlined>n<italic>o",
            MiniMessageRenderer.convertLegacyToMiniMessage("&kk&ll&mm&nn&oo")
        )
    }

    @Test
    fun `reset code converts`() {
        assertEquals("<yellow>a<reset>b", MiniMessageRenderer.convertLegacyToMiniMessage("&ea&rb"))
    }

    // --- convertBreaks ---

    @Test
    fun `literal backslash-n converts to a real newline`() {
        assertEquals("a\nb", MiniMessageRenderer.convertBreaks("a\\nb"))
    }

    @Test
    fun `br tag converts to a real newline, case-insensitive and self-closing`() {
        assertEquals("a\nb\nc", MiniMessageRenderer.convertBreaks("a<br>b<BR/>c"))
    }

    // --- neutralizeTextureTags ---

    private val NEUTRAL_OPEN =
        "<white><bold:false><italic:false><underlined:false><strikethrough:false><obfuscated:false>"
    private val NEUTRAL_CLOSE =
        "</obfuscated></strikethrough></underlined></italic></bold></white>"

    @Test
    fun `a bare head tag gets wrapped in a real, scoped neutral color`() {
        assertEquals(
            "$NEUTRAL_OPEN<head:Notch>$NEUTRAL_CLOSE",
            MiniMessageRenderer.neutralizeTextureTags("<head:Notch>")
        )
    }

    @Test
    fun `a sprite tag gets the same treatment as head`() {
        assertEquals(
            "$NEUTRAL_OPEN<sprite:blocks:block/stone>$NEUTRAL_CLOSE",
            MiniMessageRenderer.neutralizeTextureTags("<sprite:blocks:block/stone>")
        )
    }

    @Test
    fun `only the head tag is wrapped, color before and after it is left alone`() {
        // Mirrors channels.yml's own "%chat_color%<message>" shape: an open, unclosed color
        // tag that everything after it -- including a <head> the sender typed -- would
        // otherwise inherit.
        assertEquals(
            "<yellow>hi $NEUTRAL_OPEN<head:Notch>$NEUTRAL_CLOSE bye",
            MiniMessageRenderer.neutralizeTextureTags("<yellow>hi <head:Notch> bye")
        )
    }

    @Test
    fun `multiple texture tags are each wrapped independently`() {
        assertEquals(
            "$NEUTRAL_OPEN<head:A>$NEUTRAL_CLOSE and $NEUTRAL_OPEN<sprite:x:y>$NEUTRAL_CLOSE",
            MiniMessageRenderer.neutralizeTextureTags("<head:A> and <sprite:x:y>")
        )
    }

    @Test
    fun `text without a head or sprite tag is left untouched`() {
        assertEquals(
            "<yellow>hi bye",
            MiniMessageRenderer.neutralizeTextureTags("<yellow>hi bye")
        )
    }

    @Test
    fun `color after a head tag survives the full render pipeline`() {
        // Regression: <reset> doesn't actually scope to a paired </reset> in MiniMessage --
        // it resets forward from that point regardless (see CHANGELOG.md [1.4.0] and
        // neutralizeTextureTags's doc comment). Wrapping the head tag in <reset>...</reset>
        // used to leak the reset past its own closing tag, so "bye" fell back to Minecraft's
        // plain default instead of staying <yellow> like the rest of the message.
        val component = MiniMessageRenderer.render("<yellow>hi <head:Notch> bye")
        assertEquals(NamedTextColor.YELLOW, effectiveColorOf(component, "bye"))
    }

    // --- extractLinks / reinsertLinks ---

    @Test
    fun `a bare URL is pulled out and the placeholder round-trips back to a click+hover link`() {
        val extraction = MiniMessageRenderer.extractLinks("check https://example.com out")
        assertEquals(listOf("https://example.com"), extraction.urls)
        assertEquals(false, extraction.text.contains("https://"))

        assertEquals(
            "check <click:open_url:https://example.com><hover:show_text:'https://example.com'>" +
                "https://example.com</hover></click> out",
            MiniMessageRenderer.reinsertLinks(extraction.text, extraction.urls)
        )
    }

    @Test
    fun `trailing sentence punctuation is excluded from the link`() {
        val extraction = MiniMessageRenderer.extractLinks("go to https://example.com!")
        assertEquals(listOf("https://example.com"), extraction.urls)
        assertEquals(
            "go to <click:open_url:https://example.com><hover:show_text:'https://example.com'>" +
                "https://example.com</hover></click>!",
            MiniMessageRenderer.reinsertLinks(extraction.text, extraction.urls)
        )
    }

    @Test
    fun `a URL already inside an existing tag (e_g_ a MineDown-built link) is not extracted again`() {
        val input = "<click:open_url:https://example.com>Click</click>"
        val extraction = MiniMessageRenderer.extractLinks(input)
        assertEquals(emptyList(), extraction.urls)
        assertEquals(input, extraction.text)
    }

    @Test
    fun `multiple URLs are each extracted and reinserted independently`() {
        val extraction = MiniMessageRenderer.extractLinks("https://a.com and https://b.com")
        assertEquals(listOf("https://a.com", "https://b.com"), extraction.urls)
        val result = MiniMessageRenderer.reinsertLinks(extraction.text, extraction.urls)
        assertEquals(true, result.contains("open_url:https://a.com"))
        assertEquals(true, result.contains("open_url:https://b.com"))
    }

    // --- render (full pipeline) ---

    @Test
    fun `a bare URL typed straight into chat renders as a clickable link`() {
        val component = MiniMessageRenderer.render("check https://example.com out")
        assertEquals(true, component.toString().contains("https://example.com"))
        assertEquals(true, component.toString().lowercase().contains("open_url"))
    }

    @Test
    fun `a URL query string is not mangled by the hex or legacy-code passes in between`() {
        // Regression: extractLinks pulls the URL out entirely before convertHexToMiniMessage/
        // convertLegacyToMiniMessage run, specifically so a query string like "&background="
        // doesn't get its "&b" silently rewritten into a color tag mid-URL (and mid-link-text,
        // since the URL is also its own visible label).
        val component = MiniMessageRenderer.render("https://example.com?a=1&background=2")
        assertEquals(true, component.toString().contains("https://example.com?a=1&background=2"))
    }

    @Test
    fun `a URL fragment that looks like a hex color is not mangled either`() {
        val component = MiniMessageRenderer.render("https://example.com/page#a1b2c3")
        assertEquals(true, component.toString().contains("https://example.com/page#a1b2c3"))
    }

    @Test
    fun `render deserializes the fully converted string into a component`() {
        val component = MiniMessageRenderer.render("&cHello #FF0000")
        // Just confirm it actually parsed into styled content, not literal tag text --
        // detailed component-tree assertions belong to Adventure's own test suite.
        assertEquals(false, component.toString().contains("&c"))
    }

    @Test
    fun `with reset already stripped, a color set earlier in the string survives past where it used to be`() {
        // Companion to ChatPermissionGateTest's "strips reset" case -- that test proves the
        // tag is gone from the string; this proves *why* that matters: with nothing left to
        // interrupt the parse, "bye" inherits the <yellow> established for the whole string
        // (e.g. a channel format's own color) instead of resetting to Minecraft's absolute
        // default. ChatPermissionGate.stripColorTags("hi <reset>bye") already deletes the tag
        // before this ever runs -- simulated here directly since that's a separate step.
        val component = MiniMessageRenderer.render("<yellow>hi bye")
        assertEquals(NamedTextColor.YELLOW, effectiveColorOf(component, "bye"))
    }
}

/**
 * Walks the component tree for the (possibly merged-in, if no tag boundary split it out)
 * text containing [content], returning its resolved color -- its own, or inherited from the
 * nearest colored ancestor. Without a tag boundary (e.g. no <reset> in between anymore),
 * "hi bye" ends up as one single run rather than separate "hi"/"bye" children -- a substring
 * match handles both shapes.
 */
private fun effectiveColorOf(component: Component, content: String, inherited: TextColor? = null): TextColor? {
    val effective = component.color() ?: inherited
    if (component is TextComponent && component.content().contains(content)) return effective
    for (child in component.children()) {
        effectiveColorOf(child, content, effective)?.let { return it }
    }
    return null
}
