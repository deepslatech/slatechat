package ch.deepslate.slatechat.core.integrations

import ch.deepslate.slatechat.SlateChat
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.permissions.Permissible

/**
 * Resolves ItemsAdder's `:emoji_name:` font-image shorthand into the actual rendered
 * text, respecting each font image's own permission (via ItemsAdder's own API).
 * Uses reflection (soft dependency) so this is a no-op when ItemsAdder isn't installed.
 */
object ItemsAdderBridge {
    fun applyFontImages(sender: Permissible, text: String): String {
        if (!SlateChat.isItemsAdder) return text

        return SoftDependency.tryOrElse(text) {
            val cls = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper")
            val replaceFontImages = cls.getMethod(
                "replaceFontImages",
                Permissible::class.java,
                String::class.java
            )
            replaceFontImages.invoke(null, sender, text) as String
        }
    }

    /**
     * Looks up an ItemsAdder custom item by its namespaced ID (`namespace:id`) and returns a
     * fresh copy of its `ItemStack` -- or null if ItemsAdder isn't installed, the ID is
     * blank, or nothing's registered under it (a typo in a server's own config, most likely).
     * Callers (e.g. [ch.deepslate.slatechat.gui.ChatColorMenu]) still get to apply their own
     * meta (name, dye color, lore) on top of whatever comes back, same as they would a plain
     * vanilla `ItemStack`.
     */
    fun resolveCustomItem(namespacedId: String?): ItemStack? {
        if (namespacedId.isNullOrBlank() || !SlateChat.isItemsAdder) return null

        return SoftDependency.tryOrElse(null) {
            val cls = Class.forName("dev.lone.itemsadder.api.CustomStack")
            val getInstance = cls.getMethod("getInstance", String::class.java)
            val customStack = getInstance.invoke(null, namespacedId) ?: return@tryOrElse null
            val getItemStack = cls.getMethod("getItemStack")
            (getItemStack.invoke(customStack) as ItemStack).clone()
        }
    }

    /** [resolveCustomItem] plus the "which ID to use" lookup every reskinnable GUI icon in
     *  this plugin needs -- one place for that shape instead of a copy per GUI. */
    fun resolveOrDefault(config: FileConfiguration, path: String, fallback: Material): ItemStack =
        resolveCustomItem(config.getString(path)) ?: ItemStack(fallback)
}
