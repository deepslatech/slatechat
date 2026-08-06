package ch.deepslate.slatechat.core.integrations

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * Resolves <selector>/<score>/<nbt> components against a sender, via Paper's
 * `PaperComponents.resolveWithContext(component, sender, sender, bypassPermissions)`.
 * Without this, those components reach the client unresolved -- Adventure's own docs note
 * they're "rendered serverside" against "platform-defined context", and a plain
 * Audience#sendMessage() never supplies one, so the client just shows the literal pattern
 * (e.g. "@e[limit=5]") instead of resolved names.
 *
 * `bypassPermissions=true` because chat.advanced is SlateChat's own gate for these tags
 * (see ChatPermissionGate / Message.prepare()) -- we don't want Paper's internal op-level
 * selector check adding a second, conflicting gate that only true server operators could
 * ever pass, defeating the point of being able to grant chat.advanced to non-op staff via
 * a permissions plugin.
 *
 * Uses reflection because `PaperComponents` only exists from Paper 1.21.4 onward (our
 * compileOnly target is 1.21.1) -- on an older server this just no-ops and the component
 * goes out unresolved, same as before.
 */
object PaperSelectorResolver {
    fun resolve(input: Component, sender: Player): Component {
        return SoftDependency.tryOrElse(input) {
            val cls = Class.forName("io.papermc.paper.text.PaperComponents")
            val resolveWithContext = cls.getMethod(
                "resolveWithContext",
                Component::class.java,
                org.bukkit.command.CommandSender::class.java,
                org.bukkit.entity.Entity::class.java,
                Boolean::class.javaPrimitiveType
            )
            resolveWithContext.invoke(null, input, sender, sender, true) as Component
        }
    }
}
