package ch.deepslate.slatechat.common.format

import java.util.regex.Pattern

/**
 * Pure tag-stripping for the three permission-gated MiniMessage tag families. No permission
 * checking happens here -- callers (Message.prepare()) decide *whether* to call each strip
 * function, based on sender.hasPermission(...). Keeping the checks out of this object is what
 * makes it a plain String -> String transform, testable without a live Bukkit server.
 */
object ChatPermissionGate {

    private val HEX_PATTERN: Pattern = Pattern.compile("#([0-9a-fA-F]{6})")
    private val LEGACY_CODE_PATTERN: Pattern = Pattern.compile("[&§][0-9A-Fa-fK-Ok-oRr]")

    // Names of every MiniMessage tag that produces a *color* effect (solid colors, hex,
    // gradient/rainbow/shadow/transition/pride). Used to strip raw tags a player without
    // chat.colored types directly -- MineDownConverter already gates [text](args) syntax,
    // but a raw <red>/<gradient:...>/<shadow:...> tag used to sail straight through to
    // mm.deserialize() untouched. Formatting (bold/italic/...), click, hover, keybind,
    // insert, font, lang and head are NOT color tags and stay ungated here on purpose --
    // see https://deepslate.ch/docs/plugins/slatechat/permissions.
    //
    // "reset" rides along here too, even though it's not a color by itself: the channel
    // format and the player's message are one single MiniMessage string by the time this
    // gets parsed, so a bare <reset> in a player's message wipes out the channel's own
    // established color (e.g. <yellow>...<message>), not just whatever the player set --
    // that's exactly the color-manipulation chat.colored exists to prevent, so it gets the
    // same treatment: stripped, not left to reach the parser. Deleting the tag outright
    // (rather than just refusing to apply it) is what makes the surrounding color fall
    // through to the channel's own instead of resetting to Minecraft's absolute default.
    private val COLOR_TAG_NAMES = MinecraftColors.NAMES + listOf(
        "color", "colour", "c",
        "gradient", "rainbow",
        "shadow",
        "transition", "pride",
        "reset"
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
    // which -- like chat.reload/chat.update -- has no permissions: entry declared anywhere
    // in the paper module's manifest, so it defaults to OP-only until a permission plugin
    // grants it.
    private val ADVANCED_TAG_NAMES = listOf("selector", "sel", "score", "nbt", "data")
    private val MINIMESSAGE_ADVANCED_TAG_PATTERN: Pattern = Pattern.compile(
        "</?(?:" + ADVANCED_TAG_NAMES.joinToString("|") + ")(:[^<>]*)?>",
        Pattern.CASE_INSENSITIVE
    )

    // Its own pattern, separate from COLOR_TAG_NAMES/MINIMESSAGE_COLOR_TAG_PATTERN above --
    // that one's gated by chat.colored (a per-player permission), this one by
    // modules.chatformat.pride in config.yml (a per-server policy switch, see Message.kt/
    // Tell.kt's prepare()) -- independent axes, a player can have chat.colored on a server
    // that's still turned this off specifically.
    private val PRIDE_TAG_PATTERN: Pattern = Pattern.compile("</?pride(:[^<>]*)?>", Pattern.CASE_INSENSITIVE)

    /**
     * Removes raw legacy (&c), HEX (#RRGGBB), and raw MiniMessage color-family tags
     * (<red>, <gradient:...>, <rainbow>, <shadow:...>, <transition:...>, <pride:...>, <#fff>).
     * MineDownConverter already gates its own [text](args) syntax on chat.colored, but a
     * player typing MiniMessage tags directly used to skip that check entirely and sail
     * through to mm.deserialize() untouched -- this closes that gap.
     */
    fun stripColorTags(text: String): String {
        val withoutHex = HEX_PATTERN.matcher(text).replaceAll("")
        val withoutLegacy = LEGACY_CODE_PATTERN.matcher(withoutHex).replaceAll("")
        return MINIMESSAGE_COLOR_TAG_PATTERN.matcher(withoutLegacy).replaceAll("")
    }

    fun stripCosmeticTags(text: String): String =
        MINIMESSAGE_COSMETIC_TAG_PATTERN.matcher(text).replaceAll("")

    fun stripAdvancedTags(text: String): String =
        MINIMESSAGE_ADVANCED_TAG_PATTERN.matcher(text).replaceAll("")

    /** Removes just `<pride:...>`/`</pride>` -- unlike [stripColorTags] this only ever
     *  targets the one tag, leaving any text it wrapped (and every other color tag) alone. */
    fun stripPrideTag(text: String): String =
        PRIDE_TAG_PATTERN.matcher(text).replaceAll("")
}
