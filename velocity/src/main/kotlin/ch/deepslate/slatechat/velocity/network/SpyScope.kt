package ch.deepslate.slatechat.velocity.network

/** A player's active network-wide socialspy subscription -- see [SocialSpySubscriptions].
 *  There's no "local" or "off" case here on purpose: those live entirely on the Paper side
 *  (the PDC-backed toggle, and simply having no entry in [SocialSpySubscriptions] at all). */
sealed interface SpyScope {
    data class Server(val name: String) : SpyScope
    data object All : SpyScope
}
