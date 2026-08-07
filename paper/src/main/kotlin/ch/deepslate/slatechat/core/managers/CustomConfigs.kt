package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.SlateChat
import org.bukkit.Bukkit
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.util.logging.Level

object CustomConfigs {

    private val plugin get() = SlateChat.instance

    /* ================= LOAD ================= */

    fun createCustomConfig(name: String): FileConfiguration {
        val file = File(plugin.dataFolder, "$name.yml")

        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("$name.yml", false)
        }

        return YamlConfiguration().apply {
            try {
                load(file)
            } catch (e: IOException) {
                // .log(SEVERE, msg, e) over severe(msg) + e.printStackTrace() -- the latter
                // writes to stderr, disconnected from the plugin's own log/timestamps/prefix.
                plugin.logger.log(Level.SEVERE, "Failed to load $name.yml", e)
            } catch (e: InvalidConfigurationException) {
                plugin.logger.log(Level.SEVERE, "Invalid YAML in $name.yml", e)
            }
        }
    }

    /* ================= SAVE (ASYNC SAFE) ================= */

    fun saveCustomConfig(name: String, config: FileConfiguration) {
        val file = File(plugin.dataFolder, "$name.yml")

        // Paper's Folia-aware scheduler
        Bukkit.getAsyncScheduler().runNow(plugin) {
            try {
                config.save(file)
            } catch (e: IOException) {
                plugin.logger.log(Level.SEVERE, "Failed to save $name.yml", e)
            }
        }
    }
}
