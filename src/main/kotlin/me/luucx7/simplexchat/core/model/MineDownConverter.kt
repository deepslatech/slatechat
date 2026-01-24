package me.luucx7.simplexchat.core.model

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Converts MineDown-style syntax to MiniMessage format
 * Examples:
 * [Text](blue) - Blue text
 * [Text](https://example.com) - Clickable link
 * [Text](/command) - Clickable command
 * [Text](https://example.com Hover Text) - Link with hover
 * [Text](#fff-#000) - Gradient from #fff to #000
 * [Text](rainbow) - Rainbow effect
 * [Text](rainbow:20) - Rainbow with duration 20
 * [Text](blue underline https://example.com Hover Text) - Combined formatting + link + hover
 */
object MineDownConverter {

    private val MINEDOWN_PATTERN: Pattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)")

    fun convert(text: String): String {
        var result = text
        val matcher = MINEDOWN_PATTERN.matcher(result)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val displayText = matcher.group(1)
            val args = matcher.group(2)
            val miniMessage = parseMineDown(displayText, args)
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(miniMessage))
        }

        matcher.appendTail(buffer)
        return buffer.toString()
    }

    private fun parseMineDown(text: String, args: String): String {
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
            }
        }

        val hover = if (hoverParts.isNotEmpty()) hoverParts.joinToString(" ") else null

        // Build final MiniMessage string with proper nesting
        var result = text
        
        // Apply click action (outermost if present)
        if (link != null) {
            val clickAction = if (link.startsWith("/")) {
                "run_command"
            } else {
                "open_url"
            }
            result = "<click:$clickAction:$link>$result</click>"
        }
        
        // Apply hover (inside click if present)
        if (hover != null) {
            result = "<hover:show_text:'${hover.trim()}'>$result</hover>"
            
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
        
        // Apply color (innermost)
        if (color != null) {
            result = "<$color>$result<reset>"
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
