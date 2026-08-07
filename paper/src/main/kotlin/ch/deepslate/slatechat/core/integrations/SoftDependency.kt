package ch.deepslate.slatechat.core.integrations

/**
 * Reflection into another plugin's classes always needs the same shape: try it, and if the
 * plugin isn't installed or its API doesn't match what we expect, fall back quietly instead
 * of breaking chat for everyone. One place for that shape instead of repeating
 * try/catch(ClassNotFoundException)/catch(Exception) at every soft-dependency call site.
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
