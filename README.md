<div align="center">

# <img src="public/image/icon.webp" alt="" width="32" height="32" align="center" /> SlateChat

> A modular chat plugin for Paper — channels, per-player chat colors, MineDown/MiniMessage formatting, spam & flood protection, DiscordSRV bridging, and ItemsAdder compatibility.

<img alt="platform" src="https://img.shields.io/badge/platform-Paper%201.21.x%20--%2026.x-2c3598?style=for-the-badge&logo=minecraft&logoColor=white&labelColor=1a1b26" />
<img alt="language" src="https://img.shields.io/badge/language-Kotlin-2c3598?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=1a1b26" />
<a href="#-license">
  <img alt="license" src="https://img.shields.io/badge/license-MIT-2c3598?style=for-the-badge&labelColor=1a1b26" />
</a>

📚 [Full documentation](https://deepslate.ch/docs/plugins/slatechat) &nbsp;•&nbsp; 📸 [Full showcase banner](public/image/banner.webp)

</div>

---

### ⚠️ IMPORTANT!

> [!WARNING]
> **LuckPerms placeholders from PlaceholderAPI will only work if you install their extension!**
> You can easily do it by running `/papi ecloud download LuckPerms` and `/papi reload`

---

## 📌 Overview

**SlateChat** replaces vanilla chat with a channel-based system driven by simple, per-channel YAML formats — local (radius/world) and global channels out of the box, plus your own custom ones. On top of chat routing it ships optional modules for per-player chat colors, a chat "focus" system, word filtering, spam & flood protection, a one-way DiscordSRV bridge, and ItemsAdder compatibility, each toggled independently.

This README only covers what you need to get it running and building from source. Everything else — full syntax guide, every config option, every permission node — lives in [`docs/`](docs), indexed below.

---

## 🧩 Compatibility

SlateChat only uses stable Bukkit/Paper API (events, commands, Adventure/MiniMessage) — no NMS, no version-specific internals — so the same jar carries forward across Minecraft releases without changes.

Verified compatible: **Paper 1.21.x through 26.2** (including the version-numbering switch from the old `1.21.x` scheme to the new year-based `26.x` scheme) — the jar compiled against the older `1.21.1` Paper API loads and runs unchanged on a 26.2 server, no recompilation needed.

> **Java note:** Minecraft 26.1+ requires the server itself to run on **Java 25**, regardless of the plugin. This doesn't change what SlateChat is built with (still Java 21) — it's a requirement for whoever is hosting a 26.1+ server, not something the plugin needs updated for.

> **Forks (Purpur and similar):** SlateChat should run fine on Paper forks, since they build on top of the same Paper API. **Guaranteed compatibility is on Paper itself** — forks aren't part of our testing.

---

## 🔌 Dependencies

| Plugin                                                                      | Required? | Why                                                                                                                                                                                       |
|-----------------------------------------------------------------------------|:---------:|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **[PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI)**      |   Hard    | Registered and used directly at startup; SlateChat won't load without it.                                                                                                                 |
| **[DiscordSRV](https://github.com/DiscordSRV/DiscordSRV)**                  |   Soft    | Only needed if you enable `modules.discordsrv` / `modules.discordsrv_send` to bridge chat to Discord.                                                                                    |
| **[ItemsAdder](https://itemsadder.com/)**      |   Soft    | Only needed for ItemsAdder compatibility (`modules.itemsadder`) — its `:name:` font images resolve in chat. Everything else works identically without it.                                |
| **[LuckPerms](https://luckperms.net/)**                                     | Optional  | Not a Bukkit dependency at all — only needed if your channel formats use `%luckperms_prefix%`/`%luckperms_suffix%` placeholders (resolved through PlaceholderAPI, see the warning above). |

---

## 🚀 Quick start

1. Grab `SlateChat.jar` from the [official downloads](https://deepslate.ch/resources/slatechat) or [GitLab Releases](https://gitlab.com/deepslatech/slatechat/-/releases).
2. Drop it into `plugins/`, alongside [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (required — SlateChat won't load without it).
3. Start the server once to generate `config.yml`, `channels.yml`, `color.yml`, `filter.yml` and `focus.yml` in `plugins/SlateChat/`.
4. Edit to taste (see [`docs/configuration.md`](docs/configuration.md)), then `/slatechat reload` — no restart needed.

---

## 📦 Building from source

```bash
git clone https://gitlab.com/deepslatech/slatechat.git
cd slatechat
./gradlew build

# The shaded jar will be at build/libs/SlateChat-<version>.jar
```

Requires **Java 21** to build (matches the `1.21.1` Paper API target — see [Compatibility](#-compatibility) for what that means on newer servers). No local Gradle install needed, the wrapper handles it.

---

## 📚 Documentation

| File                                                          | Covers                                                                    |
| --------------------------------------------------------------- | -------------------------------------------------------------------------- |
| [`docs/configuration.md`](docs/configuration.md)                 | Every option in `config.yml`, `channels.yml`, and the other module configs, plus SlateChat's own placeholders |
| [`docs/formatting.md`](docs/formatting.md)                       | Everyday MineDown/MiniMessage syntax — colors, links, hover, gradients     |
| [`docs/formatting-advanced.md`](docs/formatting-advanced.md)     | Shadow color, transition, pride, and raw MiniMessage tags (keybind, insert, NBT, and more) for `channels.yml` formats |
| [`docs/colors.md`](docs/colors.md)                               | The 16 legacy colors — chat codes, MiniMessage tags, hex values            |
| [`docs/commands.md`](docs/commands.md)                           | Every command SlateChat registers                                          |
| [`docs/permissions.md`](docs/permissions.md)                     | Every permission node SlateChat checks, in one place                       |

The same content is also mirrored on the [full documentation site](https://deepslate.ch/docs/plugins/slatechat), with nicer navigation.

---

## 📄 License

Distributed under the [MIT License](LICENSE).

SlateChat is a continuation of the original **[SimplexChat](https://github.com/Luucx7/SimplexChat)** project by Lucas Manoel Lopes (SagaciusDev), rebranded and maintained by **Deepslate Studios**. The original copyright notice is preserved in [`LICENSE`](LICENSE) alongside the new one, as required by the MIT license.

---

## 🙏 Acknowledgements

- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) by the extendedclip team.
- [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) by Scarsz and contributors.
- [ItemsAdder](https://itemsadder.com/) by LoneDev.
- [Adventure](https://github.com/KyoriPowered/adventure) / MiniMessage by KyoriPowered.
- Lucas Manoel Lopes and every contributor to the original [SimplexChat](https://github.com/Luucx7/SimplexChat) project this plugin descends from.
