package ch.deepslate.slatechat.gui

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.common.format.MinecraftColors
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.integrations.ItemsAdderBridge
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta

/**
 * The GUI `/chatcolor` opens with no argument -- the 16 standard Minecraft chat colors as
 * dyed leather chestplates (a real RGB match per color, not an approximated wool/dye
 * swatch), a reset button, and a book that opens [HexColorMenu] for anything outside those
 * 16. Fixed -- unlike channels.yml, there's deliberately no way for a server owner to
 * reorganize or replace this list; see the color.yml comment above `menu:` for why. Clicking
 * an entry runs the exact same [ch.deepslate.slatechat.commands.ChatColorCommand.apply]/
 * `applyClear` path typing the command would (see [ChatColorMenuListener]), so permission
 * checks and confirmation messages never drift between the two entry points.
 *
 * Item *skins* (not the color list/layout) are reskinnable via ItemsAdder, per role, through
 * `menu.itemsadder.*` in color.yml -- see [baseItem]/[ItemsAdderBridge.resolveCustomItem].
 */
object ChatColorMenu {

    data class Entry(val code: String, val name: String, val color: Color)

    /** Legacy code (from [MinecraftColors], the plugin's one source of truth for that
     *  mapping) per MiniMessage color name -- the reverse of `LEGACY_CODE_TO_TAG`, needed
     *  below to build an [Entry] starting from a color name instead of a code. */
    private val TAG_TO_CODE: Map<String, Char> =
        MinecraftColors.LEGACY_CODE_TO_TAG.entries.associate { (code, tag) -> tag to code }

    /** Builds one [Entry] from a MiniMessage color name. [Entry.color] comes straight from
     *  Adventure's own `NamedTextColor` RGB value, so e.g. RED and DARK_RED render as their
     *  actual distinct shades on the chestplate instead of both falling back to the same
     *  "RED" dye a wool/dye icon would have needed. */
    private fun entryFor(tag: String): Entry {
        val code = TAG_TO_CODE.getValue(tag)
        val label = tag.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        val rgb = NamedTextColor.NAMES.value(tag)?.value() ?: 0xFFFFFF
        // The item's *label* only -- a literal <black> name is unreadable against the
        // tooltip's own near-black background. The chestplate's actual dye color above still
        // renders true black either way, that part's fine to see.
        val textTag = if (tag == "black") "white" else tag
        return Entry("&$code", "<$textTag>$label", Color.fromRGB(rgb))
    }

    /** SlateChat's own 16 colors, in [MinecraftColors]' legacy-code order. */
    val DEFAULT_COLORS: List<Entry> = MinecraftColors.LEGACY_CODE_TO_TAG.values.map(::entryFor)

    // The 14 non-black/white colors pair up into 7 dark/normal shades of the same hue --
    // dark_purple/light_purple and gold/yellow are the two names that don't literally share
    // a "dark_"/plain prefix, everything else does. GRID_SLOTS' top row gets the dark half of
    // each pair, the bottom row the normal half, same column -- see GRID_SLOTS below for why
    // that lines them up visually.
    private val DARK_NORMAL_PAIRS = listOf(
        "dark_blue" to "blue",
        "dark_green" to "green",
        "dark_aqua" to "aqua",
        "dark_red" to "red",
        "dark_purple" to "light_purple",
        "gold" to "yellow",
        "dark_gray" to "gray"
    )
    private val GRID_COLORS: List<Entry> =
        DARK_NORMAL_PAIRS.map { (dark, _) -> entryFor(dark) } + DARK_NORMAL_PAIRS.map { (_, normal) -> entryFor(normal) }

    // Black and white are set apart from the other 14 instead of taking a grid slot each --
    // both are prone to disappearing (literal black text, or white-on-white icon/background
    // depending on client theme), so they get their own two clearly-separate slots.
    private val EDGE_COLORS: List<Entry> = listOf(entryFor("black"), entryFor("white"))

