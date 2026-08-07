package ch.deepslate.slatechat.core.model

import ch.deepslate.slatechat.SlateChat

/**
 * `debug: true` in config.yml gates a handful of diagnostic log lines around message delivery
 * ([Message], [MessageDelivery]) -- one place for the "is debug on" check instead of repeating
 * `config.getBoolean("debug", false)` at every call site. `message` is a lambda, not a plain
 * `String`, so building it (string concat, JSON serialization, ...) is skipped entirely when
 * debug logging is off.
 */
object DebugLog {
    inline fun log(message: () -> String) {
        if (SlateChat.instance.config.getBoolean("debug", false)) {
            SlateChat.instance.logger.info("[DEBUG] ${message()}")
        }
    }
}
