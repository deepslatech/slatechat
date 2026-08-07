package ch.deepslate.slatechat.gui

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.commands.ChatColorCommand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

/**
 * Click handling for [ChatColorMenu] -- it's a picker, not a real inventory, so every
 * interaction with it is cancelled outright. A click on a color/reset slot runs through the
 * exact same [ChatColorCommand.apply]/[ChatColorCommand.applyClear] the typed command uses,
 * so permission checks and confirmation messages can't drift between the two entry points.
 * The book slot hands off to [HexColorMenu], or to a server's own configured command --
 * see [ChatColorMenu.HexPicker].
 */
class ChatColorMenuListener : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder
        if (holder !is ChatColorMenu.Holder) return
        event.isCancelled = true

        // Ignore clicks in the player's own inventory (still open below the menu) or outside
        // the window entirely -- only react to a click on one of the menu's own slots.
        if (event.clickedInventory != event.view.topInventory) return

        val player = event.whoClicked as? Player ?: return

        when (event.slot) {
            holder.resetSlot -> {
                ChatColorCommand.applyClear(player)
                player.closeInventory()
            }
            // bookSlot is null when hex_picker is "false", so this branch is unreachable
            // then -- event.slot (an Int) never equals a null Int?.
            holder.bookSlot -> when (holder.hexPicker) {
                ChatColorMenu.HexPicker.CUSTOM -> {
                    // Close first, unlike the built-in case below -- we don't know what the
                    // server's own command does, so don't leave our GUI lingering under it.
                    player.closeInventory()
                    val command = SlateChat.colorsConfig.getString("menu.hex_picker_command")
                    if (!command.isNullOrBlank()) player.performCommand(command.removePrefix("/"))
                }
                else -> HexColorMenu.open(player) // opening a new inventory replaces this one
            }
            // Anything else is either a color slot or one of the intentionally blank ones --
            // a click on blank space just does nothing instead of closing the menu on you.
            else -> holder.entries[event.slot]?.let { entry ->
                ChatColorCommand.apply(player, entry.code)
                player.closeInventory()
            }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is ChatColorMenu.Holder) event.isCancelled = true
    }
}
