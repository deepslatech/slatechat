package ch.deepslate.slatechat.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatPermissionGateTest {

    // --- stripColorTags ---

    @Test
    fun `strips named color tags`() {
        assertEquals("hello world", ChatPermissionGate.stripColorTags("<red>hello <blue>world</blue>"))
    }

    @Test
    fun `strips gradient, rainbow, transition and pride tags`() {
        assertEquals(
            "abcd",
            ChatPermissionGate.stripColorTags(
                "<gradient:red:blue>a</gradient><rainbow>b</rainbow>" +
                    "<transition:red:blue>c</transition><pride:trans>d</pride>"
            )
        )
    }

    @Test
    fun `strips shadow tag`() {
        assertEquals("hi", ChatPermissionGate.stripColorTags("<shadow:#000000>hi</shadow>"))
    }

    @Test
    fun `strips the disable-shadow alias (regression for the missed gate fix)`() {
        // <!shadow> is Adventure's own alias for disabling the shadow, distinct from a
        // closing </shadow> tag -- the original regex only accounted for "/", not "!".
        assertEquals("hi", ChatPermissionGate.stripColorTags("<!shadow>hi"))
    }

    @Test
    fun `strips reset, so it doesn't wipe out the channel's own color`() {
        // <reset> is deleted outright rather than just refused, so nothing is left behind to
        // interrupt a color established earlier in the same parse (e.g. the channel format's
        // own <yellow>...<message>) -- see MiniMessageRendererTest for the color-inheritance
        // proof, this just confirms the tag itself is gone.
        assertEquals("hi bye", ChatPermissionGate.stripColorTags("hi <reset>bye"))
    }

    @Test
    fun `strips raw and legacy hex codes`() {
        // The bare-hex pass runs first and is unconditional -- it eats the "#FF0000" before
        // the <#hex> tag pattern ever gets a chance to match the surrounding brackets as a
        // unit, so empty "<>" brackets are left behind. Pre-existing behavior, not something
        // this refactor changes; locking it in here so a future change is deliberate.
        assertEquals("<>hi", ChatPermissionGate.stripColorTags("<#FF0000>hi"))
        assertEquals("hihi", ChatPermissionGate.stripColorTags("#FF0000hi&chi"))
    }

    @Test
    fun `leaves formatting, click, hover and keybind tags alone`() {
        val input = "<bold>b</bold><click:run_command:/x>c</click><hover:show_text:'y'>h</hover><key:key.jump>"
        assertEquals(input, ChatPermissionGate.stripColorTags(input))
    }

    // --- stripCosmeticTags ---

    @Test
    fun `strips key, insert, font, lang, head and sprite tags`() {
        val input = "<key:key.jump><insert:x>i</insert><font:uniform>f</font>" +
            "<lang:some.key><lang_or:k:fallback><head:uuid><sprite:blocks:block/stone>"
        assertEquals("if", ChatPermissionGate.stripCosmeticTags(input))
    }

    @Test
    fun `leaves color tags alone`() {
        assertEquals("<red>x</red>", ChatPermissionGate.stripCosmeticTags("<red>x</red>"))
    }

    // --- stripAdvancedTags ---

    @Test
    fun `strips selector, score and nbt tags including aliases`() {
        val input = "<selector:@s><sel:@e[limit=5]><score:name:obj><nbt:entity:'@s':Health><data:entity:'@s':Health>"
        assertEquals("", ChatPermissionGate.stripAdvancedTags(input))
    }

    @Test
    fun `leaves formatting tags alone`() {
        assertEquals("<bold>x</bold>", ChatPermissionGate.stripAdvancedTags("<bold>x</bold>"))
    }
}
