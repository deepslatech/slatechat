![banner](/public/image/banner.png)

<div align="center">

### ⚠️ IMPORTANT!

**LuckPerms placeholders from PlaceholdersAPI will only work if you install their extension!**  
You can easily do it by running `/papi ecloud download LuckPerms` and `/papi reload`

</div>

---

## 📋 Permissions

| Permission                  | Description                                                                                                                   |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `chat.setcolor`             | Allows the player to use `/chatcolor` and change their chat color. The player still needs `chat.colored` for colors to apply. |
| `chat.colored`              | Allows the player to use color codes in chat, both legacy (`&6`) and HEX (`#RRGGBB`, if supported).                           |
| `chat.bypasscount`          | Player is excluded from the actionbar receivers count. Useful for vanished admins.                                            |
| `chat.minedown.customhover` | Allows custom hover text in MineDown links/commands. Without this, hover shows the link/command itself automatically.         |
| `simplexchat.reload`        | Allows the admins to reload the plugin with `/simplexchat reload`.                                                            |
| `chat.local`                | Permission to use the local channel (if `restrict: true` in `channels.yml`).                                                  |
| `chat.global`               | Permission to use the global channel (if `restrict: true` in `channels.yml`).                                                 |

> **Note:** Channel permissions (`chat.local`, `chat.global`, etc.) are configurable per channel in `channels.yml`. Set `restrict: true` and define the `permission` node to enable permission checks.
