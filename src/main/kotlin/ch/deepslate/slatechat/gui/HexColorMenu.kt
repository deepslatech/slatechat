package ch.deepslate.slatechat.gui

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.integrations.ItemsAdderBridge
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * `/chatcolor`'s advanced hex picker, reachable via the book in [ChatColorMenu]. Repurposes
 * an anvil's rename text field as a live hex input: the player types a color into the name
 * box, and [HexColorMenuListener]'s `PrepareAnvilEvent` handling recolors a leather
 * chestplate preview in the result slot to match on every keystroke. Clicking that preview
 * applies the color through the exact same
 * [ch.deepslate.slatechat.commands.ChatColorCommand.apply] path [ChatColorMenu] uses.
 *
 * Which player has this open is tracked here by UUID rather than via a custom
 * `InventoryHolder` (the approach [ChatColorMenu] itself uses) -- anvils are one of Bukkit's
 * "special" inventory types with their own NMS-side construction path, and a custom holder
 * isn't reliably preserved through it the way it is for a plain chest-sized inventory. A
 * player can only ever have one top inventory open at a time, so "is this player's UUID in
 * the set" is exactly as precise an identity check, without depending on that.
 */
object HexColorMenu {

    /** Optional leading '#', then exactly 6 hex digits -- same shape /chatcolor's own RGB
     *  input already accepts, just without requiring the '#' since the anvil field's easy to
     *  type either way. */
    val HEX_INPUT: Pattern = Pattern.compile("#?([0-9a-fA-F]{6})")

    private val openFor: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun isOpenFor(player: Player): Boolean = player.uniqueId in openFor

    /** Called by [HexColorMenuListener] once this player's menu closes (confirmed, cancelled,
     *  or just clicked away from) -- keeps [openFor] from growing forever. */
    fun forget(player: Player) {
        openFor -= player.uniqueId
    }

    /**
     * `openAnvil(location = null, force = true)` -- not `Bukkit.createInventory(null,
     * InventoryType.ANVIL, title)` + `openInventory()`, which is what this used to do. That
     * built a real client-visible anvil window, but not the server-side container plumbing
     * behind it that actually recomputes a result (and fires [PrepareAnvilEvent]) when the
     * rename text changes -- so nothing this class or [HexColorMenuListener] does ever ran.
     * `openAnvil` is Paper's own way to get a fully virtual (unbound to any real block) anvil
     * *with* that plumbing intact. Trade-off: it has no title parameter, so the custom
     * `menu.hex_title` from color.yml gets applied after the fact via `setTitle` instead
     * (legacy-string only, no click/hover -- a title bar can't show those anyway).
     */
    fun open(player: Player) {
        val view = player.openAnvil(null, true) ?: return
        openFor += player.uniqueId

        val title = SlateChat.colorsConfig.getString("menu.hex_title") ?: "<dark_gray>Type a hex color"
        view.setTitle(LegacyComponentSerializer.legacySection().serialize(Messages.deserialize(title)))

        view.topInventory.setItem(0, canvasItem())
    }

    /** Parses [rawText] (the anvil's current rename field) into a bare 6-digit hex string
     *  (no '#'), or null if it isn't a complete valid one yet. Shared by
     *  [HexColorMenuListener]'s live preview and its click-to-apply handling, so they can
     *  never read the typed text two different ways. */
    fun parseHex(rawText: String?): String? {
        val text = rawText?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val matcher = HEX_INPUT.matcher(text)
        return if (matcher.matches()) matcher.group(1) else null
    }

    /**
     * The static item shown in the anvil's left slot -- purely there to make the rename
     * field available; unlike the result-slot preview, this one never changes as the player
     * types.
     *
     * Deliberately has no display name. An anvil's rename box starts pre-filled with
     * whatever name the item already has (that's the whole point of "rename" -- editing an
     * existing name), so giving this a name meant the box never started blank: the prompt
     * text was already sitting in it, and anything the player typed landed in the middle of
     * (or after) that instead of matching [HEX_INPUT] on its own. The prompt goes in the
     * lore instead, which the rename box never reads from.
     *
     * `menu.itemsadder.canvas_item` in color.yml can swap the base item, same as
     * [ChatColorMenu]'s own icons -- but see [HexColorMenuListener.previewItem]'s doc before
     * using that: this GUI's whole point is the *live recolor*, which only keeps working if
     * the custom item is itself leather armor under the hood.
     */
    private fun canvasItem(): ItemStack {
        val item = ItemsAdderBridge.resolveOrDefault(SlateChat.colorsConfig, "menu.itemsadder.canvas_item", Material.LEATHER_CHESTPLATE)
        item.editMeta { meta ->
            if (meta is LeatherArmorMeta) meta.setColor(Color.WHITE)
            val prompt = SlateChat.colorsConfig.getString("menu.hex_prompt") ?: "<gray>Type a hex code, e.g. #4DC3FF"
            meta.lore(listOf(Messages.deserialize(prompt)))
        }
        return item
    }
}
