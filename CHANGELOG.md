# Changelog

All notable changes to SlateChat are documented here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/), versions follow [SemVer](https://semver.org/).

The `## [x.y.z]` section matching the version in `gradle.properties` is what the CI pipeline publishes as the GitLab release's patch notes when that file changes on the default branch (`main`) — see [`.gitlab-ci.yml`](.gitlab-ci.yml).

## [1.7.0] - 2026-08-07

### Added

- **Private messaging**: `/tell` (aliases `/msg`, `/w`), `/r` to reply to whoever you last exchanged a tell with (either direction), and `/socialspy` for staff to watch other players' tells. Standalone, same-server only for now. **Off by default** (`modules.tell.enable: false`) — plenty of servers already run their own tell/msg solution and this shouldn't start competing with it uninvited. Formats and messages live in a new `tell.yml`. The recipient's copy includes a clickable `[reply]` by default (`<reply>` placeholder, `reply_button` in `tell.yml`). A vanished player (any vanish plugin — detected via `Player#canSee`, not a specific plugin's own convention) never shows up in tab-complete or as a reachable target, indistinguishable from being offline, unless the sender has `chat.tell.vanish.bypass`. `chat.tell.spy.bypass` makes a player's tells fully invisible to `/socialspy` watchers (not just redacted) while still always logging to console. When enabled, `/tell` itself is open to everyone unless `restrict: true` in `tell.yml`, in which case it needs `chat.tell` — that node, like every other one this release adds, is hardcoded rather than configurable, so a typo can never silently break the command server-wide.
- **Chat color GUI**: bare `/chatcolor` (still needs `chat.setcolor`) now opens a picker instead of printing a usage message — the 16 standard colors as dyed leather chestplates (a real RGB match per color, not an approximated wool/dye swatch), a reset button, and a book that opens an anvil-based hex picker with a live color preview as you type. Typed `/chatcolor <color>` also now accepts MiniMessage color tags (`<red>`, `<#4dc3ff>`), not just legacy `&c`/hex `#rrggbb`. The book/hex-picker can be turned off (`menu.hex_picker: false`) or pointed at a server's own custom GUI command instead (`custom` + `menu.hex_picker_command`) — useful since Paper has no way to give an already-open anvil a real title, so a custom-font GUI-texture background can't apply to that one screen the way it can the rest of the menu. Every icon in both screens is reskinnable per-role through ItemsAdder (`menu.itemsadder.*` in `color.yml`), falling back to the plain vanilla item whenever ItemsAdder isn't installed or the configured ID isn't found.
- **Join/quit/first-join messages**: custom `join`/`quit`/`first_join` broadcasts (new `joinquit.yml`). **Off by default** (`modules.joinquit.enable: false`), same reasoning as tell above — plenty of servers already have their own join/quit message plugin. `<player>` is always available, `<online>`/`<max>` are supported but left out of the shipped defaults. `chat.join.bypass` silences all three for a player, in either direction — covers vanished staff without needing to detect any particular vanish plugin's state.
- [Paper Hangar](https://hangar.papermc.io/DeepslateStudios/SlateChat) listed alongside the existing download page and GitLab Releases as an official distribution channel.

### Changed

- Full documentation moved off this repo's own `docs/` (now removed) onto [deepslate.ch](https://deepslate.ch/docs/plugins/slatechat) as the single canonical source, instead of two copies that could drift out of sync with each other.
- `config.yml`, and the other module config files, reorganized into clearly labeled sections (general/channel/update-checker/console messages, then modules grouped by Chat/Integrations/Maintenance) — no keys renamed or moved, purely cosmetic after several releases' worth of additions had piled up with no structure.
- ItemsAdder's `:emoji_name:` tab-complete/suggestion logic, previously private to `ChannelCommand`, extracted into a shared `EmojiSuggestions` so `/tell` could reuse it instead of a second copy.

## [1.6.0] - 2026-08-07

### Added

- **Full Folia support** (`folia-supported: true` in `plugin.yml`). Chat delivery no longer assumes a single server thread: sender-side formatting (placeholders, MineDown/MiniMessage rendering, `<selector>`/`<score>`/`<nbt>` resolution) runs on the sending player's own region thread, and each recipient — message delivery and the actionbar read-receipt count alike — is handled independently on *their* own region thread, since Folia only allows a player's location/permissions/messaging to be touched by the thread that actually owns them. Verified on a live Folia server: broadcast channels, radius-limited local channels (including recipients on the other side of a region boundary), the read-receipt count, and the "no color permission" heads-up all behave identically to Paper.

### Changed

- `Message`'s recipient fan-out (`send()`) was rewritten as a scatter/gather over each online player's own scheduler (new `MessageDelivery`) instead of one thread looping over everyone's location directly — required for the Folia support above. A player disconnecting mid-send, sender or recipient, can no longer leave the read-receipt count hanging or throw the message away silently. The radius/world eligibility check itself moved into a small pure function (new `RecipientEligibility`), which also collapses what used to be the same check written out twice (once for delivery, once for the count) into one place.

## [1.5.0] - 2026-08-06

### Added

- Bare `http(s)://` URLs typed straight into chat are now clickable automatically (with a hover showing the URL) — no need for the `[text](url)` MineDown syntax, though that still works exactly as before and can still be used for custom link text. Not gated by any permission, matching MineDown's own link syntax.
- `/slatechat wiki` links to the plugin's documentation (deepslate.ch) as a clickable message — the URL (`wiki_url` in `config.yml`) is editable. Open to everyone, no permission required.
- Documented the full list of valid `<pride>` flag names (`docs/formatting-advanced.md`) — the tag already accepted them, they just weren't written down anywhere.

### Fixed

- `<head>`/`<sprite>` rendered tinted by whatever color was active where they sat (a channel's own color, or a sender's `%chat_color%`) instead of the texture's real colors — a message like `<yellow>...<head:Notch> hi` painted the skin yellow. Wrapped in `<white>` plus all five decorations forced off, all real MiniMessage tags that close and revert cleanly. `<reset>` was tried first and reverted: it turns out to not be scoped by its own closing tag the way it looks (see Changed below), so the color of everything *after* the head/sprite fell back to Minecraft's plain default instead of surviving like it should have.
- `convertLegacyToMiniMessage` did a blind whole-string replace with no regard for existing `<...>` tag boundaries — found while building the URL auto-link above: a URL query string containing `&background=`, `&color=`, etc. would get its `&b`/`&c`/etc. silently rewritten into a color tag mid-URL, corrupting both the click target and the link's own visible text. Now skips tag content the same way `convertHexToMiniMessage` already did for the equivalent hex-color bug (see [1.3.0]).
- `/slatechat reload` crashing outright with a `NullPointerException` (and, once that was fixed, an `IllegalStateException`) — both were the same root cause described in Changed below: registering a Brigadier command from a live command's own execution isn't something Paper's API supports.

### Changed

- Commands (`/ch`, `/chatcolor`, `/slatechat`, and one per configured channel) now register through Paper's Brigadier `LifecycleEvents.COMMANDS` API instead of reflecting into `PluginCommand`'s private constructor and the server's private `commandMap` — the exact thing the README's "no NMS, no version-specific internals" claim never quite covered. `plugin.yml` no longer declares any commands, and `api-version` moved from a very stale `1.13` to `1.21` to match the Paper API this project actually targets. One deliberate, narrow exception remains: Paper's Brigadier dispatcher can only be mutated while that one lifecycle event is actively dispatching (at boot, or an actual `/reload confirm`) — never from a live command's own execution, confirmed the hard way by the crashes fixed above — so a channel whose *command name* is brand new since boot (not just an edited existing one) still goes through the old reflection technique to become reachable without a full server restart. Isolated to its own file (`LegacyCommandRegistrar`), used from exactly one call site, and documented in code as the deliberate exception it is.
- Restricted channels' commands are now hidden from tab-complete/help using the same `blocksSender()` rule the rest of the plugin already uses everywhere else, instead of the native `PluginCommand.permission` field — which had no way to express "restricted with no `permission` node configured = OP only", a case `blocksSender()` has always handled correctly.

## [1.4.0] - 2026-08-06

### Added

- Restricted channels (`channels.yml` `restrict: true`) now wire their `permission` node natively into Bukkit, so the channel's command (`/l`, `/g`, etc.) is hidden from tab-complete and `/help` for senders who lack it. Trade-off worth knowing: Bukkit checks this *before* the command runs, so a sender without permission now sees the server's generic denial message there instead of SlateChat's own configurable `no_permission` — that message still shows for `/ch` and everywhere else, since this only affects the per-channel command's own native permission check.
- Typing a color (legacy `&c` or raw MiniMessage `<red>`/etc.) directly in chat without `chat.colored` now gets a quiet action-bar heads-up (the same `no_permission` message) — the message itself still sends normally, just without the color, same as before; previously there was no feedback at all.
- MineDown formatting args now accept a `!` prefix to force a decoration off — `[Text](!bold)` — using MiniMessage's own inversion syntax.

### Fixed

- `<reset>` typed in chat without `chat.colored` could wipe out the *channel's own* established color (e.g. a channel format's `<yellow>...<message>`), not just the player's — because the channel format and the player's message are parsed as one single MiniMessage string, `<reset>` doesn't know the difference. It's now stripped like the other color-family tags: with it gone, the channel's color falls through normally instead of resetting to Minecraft's plain default. With `chat.colored`, `<reset>` still works exactly as documented by Adventure.
- `[Text](underline)` MineDown shorthand silently did nothing — `underline` was never a real MiniMessage tag name or alias (the real one is `underlined`; `u` is the alias). Docs described the same wrong spelling. Both fixed.
- `[Text](!bold)` MineDown shorthand was recognized as valid input but silently produced no effect — see Added above.
- A bare `>` character outside a tag (e.g. a channel format's own `<player><bold>>` separator) was silently deleted by the hex-conversion step introduced earlier in this same version — its tag/plain-text splitter excluded `>` from *both* alternatives, so a lone one matched neither and `Matcher.find()` skipped over it without a trace.
- The action-bar "no permission for color" heads-up (see Added) and a channel's own read-receipt count both only ever show the *last* action bar sent — since the heads-up fired earlier in the pipeline, the read-receipt silently overwrote it before it could be seen. They're now combined into a single action-bar send when both apply.
- A restricted channel (`restrict: true`) with no `permission` configured blocked every sender, including OP — every other permission check in the plugin already lets OP bypass an undeclared/misconfigured node by default; this was the one exception, and could lock the server owner out of their own channel over a config typo.

### Changed

- Large internal cleanup, no other behavior change intended: `Message.kt` (previously one 436-line class doing filtering, permission-gating, rendering, three separate plugin integrations, console logging, and recipient targeting all at once) split into single-responsibility pieces under `core/format` and `core/integrations`. `plugin.description` (deprecated) replaced with Paper's `plugin.pluginMeta` throughout. The 16 Minecraft color names and 5 decorations, previously hand-typed across several places, now come from Adventure's own `NamedTextColor.NAMES`/`TextDecoration.NAMES` in one shared source each (this is what surfaced the `underline`/`!bold` bugs above). Project's first unit tests added (48, all passing) covering the newly-extracted pure logic.

## [1.3.0] - 2026-08-06

### Added

- Shadow color, transition, and pride MiniMessage tags (`chat.colored`-gated, shadow color also has a MineDown shortcut: `[Text](shadow:#hex)`).
- Keybind, insertion, font, translatable (`lang`/`lang_or`), player head, and sprite MiniMessage tags, gated behind a new `chat.cosmetic` permission. Note: `<head>`/`<sprite>` require Paper 1.21.10+ (Adventure 4.25.0) — on older servers the tag is silently unrecognized regardless of permission.
- Entity selector, scoreboard, and NBT MiniMessage tags, gated behind a new `chat.advanced` permission (OP-only by default — these can expose live server state). Resolved against the message sender via Paper's `PaperComponents.resolveWithContext` (needs Paper 1.21.4+; older servers fall back to showing the raw pattern, e.g. `@e[limit=5]`, instead of resolved names).
- `debug` config option — logs the fully processed message string plus the raw component JSON for every chat message. Off by default, noisy; only meant for troubleshooting.
- `console_format_colors` config option — renders console/log output with real ANSI colors (via Paper's `ComponentLogger`) instead of plain text.

### Fixed

- Players without `chat.colored` could bypass the color restriction entirely by typing raw MiniMessage tags (`<red>`, `<gradient:...>`, etc.) directly instead of MineDown syntax — only legacy `&`/hex codes were being stripped. Now the whole color-tag family is stripped consistently, MineDown args and raw tags alike.
- `<gradient:#hex:#hex>`, `<transition:#hex:#hex>`, and `<shadow:#hex>` (raw-typed, MineDown-generated, or baked into `channels.yml`) were silently broken — hex-to-MiniMessage conversion ran indiscriminately over the whole string and rewrote hex color *arguments* into nested `<#hex>` tags, which MiniMessage can't parse as a color, so the tag fell back to literal text. Hex conversion now skips content already inside a tag.
- `<!shadow>` (the disable-shadow alias) wasn't covered by the `chat.colored` gate — the regex only accounted for `</tag>` closing syntax, not `<!tag>`.
- `<selector>`/`<score>`/`<nbt>` reached the client unresolved (showing the raw pattern, e.g. `@e[limit=5]`, instead of resolved names) because nothing ever supplied Paper the sender context these components need to render server-side — see Added above.
- Console log only ever stripped legacy `&` codes, leaving raw MiniMessage tags (`<selector:...>`, `<yellow>`, etc.) visible as literal text; now properly deserialized and rendered as plain text (or ANSI colors with `console_format_colors`), with hover/click content naturally excluded either way.
- GitLab releases shipped with no real patch notes (just a bare "SlateChat x.y.z" description) — the CI pipeline now pulls the matching version section from this changelog instead.

### Changed

- Docs reorganized by audience: `docs/formatting.md` now covers only everyday chat syntax, `docs/formatting-advanced.md` covers admin/config-only tags, and `docs/permissions.md` is now the single canonical permission table (previously duplicated across the README and two other docs).

## [1.2.0] - 2026-08-05

### Added

- ItemsAdder compatibility — its `:name:` font-image shorthand (emoji, rank badges, custom icons) resolves wherever SlateChat renders text, respecting each font image's own permission.
- Update checker — checks GitLab's releases API on startup and notifies admins with `chat.update`; `/slatechat update` checks on demand.

### Fixed

- `/slatechat reload` no longer leaves already-connected players pointing at stale channel objects.

### Changed

- Moved off the legacy Bukkit scheduler and `Bukkit.getLogger()` in favor of Paper's Folia-aware scheduler and the plugin's own SLF4J-backed logger.
- Dropped legacy Bukkit `ChatColor` from channel format loading in favor of Adventure/MiniMessage throughout.

## [1.1.0] - 2026-08-04

### Fixed

- DiscordSRV per-channel routing — a SlateChat channel is now only forwarded if it's explicitly mapped in DiscordSRV's own `Channels` config, instead of hardcoded aliases.

## [1.0.0] - 2026-08-03

Initial SlateChat release — rebrand from the original SimplexChat project, migrated from Java to Kotlin targeting Minecraft 1.21.x, with the CI pipeline that auto-builds and releases on a `gradle.properties` (formerly `pom.xml`) version bump.
