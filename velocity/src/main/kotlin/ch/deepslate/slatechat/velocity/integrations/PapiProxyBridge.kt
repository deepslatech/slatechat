package ch.deepslate.slatechat.velocity.integrations

import java.util.UUID
import java.util.concurrent.CompletableFuture

// Same reflection-only shape paper's DiscordSrvBridge/ItemsAdderBridge already use for a
// genuinely-optional plugin -- no direct import of PAPIProxyBridge's classes, everything through
// Class.forName/getMethod/invoke inside SoftDependency.tryOrElse, so a server without it
// installed never even attempts to link those classes.
//
// API per https://william278.net/project/papiproxybridge (net.william278:papiproxybridge:1.7.2,
// declared compileOnly in velocity/build.gradle.kts for documentation, not actually needed at
// compile time since this goes through reflection): PlaceholderAPI.createInstance() returns a
// singleton whose #formatPlaceholders(String, UUID) resolves real PlaceholderAPI placeholders
// against a player's UUID cross-server, returning a CompletableFuture<String> -- never verified
// against a live install yet, worth confirming the exact class/method names hold up the first
// time PAPIProxyBridge is actually installed and this path gets exercised for real.
object PapiProxyBridge {
    private const val CLASS_NAME = "net.william278.papiproxybridge.api.PlaceholderAPI"

    fun resolve(text: String, player: UUID): CompletableFuture<String> {
        return SoftDependency.tryOrElse(CompletableFuture.completedFuture(text)) {
            val cls = Class.forName(CLASS_NAME)
            val instance = cls.getMethod("createInstance").invoke(null)
            val formatPlaceholders = cls.getMethod("formatPlaceholders", String::class.java, UUID::class.java)

            @Suppress("UNCHECKED_CAST")
            val future = formatPlaceholders.invoke(instance, text, player) as CompletableFuture<String>

            // Resolution itself can still fail after the class/method lookups above already
            // succeeded (network hiccup talking to the backend, etc.) -- fall back to the
            // unresolved text rather than losing the message.
            future.exceptionally { text }
        }
    }
}
