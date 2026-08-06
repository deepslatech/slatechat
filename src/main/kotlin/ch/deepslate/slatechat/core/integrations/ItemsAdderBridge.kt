package ch.deepslate.slatechat.core.integrations

import ch.deepslate.slatechat.SlateChat
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
}
