# Changelog

All notable changes to SlateChat are documented here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/), versions follow [SemVer](https://semver.org/).

The `## [x.y.z]` section matching the version in `gradle.properties` is what the CI pipeline publishes as the GitLab release's patch notes when that file changes on `master` — see [`.gitlab-ci.yml`](.gitlab-ci.yml).

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
