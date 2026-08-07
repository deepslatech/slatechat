# Commands

No built-in aliases — server aliases are already configurable per-server, so SlateChat ships with just the plain command name and lets you set your own on top if you want one.

## `/ch <channel>`

Switches the sender's default channel to `<channel>`, matched against a channel's name or its `command` (so `/ch g` and `/ch global` both work if that's how `global` is set up). Tab-completes to the channels the sender is allowed to use.

| Permission                                                                        |
| --------------------------------------------------------------------------------- |
| The channel's own `permission`, only checked if that channel has `restrict: true` |

## `/chatcolor <color|clear>`

Sets the sender's personal chat color, used to render `%chat_color%` in channel formats. Accepts a 6-digit hex code (`#4DC3FF`) or a legacy color code (`&c`) — hex only works while `enable_rgb` is on in `color.yml`. Passing one of the configured `clear_keywords` (`clear`, `remove`, `reset` by default) removes it instead.

| Permission                                                                       |
| --------------------------------------------------------------------------------- |
| `chat.setcolor` to use the command at all, `chat.colored` to set an actual color |

Console can't run this command — it's player-only.

## `/slatechat`

With no arguments, prints the running SlateChat version. `/slatechat reload` reloads the base config, channels, and every enabled module (chat color, focus, filter, DiscordSRV/ItemsAdder detection) without a server restart. `/slatechat update` checks GitLab for a newer release right now and reports straight back to whoever ran it — works from console too. `/slatechat wiki` links to the plugin's documentation (`wiki_url` in `config.yml`, editable) — open to everyone, no permission required.

| Subcommand | Permission    |
| ---------- | ------------- |
| `reload`   | `chat.reload` |
| `update`   | `chat.update` |
| `wiki`     | *(none)*      |

## Channel permissions

Channels only enforce a permission check when `restrict: true` is set on that channel in `channels.yml` — an unrestricted channel is open to everyone. The built-in channels ship as:

| Channel  | Permission    | Restricted by default |
| -------- | ------------- | :--------------------: |
| `local`  | `chat.local`  | No                    |
| `global` | `chat.global` | No                    |

Give a channel `restrict: true` and its `permission` node to gate it behind a rank.

## Every permission node

The commands and channels above only cover a handful of permissions — see [`permissions.md`](permissions.md) for the full list, including the ones formatting/color tags check.
