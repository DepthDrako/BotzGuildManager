# Guild Commands

All `/guild` commands require the executor to be an in-game player (not console, except `/guild xp`).

---

## Guild Lifecycle

### `/guild create <name> <tag>`
Creates a new guild. You become the Leader.

| Argument | Limit | Notes |
|---|---|---|
| `name` | ≤ 32 chars | Display name shown in `/guild info` |
| `tag` | ≤ 6 chars | Short prefix shown in guild chat |

- You must not already be in a guild
- Default ranks are created automatically: **Leader, Officer, Member, Recruit**

### `/guild disband`
Permanently deletes your guild and removes all members.
- **Requires:** Leader only
- Broadcasts a disbandment message to all online members

### `/guild leave`
Removes you from your current guild.
- **Cannot** be used by the Leader (transfer leadership or disband first)

---

## Membership

### `/guild invite <player>`
Sends a 60-second guild invitation to an online player.
- **Requires:** `INVITE` permission

### `/guild join <guildName>`
Accepts a pending invitation to a guild.
- You must have a valid, non-expired invite from that guild

### `/guild kick <player>`
Removes a member from the guild.
- **Requires:** `KICK` permission
- Cannot kick someone of equal or higher rank (unless you are the Leader)

---

## Information

### `/guild info [guildName]`
Shows detailed guild information.
- Without argument: shows your guild
- With argument: shows any guild's public info

Displays: name, tag, level, XP/next level, leader, member count, online count, friendly fire status, ally count, upgrade count, and MOTD.

### `/guild list`
Lists all guilds on the server with tags, levels, member counts, and online player counts.

### `/guild log`
Shows the 15 most recent guild activity entries (newest first).

Logged events include: member joins/leaves, rank assignments, home location changes, friendly fire toggles, bank deposits/withdrawals, and war events.

---

## Home Teleport

### `/guild sethome`
Sets the guild home to your current position and dimension.
- **Requires:** `SET_HOME` permission
- Logged in guild activity log

### `/guild home`
Teleports you to the guild home.
- Has a cooldown (default: 30 seconds)
- Fails if no home is set

---

## Settings

### `/guild ff <on|off>`
Toggles friendly fire (guild members hurting each other).
- **Requires:** `TOGGLE_FF` permission
- Default: OFF

### `/guild motd <message>`
Sets the guild Message of the Day (shown in `/guild info`).
- **Requires:** `MANAGE_RANKS` permission
- Supports spaces — everything after the command is the message

---

## Chat

### `/guild chat <message>` or `/gc <message>`
Sends a message visible only to online guild members.

### `/guild ochat <message>` or `/goc <message>`
Sends a message visible only to Officers and above (priority ≥ 300).

---

## XP Admin

### `/guild xp <guildName> <amount>`
Adds or removes XP from a guild.
- **Requires:** OP level 2
- Works from console
- Supports negative values to subtract XP
- Recalculates level and broadcasts level-up/down to online members
- Example: `/guild xp MyGuild 1000`

---

## Guild XP & Leveling

Guilds level up by accumulating XP. Max level is configurable (default: 25).

| Source | Default XP |
|---|---|
| Member login | 5 XP |
| Player kill by guild member | 15 XP |
| Winning a war | 500 XP |

**XP required per level:** `(level - 1)² × 100`

| Level | XP Needed |
|---|---|
| 2 | 100 |
| 3 | 400 |
| 5 | 1,600 |
| 10 | 8,100 |
| 25 | 57,600 |

Higher levels unlock certain upgrades (each upgrade has a `requiredLevel` field).

---

## Arena Admin Commands

### `/guild arena`
Generates a test arena at reserved coordinates and teleports you to it.
- **Requires:** OP level 2
- Safe for testing — does not affect the production war arena

### `/guild arena edit`
Teleports you to the production arena without regenerating it.
- **Requires:** OP level 2
- Enables **custom arena mode** — future wars use your build instead of regenerating

### `/guild arena reset`
Regenerates the production arena with the default Roman Colosseum layout.
- **Requires:** OP level 2
- Disables custom arena mode

---

## See Also
- [Ranks & Permissions](Ranks-and-Permissions)
- [Bank & Economy](Bank-and-Economy)
- [Wars](Wars)
- [Upgrade System](Upgrade-System)
