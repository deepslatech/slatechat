# Configuration

Every option in `config.yml`, `channels.yml` and the other module config files. All of them support [MiniMessage/MineDown](formatting.md) formatting, plus any [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) expansion you have installed. Each module has its own file so you're never staring at options you don't use.

## Plugin placeholders

SlateChat exposes two placeholders of its own, under the `chat` identifier (`%chat_<placeholder>%`), usable anywhere PlaceholderAPI is — including inside the channel `format`s below:

| Placeholder     | Description                                                  |
| ---------------- | ------------------------------------------------------------- |
| `%chat_color%`   | The player's currently selected chat color (legacy/HEX code). |
| `%chat_channel%` | The display name of the player's currently focused channel.   |

## `config.yml` — modules & core messages

```yaml
# Mirror every chat message to console
log_to_console: true
console_log: "[<channel>] <player>> <message>"
# Render colors as real ANSI escapes in console/log file instead of plain text.
# Hover/click never show up in console either way, on or off.
console_format_colors: false
# Verbose logging: the processed message + raw component JSON for every message. Noisy.
debug: false

modules:
    chatcolor: true      # Per-player chat color (/chatcolor)
    discordsrv: true      # DiscordSRV integration hook
    discordsrv_send: false # Forward chat to DiscordSRV, per-channel as mapped in DiscordSRV's own Channels config (off by default)
    itemsadder: true      # ItemsAdder compatibility: resolve its :name: font images in chat (no-op if ItemsAdder isn't installed)
    update_checker: true  # Check GitLab for a newer release on startup (async, fail-silent)
    focus: true           # Chat focus module
    filter: true           # Word filter/replacer
    chatformat:
        spam: { enable: false, limit: 10 }   # Repeated-character spam guard
        flood: { enable: false }              # Repeated-message guard
        lowercase: false
        title_format: false
```

> **DiscordSRV channel selection:** with `discordsrv_send: true`, a SlateChat channel is only forwarded if its name is a key in DiscordSRV's own `Channels` option (`plugins/DiscordSRV/config.yml`) — e.g. `Channels: {"global": "123456789"}` forwards only the `global` channel; channels left out simply aren't sent. If you also want to stop DiscordSRV's *own* built-in chat listener from independently relaying raw vanilla chat (which ignores SlateChat's channels entirely), set `DiscordChatChannelMinecraftToDiscord: false` in DiscordSRV's config so SlateChat's per-channel forwarding is the only path to Discord.

> **ItemsAdder compatibility:** with `itemsadder: true` (default), any `:name:` font image ItemsAdder registers — emoji, rank/prefix badges (including one set via `%luckperms_prefix%`), custom icons — resolves through ItemsAdder's own `FontImageWrapper.replaceFontImages()` API wherever SlateChat renders text, including that font image's own `permission:`, if it's set in ItemsAdder's config. Nothing to configure on ItemsAdder's side; it just needs to be installed and enabled.

## `channels.yml` — channel definitions

Ships with `local` (radius-based) and `global` (broadcast) by default:

```yaml
local:
    format: "<yellow>[Local] <yellow><player><blue><bold>> <yellow>%chat_color%<message>"
    command: "l"
    broadcast: false
    radius: 200
    restrict: false
    actionbar: true
    permission: chat.local

global:
    format: "<gray>[Global] <gray><player><blue><bold>> <gray>%chat_color%<message>"
    command: "g"
    broadcast: true
    radius: 0
    restrict: false
    actionbar: false
    permission: chat.global
```

| Key          | Meaning                                                                                    |
| ------------ | ------------------------------------------------------------------------------------------ |
| `format`     | The message format. Supports `<player>`, `<message>`, PAPI placeholders and `%chat_color%` |
| `command`    | What `/ch <command>` matches, alongside the channel's name                                 |
| `enable`     | Only used on `local` — whether the default channel is active                               |
| `broadcast`  | If `true`, ignores `radius` and reaches every online player                                |
| `radius`     | Block radius the message reaches when `broadcast` is `false`. `0` means same world only    |
| `restrict`   | If `true`, only players with `permission` can use this channel                             |
| `actionbar`  | Show the sender an action bar read receipt for this channel                                |
| `permission` | Node checked when `restrict` is `true`, and reported by `/ch` tab-completion               |

Add as many extra channels as you want under this file, they all support the same keys as `global`. Add PlaceholderAPI placeholders (e.g. `%luckperms_prefix%`) directly into any channel's `format` if you want prefixes/suffixes — they're not baked into the defaults so a fresh install doesn't depend on LuckPerms or other rank plugins.

## `focus.yml` — forced focus per world

Forces players into a channel while they're in specific worlds, regardless of what they last picked with `/ch`.

```yaml
worlds:
    - dummy:global
```

Each entry is `world:channel` — replace `dummy` with a real world name and `global` with the channel you want enforced there.

## `filter.yml` — blocked words & replacements

Word filtering. Matching is case-insensitive.

```yaml
blocked_words:
    - "bitch"
replace:
    - "barney->swarley"
```

## `color.yml` — chat color module

Backs the `/chatcolor` command.

```yaml
enable_rgb: true
clear_keywords: ["clear", "remove", "reset"]
```
