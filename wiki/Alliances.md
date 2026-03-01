# Alliances

Alliances are mutual partnerships between guilds. Allied guilds cannot hurt each other and can see each other in the guild info.

---

## Requirements

- Your guild must own the **`ALLIANCE`** upgrade (Arcane category)
- Both guilds must be under the alliance cap (default: 3 alliances per guild)

---

## Commands

### `/guild ally list`
Lists all guilds currently allied with yours, showing their tags.

### `/guild ally invite <guildName>`
Sends an alliance proposal to another guild.
- **Requires:** `MANAGE_ALLIES` permission
- The target guild's leader/officers will receive a notification

### `/guild ally accept <guildName>`
Accepts a pending alliance invitation from a guild.
- **Requires:** `MANAGE_ALLIES` permission
- Both guilds immediately become allies

### `/guild ally deny <guildName>`
Rejects an alliance invitation.

### `/guild ally break <guildName>`
Dissolves an existing alliance.
- **Requires:** `MANAGE_ALLIES` permission
- Both guilds lose the alliance relationship immediately
- No cooldown or notification delay

---

## Alliance Cap

By default, each guild can have a maximum of **3 alliances** at a time. This is configurable:

```toml
# botzguildz-server.toml
MAX_ALLIANCES = 3
```

---

## See Also
- [Upgrade System](Upgrade-System) — The `ALLIANCE` upgrade
- [Configuration](Configuration)
