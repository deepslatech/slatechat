package ch.deepslate.slatechat.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class MinecraftDecorationsTest {

    @Test
    fun `all 5 standard decorations are present, using the real MiniMessage names`() {
        // "underlined", not "underline" -- MineDownConverter used to hand-type the wrong one.
        assertEquals(
            setOf("bold", "italic", "underlined", "strikethrough", "obfuscated"),
            MinecraftDecorations.NAMES
        )
    }

    @Test
    fun `legacy codes map to the correct decoration, in vanilla's own order`() {
        assertEquals("obfuscated", MinecraftDecorations.LEGACY_CODE_TO_TAG['k'])
        assertEquals("bold", MinecraftDecorations.LEGACY_CODE_TO_TAG['l'])
        assertEquals("strikethrough", MinecraftDecorations.LEGACY_CODE_TO_TAG['m'])
        assertEquals("underlined", MinecraftDecorations.LEGACY_CODE_TO_TAG['n'])
        assertEquals("italic", MinecraftDecorations.LEGACY_CODE_TO_TAG['o'])
        assertEquals(5, MinecraftDecorations.LEGACY_CODE_TO_TAG.size)
    }
}
