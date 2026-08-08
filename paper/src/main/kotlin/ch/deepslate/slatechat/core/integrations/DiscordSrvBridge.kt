package ch.deepslate.slatechat.core.integrations

import ch.deepslate.slatechat.common.integrations.SoftDependency

/**
 * Forwards a chat line to the DiscordSRV text channel mapped to a given SlateChat channel
 * name. Uses reflection (soft dependency) so this is a no-op when DiscordSRV isn't installed.
 */
object DiscordSrvBridge {
    fun send(content: String, channelName: String) {
        SoftDependency.tryOrElse(Unit) {
            val cls = Class.forName("github.scarsz.discordsrv.DiscordSRV")
            val getPlugin = cls.getMethod("getPlugin")
            val discordSrv = getPlugin.invoke(null)

            // Only send if this in-game channel is explicitly mapped in DiscordSRV's
            // config.yml "Channels" option, so channels left out of that map (e.g. "local")
            // are never forwarded instead of always falling back to the main channel.
            val destinationMethod = cls.getMethod(
                "getDestinationTextChannelForGameChannelName",
                String::class.java
            )
            val destinationChannel = destinationMethod.invoke(discordSrv, channelName) ?: return@tryOrElse

            val sendMethod = destinationChannel.javaClass.getMethod("sendMessage", CharSequence::class.java)
            val action = sendMethod.invoke(destinationChannel, content)
            val queueMethod = action.javaClass.getMethod("queue")
            queueMethod.invoke(action)
        }
    }
}
