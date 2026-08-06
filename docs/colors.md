# Colors Reference

The 16 standard Minecraft legacy colors, usable anywhere SlateChat accepts color input — legacy codes (`&e`), MiniMessage tags (`<yellow>`), or MineDown args (`[Text](yellow)`). All require `chat.colored` when typed by a player; see [`formatting.md`](formatting.md).

| Color         | Name          | Chat code | Hex code  |
| ------------- | ------------- | :-------: | --------- |
| ⬛ Black       | `black`       |   `&0`    | `#000000` |
| 🟦 Dark Blue   | `dark_blue`   |   `&1`    | `#0000AA` |
| 🟩 Dark Green  | `dark_green`  |   `&2`    | `#00AA00` |
| 🟦 Dark Aqua   | `dark_aqua`   |   `&3`    | `#00AAAA` |
| 🟥 Dark Red    | `dark_red`    |   `&4`    | `#AA0000` |
| 🟪 Dark Purple | `dark_purple` |   `&5`    | `#AA00AA` |
| 🟧 Gold        | `gold`        |   `&6`    | `#FFAA00` |
| ⬜ Gray        | `gray`        |   `&7`    | `#AAAAAA` |
| ⬛ Dark Gray   | `dark_gray`   |   `&8`    | `#555555` |
| 🟦 Blue        | `blue`        |   `&9`    | `#5555FF` |
| 🟩 Green       | `green`       |   `&a`    | `#55FF55` |
| 🟦 Aqua        | `aqua`        |   `&b`    | `#55FFFF` |
| 🟥 Red         | `red`         |   `&c`    | `#FF5555` |
| 🟪 Light Purple| `light_purple`|   `&d`    | `#FF55FF` |
| 🟨 Yellow      | `yellow`      |   `&e`    | `#FFFF55` |
| ⬜ White       | `white`       |   `&f`    | `#FFFFFF` |

## Usage per syntax

```
&e                     # Legacy code
<yellow>               # MiniMessage tag
[Text](yellow)         # MineDown arg
#FFFF55                # Raw HEX (converted to <#FFFF55> automatically)
```

## Formatting codes (not colors, but combine with any of the above)

| Format        | Legacy code | MiniMessage tag    |
| -------------- | :---------: | ------------------- |
| Bold           | `&l`        | `<bold>`             |
| Italic         | `&o`        | `<italic>`           |
| Underlined     | `&n`        | `<underlined>`       |
| Strikethrough  | `&m`        | `<strikethrough>`    |
| Obfuscated     | `&k`        | `<obfuscated>`       |
| Reset          | `&r`        | `<reset>`             |

> HEX colors (`#RRGGBB`) and gradients are only available via MiniMessage (`<#RRGGBB>`, `<gradient:#A-#B>`) or MineDown (`#RRGGBB`, `#RRGGBB-#RRGGBB`) — there's no legacy equivalent, since vanilla legacy codes only cover the 16 colors above.

> The same names/hex values also work as the color argument for `<shadow:...>` (text shadow color) — see [`formatting-advanced.md`](formatting-advanced.md#shadow-color).
