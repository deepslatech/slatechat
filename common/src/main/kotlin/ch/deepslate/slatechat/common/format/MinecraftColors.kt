package ch.deepslate.slatechat.common.format

import net.kyori.adventure.text.format.NamedTextColor

/**
 * The 16 standard Minecraft colors, sourced from Adventure's own `NamedTextColor` instead of
 * typed out by hand -- this used to be its own copy of the same 16 names in three different
 * places (ChatPermissionGate's tag-strip list, and MineDownConverter's isColor/convertColor),
 * each one a chance to drift out of sync if a name were ever fat-fingered in only one spot.
 */
object MinecraftColors {

    /**
     * Legacy-code order (0-9, a-f) -- Vanilla's own fixed convention, not something Adventure
     * exposes as an ordered list (`NamedTextColor.NAMES` is a set/index, not an array), so this
     * part still has to be spelled out once. Everything downstream (the name set, the legacy
     * character mapping) is derived from this rather than re-typed.
     */
    private val LEGACY_ORDER: List<NamedTextColor> = listOf(
        NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA,
        NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE, NamedTextColor.GOLD, NamedTextColor.GRAY,
        NamedTextColor.DARK_GRAY, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
        NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE
    )

    /** Every valid MiniMessage color name (lowercase), straight from Adventure's own index. */
    val NAMES: Set<String> = NamedTextColor.NAMES.keys()

    /** Legacy `&`/`§` color character ('0'-'9', 'a'-'f') -> the matching MiniMessage tag name. */
    val LEGACY_CODE_TO_TAG: Map<Char, String> = "0123456789abcdef".toList().zip(LEGACY_ORDER)
        .associate { (code, color) -> code to NamedTextColor.NAMES.keyOrThrow(color) }
}
