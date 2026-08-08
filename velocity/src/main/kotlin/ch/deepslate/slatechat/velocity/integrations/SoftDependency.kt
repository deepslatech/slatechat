package ch.deepslate.slatechat.velocity.integrations

// Mirrors paper's ch.deepslate.slatechat.core.integrations.SoftDependency exactly -- that file
// has zero Bukkit imports already, so this is a straight duplicate rather than a rewrite. Kept
// as its own copy for now instead of relocating the paper one into common (bigger, separate
// change); a good candidate to consolidate later since nothing here is platform-specific.
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
