package ch.deepslate.slatechat.common.format

/**
 * Pure word filter/replace transform, extracted from Message.validate(). Never touches
 * SlateChat/Bukkit directly -- the caller reads SlateChat.filterConfig and passes the lists
 * in, then logs each returned Event itself (via Message.logFilter()). That split is what
 * makes this object testable in isolation.
 */
object ChatFilter {

    /** One filter action that was applied. `original` is the text as it was before *any*
     *  filtering happened this call, matching the previous inline logFilter() behavior. */
    data class Event(val reason: String, val detail: String, val original: String)

    data class Result(val text: String, val events: List<Event>)

    fun apply(text: String, blockedWords: List<String>, replacements: List<String>): Result {
        val original = text
        var message = text
        val events = mutableListOf<Event>()

        // Process blocked words (censor with asterisks)
        for (word in blockedWords) {
            if (message.contains(word, ignoreCase = true)) {
                message = message.replace(word, "*".repeat(word.length), true)
                events += Event("blocked", word, original)
            }
        }

        // Process replacements (replace word A with word B)
        for (entry in replacements) {
            val parts = entry.split("->", limit = 2)
            if (parts.size == 2) {
                val from = parts[0]
                val to = parts[1]
                if (message.contains(from, ignoreCase = true)) {
                    message = message.replace(from, to, true)
                    events += Event("replace", "$from->$to", original)
                }
            }
        }

        return Result(message, events)
    }
}
