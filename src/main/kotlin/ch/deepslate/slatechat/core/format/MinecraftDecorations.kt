package ch.deepslate.slatechat.core.format

import net.kyori.adventure.text.format.TextDecoration

/**
 * The 5 Minecraft text decorations, sourced from Adventure's own `TextDecoration` instead of
 * typed out by hand -- same idea as MinecraftColors, and for the same reason: this is what
 * caught MineDownConverter's "underline" (never a real tag name or alias -- the actual one is
 * "underlined", `u` is the alias) silently producing unparsed literal text for anyone who used
 * `[Text](underline)`. One source instead of hand-copied lists that can drift out of sync.
 *
 * "reset" isn't a TextDecoration -- Adventure tracks it separately -- so it's handled on its
 * own wherever this is used (see ChatPermissionGate, MiniMessageRenderer).
 */
object MinecraftDecorations {

    /** Every valid MiniMessage decoration name (lowercase), straight from Adventure's own index. */
    val NAMES: Set<String> = TextDecoration.NAMES.keys()

    // Legacy-code order (k, l, m, n, o) -- Vanilla's own fixed convention, not something
    // Adventure exposes as an ordered list, so this part still has to be spelled out once.
    private val LEGACY_ORDER: List<TextDecoration> = listOf(
        TextDecoration.OBFUSCATED, TextDecoration.BOLD, TextDecoration.STRIKETHROUGH,
        TextDecoration.UNDERLINED, TextDecoration.ITALIC
    )

    /** Legacy `&`/`§` formatting character ('k','l','m','n','o') -> the matching MiniMessage tag name. */
    val LEGACY_CODE_TO_TAG: Map<Char, String> = "klmno".toList().zip(LEGACY_ORDER)
        .associate { (code, deco) -> code to TextDecoration.NAMES.keyOrThrow(deco) }
}
