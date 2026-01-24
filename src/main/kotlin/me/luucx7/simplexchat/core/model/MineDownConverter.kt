package me.luucx7.simplexchat.core.model

import org.bukkit.entity.Player
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Converts MineDown-style syntax to MiniMessage format
 * Examples:
 * [Text](blue) - Blue text
 * [Text](https://example.com) - Clickable link with automatic hover showing URL
 * [Text](/command) - Clickable command with automatic hover showing command
 * [Text](https://example.com Custom Hover) - Link with custom hover (requires permission)
 * [Text](#fff-#000) - Gradient from #fff to #000
 * [Text](rainbow) - Rainbow effect
 * [Text](rainbow:20) - Rainbow with duration 20
 * [Text](blue underline https://example.com) - Combined formatting + link with auto hover
 */
object MineDownConverter {

    private val MINEDOWN_PATTERN: Pattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)")

    fun convert(text: String, player: Player? = null): String {
        val matcher = MINEDOWN_PATTERN.matcher(text)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val displayText = matcher.group(1)
            val args = matcher.group(2)
            val miniMessage = parseMineDown(displayText, args, player)
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(miniMessage))
        }

        matcher.appendTail(buffer)
        return buffer.toString()
    }

    private fun parseMineDown(text: String, args: String, player: Player? = null): String {
        // Parse arguments: can be color, formatting, link, hover, gradient, or rainbow
        val parts = args.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        
        val formatting = mutableListOf<String>()
        var color: String? = null
        var link: String? = null
        val hoverParts = mutableListOf<String>()
        var linkFound = false

        // Process parts - simple linear pass
        for (part in parts) {
            when {
                // If we already found a link, all remaining parts are hover text
                linkFound -> {
                    hoverParts.add(part)
                }
                // Gradient: #fff-#000 or #RRGGBB-#RRGGBB
                part.matches(Regex("#[0-9a-fA-F]{3,6}-#[0-9a-fA-F]{3,6}")) -> {
                    val colors = part.split("-")
                    formatting.add("gradient:${colors[0]}:${colors[1]}")
                }
                // Rainbow
                part.equals("rainbow", true) -> {
                    formatting.add("rainbow")
                }
                // Rainbow with duration: rainbow:20
                part.matches(Regex("(?i)rainbow:\\d+")) -> {
                    val duration = part.substringAfter(":").toIntOrNull() ?: 20
                    formatting.add("rainbow:$duration")
                }
                // URL
                part.startsWith("http://") || part.startsWith("https://") -> {
                    link = part
                    linkFound = true
                }
                // Command
                part.startsWith("/") -> {
                    link = part
                    linkFound = true
                }
                // Color codes
                isColor(part) -> {
                    color = convertColor(part)
                }
                // Formatting: bold, italic, underline, strikethrough, obfuscated
                isFormatting(part) -> {
                    formatting.add(part.lowercase())
                }
                // If not recognized and no link yet, treat as potential hover text
                else -> {
                    hoverParts.add(part)
                }
            }
        }

        // Handle hover text with permission check
        var hover: String? = null
        if (link != null) {
            // If there's a link/command
            if (hoverParts.isNotEmpty()) {
                // Custom hover text provided - check permission
                if (player?.hasPermission("chat.minedown.customhover") == true) {
                    hover = hoverParts.joinToString(" ")
                } else {
                    // No permission - use automatic hover (show the link/command)
                    hover = link
                }
            } else {
                // No custom hover - automatically show the link/command
                hover = link
            }
        } else if (hoverParts.isNotEmpty()) {
            // No link, just hover text (no permission needed for hover-only)
            hover = hoverParts.joinToString(" ")
        }

        // Build final MiniMessage string with proper nesting
        // Order: hover (outermost) -> click -> formatting -> color (innermost)
        var result = text
        
        // Apply color (innermost)
        if (color != null) {
            result = "<$color>$result<reset>"
        }
        
        // Apply formatting (bold, italic, etc.)
        formatting.forEach { fmt ->
            result = when (fmt) {
                "bold" -> "<bold>$result</bold>"
                "italic" -> "<italic>$result</italic>"
                "underline" -> "<underline>$result</underline>"
                "strikethrough" -> "<strikethrough>$result</strikethrough>"
                "obfuscated" -> "<obfuscated>$result</obfuscated>"
                else -> if (fmt.startsWith("gradient:")) {
                    val parts = fmt.substringAfter("gradient:").split(":")
                    if (parts.size == 2) {
                        "<gradient:${parts[0]}:${parts[1]}>$result</gradient>"
                    } else {
                        result
                    }
                } else if (fmt.startsWith("rainbow")) {
                    if (fmt.contains(":")) {
                        val duration = fmt.substringAfter(":").toIntOrNull() ?: 20
                        "<rainbow:$duration>$result</rainbow>"
                    } else {
                        "<rainbow>$result</rainbow>"
                    }
                } else {
                    result
                }
            }
        }
        
        // Apply click action
        if (link != null) {
            val clickAction = if (link.startsWith("/")) {
                "run_command"
            } else {
                "open_url"
            }
            result = "<click:$clickAction:$link>$result</click>"
        }
        
        // Apply hover (outermost) - MUST be outside click for MiniMessage
        if (hover != null) {
            val escapedHover = hover.trim().replace("'", "\\'")
            result = "<hover:show_text:'$escapedHover'>$result</hover>"
        }
        
        return result
    }

    private fun isColor(part: String): Boolean {
        return when (part.lowercase()) {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white" -> true
            else -> part.matches(Regex("#[0-9a-fA-F]{6}"))
        }
    }

    private fun convertColor(part: String): String {
        return when (part.lowercase()) {
            "black" -> "black"
            "dark_blue" -> "dark_blue"
            "dark_green" -> "dark_green"
            "dark_aqua" -> "dark_aqua"
            "dark_red" -> "dark_red"
            "dark_purple" -> "dark_purple"
            "gold" -> "gold"
            "gray" -> "gray"
            "dark_gray" -> "dark_gray"
            "blue" -> "blue"
            "green" -> "green"
            "aqua" -> "aqua"
            "red" -> "red"
            "light_purple" -> "light_purple"
            "yellow" -> "yellow"
            "white" -> "white"
            else -> if (part.matches(Regex("#[0-9a-fA-F]{6}"))) part else "white"
        }
    }

    private fun isFormatting(part: String): Boolean {
        return when (part.lowercase()) {
            "bold", "italic", "underline", "strikethrough", "obfuscated", "!bold" -> true
            else -> false
        }
    }
}
