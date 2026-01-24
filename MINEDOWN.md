# MineDown Syntax Guide

## Basic Format

MineDown uses the syntax: `[Display Text](arguments)`

- `Display Text`: The text that appears in chat
- `arguments`: Colors, formatting, links, hover text, etc.

## Automatic Hover Feature

**Important**: When you use a link or command, the hover text **automatically shows the hidden link/command**:

- `[Click here](/kill)` - Shows "Click here" and hovering displays "/kill"
- `[Visit](https://example.com)` - Shows "Visit" and hovering displays "https://example.com"

**Custom Hover**: To use custom hover text instead of the automatic one, you need the permission `chat.minedown.customhover`:

- With permission: `[Click here](/kill Execute the kill command)` - Shows custom hover "Execute the kill command"
- Without permission: The custom text is ignored and the command "/kill" is shown in hover instead

## Usage Examples

### 1. Clickable Link (URL)

```
[Visit our website](https://example.com)
```

- Shows: "Visit our website"
- Click: opens the website
- Hover: "https://example.com" (automatic)

### 2. Clickable Command

```
[Click here](/help)
```

- Shows: "Click here"
- Click: executes `/help`
- Hover: "/help" (automatic)

### 3. Command with Custom Hover (requires permission)

```
[View rules](/rules Click to see server rules)
```

- Shows: "View rules"
- Click: executes `/rules`
- Hover: "Click to see server rules" (if player has `chat.minedown.customhover` permission)
- Hover: "/rules" (if player does NOT have permission - automatic fallback)

### 4. Hover Only (no link)

```
[Information](Hover for more details)
```

- Shows: "Information"
- Hover: "Hover for more details" (no permission needed for hover-only)

### 5. Colors

```
[Colored text](red)
[Hex color text](#FF5733)
```

### 6. Formatting

```
[Bold text](bold)
[Italic text](italic)
[Underlined text](underline)
[Strikethrough text](strikethrough)
[Obfuscated text](obfuscated)
```

### 7. Combinations

```
[Admin](red bold)
[Click here](green underline /warp spawn Teleport to spawn)
[Website](aqua https://example.com Visit our website!)
```

### 8. Gradients

```
[Colored text](#FF0000-#0000FF)
```

### 9. Rainbow

```
[Rainbow](rainbow)
[Slow rainbow](rainbow:50)
```

## Processing Order

1. First argument starting with `/` or `http(s)://` = link/command
2. Arguments after the link = hover text
3. Colors and formatting are applied to the display text
4. If there's no link, all unrecognized arguments become hover text

## Correct vs Incorrect Format

### ❌ INCORRECT

```
[/kill](Click here)
```

This shows "/kill" as text and doesn't work properly.

### ✅ CORRECT

```
[Click here](/kill)
[Click here](/kill Execute kill command)
```

## Usage in Chat

Simply type the MineDown syntax directly in chat:

```
Hello [everyone](yellow bold)! [Click here](/warp spawn Go to spawn)
```

Result:

- "Hello" = normal
- "everyone" = yellow and bold
- "Click here" = clickable (executes `/warp spawn`) with hover "Go to spawn"

## Important Notes

1. **Automatic Hover**: Links and commands always show hover text - either the link/command itself (automatic) or custom text with permission
2. **Permission Required**: `chat.minedown.customhover` is needed to use custom hover text with links/commands
3. Hover always appears **outside** of click (order: hover > click > formatting > color)
4. Single quotes in hover text are automatically escaped
5. Multiple spaces in arguments are preserved in hover text
6. MiniMessage tags (`<red>`, `<bold>`, etc.) work together with MineDown

## Permissions

- `chat.minedown.customhover` - Allows players to set custom hover text for links and commands (without this, the link/command itself is shown in hover)
