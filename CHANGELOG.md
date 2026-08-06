# Changelog

All notable changes to SlateChat are documented here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/), versions follow [SemVer](https://semver.org/).

The `## [x.y.z]` section matching the version in `gradle.properties` is what the CI pipeline publishes as the GitLab release's patch notes when that file changes on `master` — see [`.gitlab-ci.yml`](.gitlab-ci.yml).

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
