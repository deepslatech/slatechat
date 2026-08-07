package ch.deepslate.slatechat.gui

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.commands.ChatColorCommand
import ch.deepslate.slatechat.core.format.Messages
import ch.deepslate.slatechat.core.integrations.ItemsAdderBridge
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.view.AnvilView

/**
 * Drives [HexColorMenu]'s live preview and click-to-apply. [onPrepare] fires on every
 * keystroke in the anvil's rename field (that's just how `PrepareAnvilEvent` works, not
 * something this registers separately for) -- it's the one place the result slot's item ever
 * gets set, so the preview and the "what actually gets applied" logic can never drift apart.
 *
 * Identifies "is this event about our menu" via [HexColorMenu.isOpenFor] (the player, not the
 * inventory's holder) -- see that class's doc for why.
 */
class HexColorMenuListener : Listener {

    @EventHandler
    fun onPrepare(event: PrepareAnvilEvent) {
        val player = event.view.player as? Player ?: return
        if (!HexColorMenu.isOpenFor(player)) return

        // This never runs a real repair -- there's nothing here to actually charge for.
        // event.view is statically an AnvilView here -- PrepareAnvilEvent narrows the return
        // type -- the non-deprecated replacement for AnvilInventory's own
        // renameText/repairCost.
        val view = event.view
        view.setRepairCost(0)

        val hex = HexColorMenu.parseHex(view.renameText)
        // Null result -- Vanilla just shows an empty output slot, i.e. nothing to click yet,
        // which is exactly the feedback "not a valid color" needs without a separate message.
        event.result = hex?.let(::previewItem)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!HexColorMenu.isOpenFor(player)) return
        event.isCancelled = true

        if (event.clickedInventory != event.view.topInventory || event.slot != RESULT_SLOT) return

        // Unlike PrepareAnvilEvent, InventoryClickEvent's getView() isn't statically narrowed
        // to AnvilView -- isOpenFor(player) above already confirms this is our anvil, so this
        // cast can't actually fail in practice.
        val view = event.view as? AnvilView ?: return
        val hex = HexColorMenu.parseHex(view.renameText) ?: return

        // Cancelling the click above stops Bukkit's own item transfer, but a *real* anvil
        // (openAnvil's whole point -- see HexColorMenu's doc) still runs its own underlying
        // consume-inputs/produce-result mechanic independently of that, and was handing the
        // player a real leather chestplate regardless. Clearing every slot first leaves
        // nothing for it to hand out.
        view.topInventory.clear()

        ChatColorCommand.apply(player, "#$hex")
        HexColorMenu.forget(player)
        player.closeInventory()
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (HexColorMenu.isOpenFor(player)) event.isCancelled = true
    }

    /** Covers every way the menu can close without a click on the result slot -- Escape,
     *  opening something else, disconnecting -- so [HexColorMenu.openFor] never leaks an
     *  entry for a player who isn't actually looking at it anymore. */
    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        HexColorMenu.forget(player)
    }

    /**
     * `menu.itemsadder.preview_item` in color.yml can swap this too -- but only the dye
     * color changes on every keystroke, never the base item/model itself (Bukkit has no way
     * to swap a *custom* model's texture per-color the way `LeatherArmorMeta.setColor` does
     * for a vanilla one). If the configured item's meta isn't `LeatherArmorMeta` under the
     * hood, this GUI stops being a *color* preview at all -- every hex still shows the same
     * skin, only the name/lore differ. A custom model built on leather armor is the only way
     * to keep the live-recolor working.
     */
    private fun previewItem(hex: String): ItemStack {
        val item = ItemsAdderBridge.resolveOrDefault(SlateChat.colorsConfig, "menu.itemsadder.preview_item", Material.LEATHER_CHESTPLATE)
        item.editMeta { meta ->
            if (meta is LeatherArmorMeta) meta.setColor(Color.fromRGB(Integer.parseInt(hex, 16)))
            meta.displayName(Messages.deserialize("<#$hex>#${hex.uppercase()}"))
        }
        return item
    }

    companion object {
        private const val RESULT_SLOT = 2
    }
}
