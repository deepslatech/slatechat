# Syntax Guide — MineDown & MiniMessage

SlateChat accepts **two** formatting syntaxes at the same time, in every channel format, chat message (with `chat.colored`), and plugin message: **MineDown** (`[Text](args)`) and **MiniMessage** (`<tag>text</tag>`). They're independent — use whichever fits, or mix both in the same string.

Legacy `&`/`§` codes and raw HEX (`#RRGGBB`) are also converted automatically wherever the plugin renders text (see [`Colors.md`](Colors.md) for the full legacy code table).

---

## MiniMessage

MiniMessage is Adventure's tag-based format: `<tag>content</tag>`. It's what channel `format:` strings in `channels.yml` are written in by default.

### Colors

```
<red>Red text</red>
<gold>Gold text</gold>
<#FF5733>Custom hex color</#FF5733>
```

Named colors match the standard 16 Minecraft colors (`<black>`, `<dark_blue>`, `<dark_green>`, `<dark_aqua>`, `<dark_red>`, `<dark_purple>`, `<gold>`, `<gray>`, `<dark_gray>`, `<blue>`, `<green>`, `<aqua>`, `<red>`, `<light_purple>`, `<yellow>`, `<white>`) — see [`Colors.md`](Colors.md).

### Formatting

```
<bold>Bold</bold>
<italic>Italic</italic>
<underlined>Underlined</underlined>
<strikethrough>Strikethrough</strikethrough>
<obfuscated>Obfuscated</obfuscated>
<reset>Reset formatting</reset>
```

Closing tags (`</bold>`) are optional if the rest of the string should stay formatted, but recommended for clarity.

### Gradients & Rainbow

```
<gradient:#FF0000:#0000FF>Gradient text</gradient>
<rainbow>Rainbow text</rainbow>
```

### Click & Hover events

```
<click:run_command:/spawn>Click to teleport</click>
<click:open_url:https://example.com>Visit our site</click>
<hover:show_text:'Hover message'>Hover over me</hover>
```

Events can be combined:

```
<hover:show_text:'Go to spawn'><click:run_command:/spawn><green>Click here</green></click></hover>
```

### Placeholders inside MiniMessage

PlaceholderAPI is resolved **before** MiniMessage parsing, so placeholders can safely sit inside or around tags:

```
<yellow>%luckperms_prefix% <player></yellow>
```

### Permission gating

Just like MineDown, color/gradient/rainbow tags typed by a player (e.g. via `/chatcolor`) are stripped if they lack `chat.colored`. MiniMessage baked directly into `channels.yml` formats is config, not player input, so it always renders regardless of permission.

---

## MineDown

MineDown uses the syntax: `[Display Text](arguments)`

- `Display Text`: The text that appears in chat
- `arguments`: Colors, formatting, links, hover text, etc.

### Automatic Hover Feature

**Important**: When you use a link or command, the hover text **automatically shows the hidden link/command**:

- `[Click here](/kill)` - Shows "Click here" and hovering displays "/kill"
- `[Visit](https://example.com)` - Shows "Visit" and hovering displays "https://example.com"

**Custom Hover**: To use custom hover text instead of the automatic one, you need the permission `chat.hover.bypass`:

- With permission: `[Click here](/kill Execute the kill command)` - Shows custom hover "Execute the kill command"
- Without permission: The custom text is ignored and the command "/kill" is shown in hover instead

### Usage Examples

#### 1. Clickable Link (URL)

```
[Visit our website](https://example.com)
```

- Shows: "Visit our website"
- Click: opens the website
- Hover: "https://example.com" (automatic)

#### 2. Clickable Command

```
[Click here](/help)
```

- Shows: "Click here"
- Click: executes `/help`
- Hover: "/help" (automatic)

#### 3. Command with Custom Hover (requires permission)

```
[View rules](/rules Click to see server rules)
```

- Shows: "View rules"
- Click: executes `/rules`
- Hover: "Click to see server rules" (if player has `chat.hover.bypass` permission)
- Hover: "/rules" (if player does NOT have permission - automatic fallback)

#### 4. Hover Only (no link)

```
[Information](Hover for more details)
```

- Shows: "Information"
- Hover: "Hover for more details" (no permission needed for hover-only)

#### 5. Colors

```
[Colored text](red)
[Hex color text](#FF5733)
```

**Requires `chat.colored`**: solid colors, gradients (#8), and rainbow (#9) are all silently ignored if the player doesn't have this permission — the text just falls back to whatever color already surrounds it. Links, hover, and plain formatting (bold/italic/underline/strikethrough/obfuscated) still work either way.

#### 6. Formatting

```
[Bold text](bold)
[Italic text](italic)
[Underlined text](underline)
[Strikethrough text](strikethrough)
[Obfuscated text](obfuscated)
```

#### 7. Combinations

```
[Admin](red bold)
[Click here](green underline /warp spawn Teleport to spawn)
[Website](aqua https://example.com Visit our website!)
```

#### 8. Gradients

```
[Colored text](#FF0000-#0000FF)
```

#### 9. Rainbow

```
[Rainbow](rainbow)
[Slow rainbow](rainbow:50)
```

#### 10. Escaping (show the raw syntax)

```
\[Click here](/kill)
```

- Shows: `[Click here](/kill)` as literal text — not clickable, not formatted
- A backslash right before the `[` tells the plugin to skip the conversion for that pair, which is handy when you want to show someone how a MineDown link/format is written instead of triggering it.

### Processing Order

1. First argument starting with `/` or `http(s)://` = link/command
2. Arguments after the link = hover text
3. Colors and formatting are applied to the display text
4. If there's no link, all unrecognized arguments become hover text

### Correct vs Incorrect Format

#### ❌ INCORRECT

```
[/kill](Click here)
```

This shows "/kill" as text and doesn't work properly.

#### ✅ CORRECT

```
[Click here](/kill)
[Click here](/kill Execute kill command)
```

### Usage in Chat

Simply type the MineDown syntax directly in chat:

```
Hello [everyone](yellow bold)! [Click here](/warp spawn Go to spawn)
```

Result:

- "Hello" = normal
- "everyone" = yellow and bold
- "Click here" = clickable (executes `/warp spawn`) with hover "Go to spawn"

### Important Notes

1. **Automatic Hover**: Links and commands always show hover text - either the link/command itself (automatic) or custom text with permission
2. **Permission Required**: `chat.hover.bypass` is needed to use custom hover text with links/commands
3. Hover always appears **outside** of click (order: hover > click > formatting > color)
4. Single quotes in hover text are automatically escaped
5. Multiple spaces in arguments are preserved in hover text
6. MineDown and MiniMessage tags can be freely combined in the same string — MineDown is converted to MiniMessage internally before final rendering

---

## Permissions

- `chat.colored` — Required for colors, HEX, gradients, and rainbow (both MineDown args and legacy/HEX codes typed by a player). Plain formatting (bold/italic/underline/strikethrough/obfuscated) and links/hover work regardless.
- `chat.hover.bypass` — Allows players to set custom hover text for MineDown links and commands (without this, the link/command itself is shown in hover).
