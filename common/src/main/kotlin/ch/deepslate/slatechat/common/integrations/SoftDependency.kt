package ch.deepslate.slatechat.common.integrations

/**
 * Reflection into another plugin's classes always needs the same shape: try it, and if the
 * plugin isn't installed or its API doesn't match what we expect, fall back quietly instead
 * of breaking chat for everyone. One place for that shape instead of repeating
 * try/catch(ClassNotFoundException)/catch(Exception) at every soft-dependency call site.
 *
 * Lives in `common`, not paper or velocity -- this had zero Bukkit/Velocity imports from the
 * start (both platforms' soft-dependency bridges are reflection-only, see DiscordSrvBridge/
 * ItemsAdderBridge/PaperSelectorResolver/EmojiSuggestions on paper and PapiProxyBridge on
 * velocity), so the two copies that existed before this were a straight duplicate, not a fork
 * with platform-specific behavior. Consolidated once the monorepo split actually needed both
 * platforms to share it, per the paper/velocity split's own deferred-cleanup note.
 */
object SoftDependency {
    inline fun <T> tryOrElse(default: T, block: () -> T): T {
        return try {
            block()
        } catch (_: ClassNotFoundException) {
            // The other plugin isn't installed
            default
        } catch (_: Exception) {
            // Present, but its API shape didn't match what we expected (version mismatch,
            // method renamed, etc.) -- fail soft rather than breaking chat for everyone
            default
        }
    }
}
