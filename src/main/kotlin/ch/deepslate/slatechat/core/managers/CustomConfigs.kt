package ch.deepslate.slatechat.core.managers

import ch.deepslate.slatechat.SlateChat
import org.bukkit.Bukkit
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException

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
                plugin.logger.severe("Failed to load $name.yml")
                e.printStackTrace()
            } catch (e: InvalidConfigurationException) {
                plugin.logger.severe("Invalid YAML in $name.yml")
                e.printStackTrace()
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
                plugin.logger.severe("Failed to save $name.yml")
                e.printStackTrace()
            }
        }
    }
}
