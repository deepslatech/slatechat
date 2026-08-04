<div align="center">

# <img src="public/image/icon.webp" alt="" width="32" height="32" align="center" /> SlateChat

> A modular chat plugin for Paper — channels, per-player chat colors, MineDown/MiniMessage formatting, spam & flood protection, and DiscordSRV bridging.

<img alt="platform" src="https://img.shields.io/badge/platform-Paper%201.21.x%20--%2026.x-2c3598?style=for-the-badge&logo=minecraft&logoColor=white&labelColor=1a1b26" />
<img alt="language" src="https://img.shields.io/badge/language-Kotlin-2c3598?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=1a1b26" />
<a href="#-license">
  <img alt="license" src="https://img.shields.io/badge/license-MIT-2c3598?style=for-the-badge&labelColor=1a1b26" />
</a>

📸 [Full showcase banner](public/image/banner.webp)

</div>

---

[[_TOC_]]

---

### ⚠️ IMPORTANT!

> [!WARNING]
> **LuckPerms placeholders from PlaceholderAPI will only work if you install their extension!**
> You can easily do it by running `/papi ecloud download LuckPerms` and `/papi reload`

---

## 📌 Overview

**SlateChat** replaces vanilla chat with a channel-based system driven by simple, per-channel YAML formats. Every channel supports full [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) expansion, MineDown-style links/hover/gradients rendered through Adventure's MiniMessage, and legacy `&`/`§` codes for drop-in compatibility with existing configs — see [`Syntax.md`](Syntax.md) and [`Colors.md`](Colors.md).

On top of chat routing, it ships optional modules for per-player chat colors, a chat "focus" system, word filtering/replacement, spam & flood protection, and a one-way bridge to DiscordSRV — each toggled independently so you only configure what you actually use.

---

## ✨ Features

- **Channel system** — ships with `local` (radius or world-based) and `global` (broadcast) channels out of the box; add as many custom channels as you want in `channels.yml`.
- **Per-player chat color** — `/chatcolor` lets players pick a legacy or HEX/RGB color for their messages, with configurable clear keywords and an `enable_rgb` toggle.
- **MineDown + MiniMessage rendering** — `[Click here](/warp spawn Teleport to spawn)` syntax for clickable/hoverable links and commands, HEX colors (`#FF5733`), gradients, and rainbow text. See [`Syntax.md`](Syntax.md) for the full syntax guide and [`Colors.md`](Colors.md) for the color reference.
- **Chat focus** — players can lock themselves to a default channel, with optional forced-focus per world.
- **Chat filter** — censor blocked words (replaced with asterisks) or auto-replace specific words, case-insensitive.
- **Spam & flood protection** — block messages with excessive repeated characters or repeated consecutive messages.
- **Action bar read receipts** — senders see how many nearby/online players actually received their message.
- **DiscordSRV bridge** — optionally forward console-logged chat to Discord, per-channel: each SlateChat channel is only sent if it's mapped in DiscordSRV's own `Channels` config option, so you pick exactly which channel(s) get relayed (off by default to avoid duplicate messages if DiscordSRV already relays chat itself).
- **Full PlaceholderAPI support** — usable in channel formats, console log format, and every player-facing message.

---

## 🔤 Placeholders

Exposed under the `chat` identifier (`%chat_<placeholder>%`):

| Placeholder     | Description                                                  |
| ---------------- | ------------------------------------------------------------- |
| `%chat_color%`   | The player's currently selected chat color (legacy/HEX code). |
| `%chat_channel%` | The display name of the player's currently focused channel.   |

---

## 📋 Permissions

| Permission                  | Description                                                                                                                   |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `chat.setcolor`             | Allows the player to use `/chatcolor` and change their chat color. The player still needs `chat.colored` for colors to apply. |
| `chat.colored`               | Allows the player to use color in chat — legacy (`&6`), HEX (`#RRGGBB`), and MineDown color/gradient/rainbow args. Without it, no color is applied anywhere (raw codes are stripped, MineDown color args are silently ignored) and `/chatcolor` refuses to set one. |
| `chat.count.bypass`          | Player is excluded from the actionbar receivers count. Useful for vanished admins.                                            |
| `chat.hover.bypass`         | Allows custom hover text in MineDown links/commands. Without this, hover shows the link/command itself automatically.         |
| `chat.reload`                | Allows the admins to reload the plugin with `/slatechat reload`.                                                              |
| `chat.local`                 | Permission to use the local channel (if `restrict: true` in `channels.yml`).                                                  |
| `chat.global`                | Permission to use the global channel (if `restrict: true` in `channels.yml`).                                                 |

> **Note:** Channel permissions (`chat.local`, `chat.global`, etc.) are configurable per channel in `channels.yml`. Set `restrict: true` and define the `permission` node to enable permission checks.

---

## ⌨️ Commands

No built-in aliases — server aliases are already configurable per-server, so SlateChat ships with just the plain command name and lets you set your own on top if you want one.

| Command                    | Description                                      |
| --------------------------- | ------------------------------------------------- |
| `/ch <channel>`            | Switch your active chat channel.                  |
| `/chatcolor <color\|clear>` | Set or clear your personal chat color.            |
| `/slatechat`               | Show the running plugin version.                  |
| `/slatechat reload`        | Reload configs and modules (`chat.reload`).        |