    // 5 rows (45 slots) -- Bukkit/Paper inventories can be any multiple of 9 from 9 to 54
    // (1-6 rows), passed straight through to Bukkit.createInventory's `size` below. Layout
    // (1-indexed rows/columns, as specced): row 1 blank; rows 2-3 hold the 14 non-black/white
    // colors, columns 2-8 each (column 1 and 9 left empty on both, a real border on either
    // side) -- row 2 is GRID_COLORS' first 7 (the dark half of each pair), row 3 its last 7
    // (the normal half), same column in both since row 3 = row 2 + 9, so e.g. dark_blue sits
    // directly above blue. Row 4 holds black and white in columns 2-3, then blank, then the
    // hex-picker book in column 7 and reset in column 8; row 5 blank. Every blank slot gets a
    // decorative filler pane (see [fillerItem]) -- not part of `entries`, so clicking one is
    // a no-op.
    private val GRID_SLOTS = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25
    )
    private val EDGE_SLOTS = listOf(28, 29)
    private const val BOOK_SLOT = 33
    private const val RESET_SLOT = 34
    private const val SIZE = 45

    /** `menu.hex_picker` in color.yml -- see [hexPicker] for how a string config value maps
     *  to this. [CUSTOM] exists specifically so a server can point the book at *their own*
     *  GUI's open command instead (a fully custom-textured menu of their own, say) rather
     *  than SlateChat's built-in anvil, which -- see [HexColorMenu]'s doc -- can't have a
     *  custom-font title the way the rest of this GUI can. */
    enum class HexPicker { BUILT_IN, DISABLED, CUSTOM }

    class Holder(
        val entries: Map<Int, Entry>,
        val resetSlot: Int,
        val bookSlot: Int?,
        val hexPicker: HexPicker
    ) : InventoryHolder {
        // Not a Kotlin `override val inventory` -- that generates its own getInventory()
        // accessor which clashes at the JVM level with InventoryHolder's own abstract
        // getInventory(), since both would compile to the exact same signature.
        private lateinit var inv: Inventory
        override fun getInventory(): Inventory = inv
        internal fun attach(inventory: Inventory) {
            inv = inventory
        }
    }

    /** Anything other than "false"/"custom" (including "true", unset, or a typo) falls back
     *  to the built-in picker -- same "never leave a slot dead over a config mistake"
     *  philosophy as [baseItem]'s ItemsAdder fallback. */
    private fun hexPicker(): HexPicker = when (SlateChat.colorsConfig.getString("menu.hex_picker")?.lowercase()) {
        "false" -> HexPicker.DISABLED
        "custom" -> HexPicker.CUSTOM
        else -> HexPicker.BUILT_IN
    }

    fun open(player: Player) {
        val hexPicker = hexPicker()
        val bookSlot = if (hexPicker == HexPicker.DISABLED) null else BOOK_SLOT

        val entries = (GRID_SLOTS.zip(GRID_COLORS) + EDGE_SLOTS.zip(EDGE_COLORS)).toMap()
        val holder = Holder(entries, RESET_SLOT, bookSlot, hexPicker)

        val title = Messages.deserialize(SlateChat.colorsConfig.getString("menu.title") ?: "<dark_gray>Choose your chat color")
        val inventory = Bukkit.createInventory(holder, SIZE, title)
        holder.attach(inventory)

        // Every blank slot in the layout gets a filler pane -- purely decorative, none of
        // them are in `entries`/resetSlot/bookSlot, so clicking one is still a no-op. When
        // hex_picker is "false", bookSlot is null, so BOOK_SLOT just keeps this filler
        // instead of getting overwritten below -- no separate "remove the book" step needed.
        for (slot in 0 until SIZE) inventory.setItem(slot, fillerItem())
        entries.forEach { (slot, entry) -> inventory.setItem(slot, colorItem(entry)) }
        inventory.setItem(RESET_SLOT, resetItem())
        bookSlot?.let { inventory.setItem(it, bookItem()) }

        player.openInventory(inventory)
    }

    /**
     * The vanilla item a role falls back to if `menu.itemsadder.<configKey>` isn't set,
     * ItemsAdder isn't installed, or the configured ID isn't registered -- [ItemsAdderBridge]
     * already returns null for all three of those, uniformly, so this never needs to know
     * which one happened.
     */
    private fun baseItem(configKey: String, fallback: Material): ItemStack =
        ItemsAdderBridge.resolveOrDefault(SlateChat.colorsConfig, "menu.itemsadder.$configKey", fallback)

    private fun colorItem(entry: Entry): ItemStack {
        val item = baseItem("color_item", Material.LEATHER_CHESTPLATE)
        item.editMeta { meta ->
            // Only if the (possibly custom) item actually supports it -- a custom
            // ItemsAdder skin built on leather armor keeps per-color differentiation,
            // anything else just shows the same skin for every color, name only. See
            // color.yml's own comment on menu.itemsadder.color_item.
            if (meta is LeatherArmorMeta) meta.setColor(entry.color)
            meta.displayName(Messages.deserialize(entry.name))
        }
        return item
    }

    private fun resetItem(): ItemStack {
        val item = baseItem("reset_item", Material.BARRIER)
        val name = SlateChat.colorsConfig.getString("menu.reset_name") ?: "<gray>Reset to default"
        item.editMeta { meta -> meta.displayName(Messages.deserialize(name)) }
        return item
    }

    private fun bookItem(): ItemStack {
        val item = baseItem("book_item", Material.BOOK)
        val name = SlateChat.colorsConfig.getString("menu.hex_button_name") ?: "<yellow>Custom hex color..."
        item.editMeta { meta -> meta.displayName(Messages.deserialize(name)) }
        return item
    }

    private fun fillerItem(): ItemStack {
        val item = baseItem("filler_item", Material.GRAY_STAINED_GLASS_PANE)
        item.editMeta { meta -> meta.displayName(Component.empty()) }
        return item
    }
}
