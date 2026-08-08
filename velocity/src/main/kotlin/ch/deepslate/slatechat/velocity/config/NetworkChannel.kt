package ch.deepslate.slatechat.velocity.config

import org.slf4j.Logger
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

private const val FILE_NAME = "network-channels.yml"

data class NetworkChannel(
    val key: String,
    val command: String,
    val format: String,
    val permission: String,
)

// Velocity has no saveResource()/plugin.dataFolder equivalent to Bukkit's -- this copies the
// jar's bundled default out to the plugin's data directory on first boot (same first-run idea as
// paper's CustomConfigs.createCustomConfig, just without any Bukkit types), then reads it back
// with raw SnakeYAML, already bundled by every Velocity proxy at runtime -- see
// velocity/build.gradle.kts for why that dependency is safe to compileOnly against.
fun loadNetworkChannels(dataDirectory: Path, logger: Logger): List<NetworkChannel> {
    val file = dataDirectory.resolve(FILE_NAME)
    if (!file.exists()) {
        Files.createDirectories(dataDirectory)
        val default = NetworkChannel::class.java.getResourceAsStream("/$FILE_NAME")
            ?: error("Bundled $FILE_NAME resource is missing from the jar")
        default.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
    }

    @Suppress("UNCHECKED_CAST")
    val root = file.inputStream().use { Yaml().load<Map<String, Map<String, Any>>>(it) }
        ?: emptyMap()

    return root.mapNotNull { (key, entry) ->
        val command = entry["command"] as? String
        val format = entry["format"] as? String
        val permission = entry["permission"] as? String
        if (command == null || format == null || permission == null) {
            logger.error("Skipping network channel '$key' in $FILE_NAME: missing 'command', 'format' or 'permission'")
            return@mapNotNull null
        }
        NetworkChannel(key.lowercase(), command, format, permission)
    }
}
