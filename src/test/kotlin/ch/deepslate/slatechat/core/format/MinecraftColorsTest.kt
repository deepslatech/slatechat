package ch.deepslate.slatechat.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class MinecraftColorsTest {

    @Test
    fun `all 16 standard colors are present`() {
        assertEquals(16, MinecraftColors.NAMES.size)
        assertEquals(
            setOf(
                "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
                "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
                "yellow", "white"
            ),
            MinecraftColors.NAMES
        )
    }

    @Test
    fun `legacy codes map to the correct vanilla color, in vanilla's own order`() {
        // Spot-checks against Vanilla's fixed 0-9/a-f convention, not just "16 entries exist".
        assertEquals("black", MinecraftColors.LEGACY_CODE_TO_TAG['0'])
        assertEquals("dark_purple", MinecraftColors.LEGACY_CODE_TO_TAG['5'])
        assertEquals("green", MinecraftColors.LEGACY_CODE_TO_TAG['a'])
        assertEquals("red", MinecraftColors.LEGACY_CODE_TO_TAG['c'])
        assertEquals("white", MinecraftColors.LEGACY_CODE_TO_TAG['f'])
        assertEquals(16, MinecraftColors.LEGACY_CODE_TO_TAG.size)
    }
}
