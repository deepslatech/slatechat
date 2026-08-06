# Permissions

The canonical list — every other doc links back here instead of repeating it. Grouped by what they gate, not by which command/tag happens to check them.

> SlateChat's `plugin.yml` doesn't declare a `permissions:` block. On Bukkit, an undeclared permission node falls back to **OP-only** until a permission plugin (LuckPerms, etc.) explicitly grants it — that's every node below except the channel ones, which channel authors opt into via `restrict` + `permission` in `channels.yml`.

## Chat color & formatting

| Permission          | Grants                                                                                                                                                                                                                                 |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `chat.colored`       | Colors, HEX, gradient, rainbow, shadow, transition, pride, and `<reset>` — both [MineDown args](formatting.md) and raw [MiniMessage tags](formatting-advanced.md) typed directly in chat. Without it, all of the above are stripped from what a player types. `<reset>` is included because it can wipe the channel's own established color, not just the player's. |
| `chat.cosmetic`      | The `key`, `insert`, `font`, `lang`/`lang_or`, `head`, and `sprite` [advanced tags](formatting-advanced.md#cosmetic-tags--chatcosmetic) — no server-state risk, just not everyday syntax. `head`/`sprite` also need Paper 1.21.10+ regardless of permission — see the note in that section. |
| `chat.advanced`      | The `selector`, `score`, and `nbt` [advanced tags](formatting-advanced.md#data-tags--chatadvanced-op-only-by-default) — these read live server state, so this node is **OP-only by default**. Grant deliberately. Resolving to actual values (not just the raw pattern) also needs Paper 1.21.4+ — see the note in that section. |
| `chat.hover.bypass`  | Custom hover text on MineDown links/commands. Without it, hover shows the link/command itself automatically.                                                                                                                          |
| `chat.setcolor`      | Using `/chatcolor` at all, including clearing. Still needs `chat.colored` to set an actual color, not just clear one.                                                                                                                |

## Commands

| Permission     | Grants                                                                                                       |
| --------------- | ---------------------------------------------------------------------------------------------------------- |
| `chat.reload`  | Running `/slatechat reload`. OP-only by default.                                                             |
| `chat.update`  | Getting notified in chat on join if a newer version is out (console is always notified regardless). OP-only by default. |

## Channels & misc

| Permission           | Grants                                                                       |
| --------------------- | ----------------------------------------------------------------------------- |
| `chat.local`          | Speaking in the default `local` channel (only enforced if it's `restrict: true`) |
| `chat.global`         | Speaking in the default `global` channel (only enforced if it's `restrict: true`) |
| _custom_              | Whatever `permission` node you set on a custom `restrict: true` channel      |
| `chat.count.bypass`   | Excluded from the actionbar read-receipt count — useful for vanished admins  |
