package ch.deepslate.slatechat.core.integrations

import ch.deepslate.slatechat.SlateChat
import ch.deepslate.slatechat.common.integrations.SoftDependency
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture

/**
 * Suggests ItemsAdder `:emoji_name:` completions for the word currently being typed, matching
 * the same suggestions the vanilla chat box already offers via ItemsAdder's own client-side
 * chat completions -- those don't reach command arguments on their own, since commands use
 * Brigadier's separate tab-complete protocol.
 *
 * Extracted from ChannelCommand.kt (which used to be the only caller) so TellCommand can reuse
 * it verbatim instead of a second copy of the same reflection dance.
 */
object EmojiSuggestions {

    /**
     * The "message" argument is a single greedy string, so `builder.remaining` is everything
     * typed so far, not just the current word -- offset a fresh builder to the last space to
     * suggest only for the word actually being typed.
     */
    fun suggest(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        if (!SlateChat.isItemsAdder) return builder.buildFuture()

        val remaining = builder.remaining
        val lastSpace = remaining.lastIndexOf(' ')
        val current = remaining.substring(lastSpace + 1)
        if (!current.startsWith(":")) return builder.buildFuture()

        val prefix = current.removePrefix(":")
        val wordBuilder = builder.createOffset(builder.start + lastSpace + 1)
        names()
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .sorted()
            .forEach { wordBuilder.suggest(":$it:") }

        return wordBuilder.buildFuture()
    }

    /** Bare (non-namespaced) font image names registered in ItemsAdder, via reflection. */
    fun names(): Set<String> {
        return SoftDependency.tryOrElse(emptySet()) {
            val cls = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper")
            val getIds = cls.getMethod("getNamespacedIdsInRegistry")
            @Suppress("UNCHECKED_CAST")
            val namespacedIds = getIds.invoke(null) as Set<String>
            namespacedIds.mapNotNullTo(HashSet()) { id ->
                id.substringAfter(':', missingDelimiterValue = "").ifEmpty { null }
            }
        }
    }
}
