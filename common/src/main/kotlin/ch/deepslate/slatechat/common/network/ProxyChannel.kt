package ch.deepslate.slatechat.common.network

/**
 * The one plugin-messaging channel SlateChat's Paper and Velocity halves talk over. Every
 * feature built on top of the transport (tell relay, socialspy, vanish sync, ...) shares this
 * single channel and picks it apart by [NetworkMessage.subchannel] instead of registering a
 * channel of its own -- Bukkit's Messenger and Velocity's ChannelRegistrar both track registered
 * channels per-plugin, and one registration is simpler to install/uninstall/reload than N.
 *
 * Namespaced ("slatechat:main"), not the legacy unnamespaced form BungeeCord's own "BungeeCord"
 * channel used -- the modern convention since 1.13, and what both Messenger and
 * MinecraftChannelIdentifier expect.
 */
object ProxyChannel {
    const val ID = "slatechat:main"
}