---

## ⚙️ Configuration

Each module keeps its own file, so you only need to touch what you're actually enabling.

### `config.yml` — modules & core messages

```yaml
modules:
    chatcolor: true      # Per-player chat color (/chatcolor)
    discordsrv: true      # DiscordSRV integration hook
    discordsrv_send: false # Forward chat to DiscordSRV, per-channel as mapped in DiscordSRV's own Channels config (off by default)
    focus: true           # Chat focus module
    filter: true           # Word filter/replacer
    chatformat:
        spam: { enable: false, limit: 10 }   # Repeated-character spam guard
        flood: { enable: false }              # Repeated-message guard
        lowercase: false
        title_format: false
```

> **DiscordSRV channel selection:** with `discordsrv_send: true`, a SlateChat channel is only forwarded if its name is a key in DiscordSRV's own `Channels` option (`plugins/DiscordSRV/config.yml`) — e.g. `Channels: {"global": "123456789"}` forwards only the `global` channel; channels left out simply aren't sent. If you also want to stop DiscordSRV's *own* built-in chat listener from independently relaying raw vanilla chat (which ignores SlateChat's channels entirely), set `DiscordChatChannelMinecraftToDiscord: false` in DiscordSRV's config so SlateChat's per-channel forwarding is the only path to Discord.

### `channels.yml` — channel definitions

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

Add PlaceholderAPI placeholders (e.g. `%luckperms_prefix%`) directly into any channel's `format` if you want prefixes/suffixes — they're not baked into the defaults so a fresh install doesn't depend on LuckPerms or other rank plugins.

### `focus.yml` — forced focus per world

```yaml
worlds:
    - dummy:global
```

### `filter.yml` — blocked words & replacements

```yaml
blocked_words:
    - "bitch"
replace:
    - "barney->swarley"
```

### `color.yml` — chat color module

```yaml
enable_rgb: true
clear_keywords: ["clear", "remove", "reset"]
```

---

## 🧩 Compatibility

SlateChat only uses stable Bukkit/Paper API (events, commands, Adventure/MiniMessage) — no NMS, no version-specific internals — so the same jar carries forward across Minecraft releases without changes.

Verified compatible: **Paper 1.21.x through 26.2** (including the version-numbering switch from the old `1.21.x` scheme to the new year-based `26.x` scheme) — the jar compiled against the older `1.21.1` Paper API loads and runs unchanged on a 26.2 server, no recompilation needed.

> **Java note:** Minecraft 26.1+ requires the server itself to run on **Java 25**, regardless of the plugin. This doesn't change what SlateChat is built with (still Java 21, see below) — it's a requirement for whoever is hosting a 26.1+ server, not something the plugin needs updated for.

> **Forks (Purpur and similar):** SlateChat should run fine on Paper forks like Purpur, since they build on top of the same Paper API. **Guaranteed compatibility is on Paper itself** — forks aren't part of our testing, so behavior there isn't officially verified.

---

## 🔌 Dependencies

| Plugin                                                                      | Required? | Why                                                                                                                                                                                       |
|-----------------------------------------------------------------------------|:---------:|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **[PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI)**      |   Hard    | Registered and used directly at startup; SlateChat won't load without it.                                                                                                                 |
| **[DiscordSRV](https://github.com/DiscordSRV/DiscordSRV)**                  |   Soft    | Only needed if you enable `modules.discordsrv` / `modules.discordsrv_send` to bridge chat to Discord.                                                                                    |
| **[LuckPerms](https://luckperms.net/)**                                     | Optional  | Not a Bukkit dependency at all — only needed if your channel formats use `%luckperms_prefix%`/`%luckperms_suffix%` placeholders (resolved through PlaceholderAPI, see the warning above). |

---

## 📦 Building from source

```bash
# Clone the repository
git clone https://gitlab.com/deepslatech/slatechat.git
cd slatechat

# Build the plugin jar
mvn clean package

# The shaded jar will be at target/SlateChat-<version>.jar
```

### Requirements

- **Java 21** to build (matches the `1.21.1` Paper API target — running the resulting jar on a Minecraft 26.1+ server requires that server to run on Java 25, see [Compatibility](#-compatibility))
- **Maven**
- **Paper** 1.21.1 through 26.2 (or compatible fork) — see [Compatibility](#-compatibility)
- **[PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI)** — required dependency
- **[DiscordSRV](https://github.com/DiscordSRV/DiscordSRV)** — optional, only needed for the Discord bridge

---

## 📄 License

Distributed under the [MIT License](LICENSE).

SlateChat is a continuation of the original **[SimplexChat](https://github.com/Luucx7/SimplexChat)** project by Lucas Manoel Lopes (SagaciusDev), rebranded and maintained by **Deepslate Studios**. The original copyright notice is preserved in [`LICENSE`](LICENSE) alongside the new one, as required by the MIT license.

---

## 🙏 Acknowledgements

- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) by the extendedclip team.
- [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) by Scarsz and contributors.
- [Adventure](https://github.com/KyoriPowered/adventure) / MiniMessage by KyoriPowered.
- Lucas Manoel Lopes and every contributor to the original [SimplexChat](https://github.com/Luucx7/SimplexChat) project this plugin descends from.
