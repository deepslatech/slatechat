# Advanced Formatting

Everything on this page is aimed at whoever edits `channels.yml` — not the average player typing in chat. These tags either need a permission most players won't have, or are awkward enough that they only really make sense baked into a config format string. For the syntax players actually use day to day, see [`formatting.md`](formatting.md).

> Config-baked `channels.yml` formats always render, regardless of permission — the gates below only apply to what a *player types*. See [`permissions.md`](permissions.md) for the full list.

---

## Shadow color

Colors the drop shadow behind the text separately from the text itself (requires a client on 1.21.4+; on older clients it's silently ignored). Gated by `chat.colored`, same as any other color:

```
<shadow:#000000>Text with a black shadow</shadow>
<shadow:yellow:0.5>Text with a half-transparent yellow shadow</shadow>
```

Also available as a MineDown arg:

```
[Text](shadow:#000000)
```

## Transition & Pride

Two more color-family tags, also gated by `chat.colored`, MiniMessage-only (no MineDown shortcut):

```
<transition:red:blue:green>Multi-color transition</transition>
<pride:trans>Pride flag gradient</pride>
```

> **`transition` is not a per-character gradient.** Unlike `<gradient>`, it picks *one solid color* for the whole span, chosen from the color list by the trailing `phase` argument (`-1` to `1`, defaults to `0`). `<transition:red:blue:green:0>text</transition>` renders `text` entirely red — every phase value is a single flat color, not a blend across the text. The visual "transition" only shows up if something re-sends the message with a different `phase` each time (a scoreboard/bossbar/actionbar on a repeating task, for example) — a one-off chat message will always look like a solid color. For a static multi-color effect in chat, use `gradient` or `rainbow` instead.

## Cosmetic tags — `chat.cosmetic`

These generate their own content rather than decorating existing text, so there's no MineDown equivalent. No server-state risk, but still stripped from player-typed chat without the permission:

```
<key:key.jump>                               # Shows the player's bound key for an action
<insert:some text>Click to insert</insert>   # Shift-click inserts "some text" into the chat box
<font:uniform>Text in the uniform font</font>
<lang:block.minecraft.diamond_block>         # Translatable text (aliases: tr, translate)
<lang_or:some.missing.key:Fallback text>     # With a fallback if the key doesn't exist (aliases: tr_or, translate_or)
<head:8667ba71-b85a-4004-af54-457a9734eed7>  # Renders a player head (name, UUID, or texture path)
<head:entity/player/wide/steve:false>        # Texture path form, hat layer off
<sprite:blocks:block/stone>                  # Renders a texture atlas sprite
```

> **`<head>` and `<sprite>` need Adventure 4.25.0+**, which Paper only started bundling in **1.21.10** (1.21.9 shipped a snapshot build; 1.21.8 and earlier are on Adventure 4.24.0 and don't have these tags at all). On an older server the tag isn't invalid syntax — MiniMessage just doesn't recognize it and passes it through as literal text, permission or not. There's no workaround short of updating the server; this isn't something SlateChat can gate around.

## Data tags — `chat.advanced` (OP-only by default)

These read live server state and can leak information to whoever reads the message — entity/block NBT, scoreboard values, nearby entities — the same reason vanilla restricts raw JSON text components like these to permission level 2+.

> **OP-only by default:** `chat.advanced` isn't declared in `plugin.yml`, so like `chat.reload`/`chat.update` it's OP-only until a permission plugin grants it deliberately — think twice before handing it to regular staff.

```
<selector:@e[limit=5]>       # Lists matching entities (alias: sel)
<score:PlayerName:objective> # A scoreboard value
<nbt:entity:'@s':Health>     # NBT data from an entity/block/storage (alias: data)
```

> **Resolved against the message sender, needs Paper 1.21.4+.** These three read live server state and (per Adventure's own docs) are "rendered serverside" against "platform-defined context" — a resolution step SlateChat does for you (via Paper's `PaperComponents.resolveWithContext`, using the sender as both the command context and the `@s`/scoreboard subject) before the message goes out. That API only exists from Paper 1.21.4 onward; on an older server the resolve step is silently skipped and these tags fall back to showing their raw pattern (e.g. `@e[limit=5]`) instead of resolved names — different limitation than `<head>`/`<sprite>` above, just a lower version floor.
