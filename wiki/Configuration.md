# Configuration

All settings are in `botzguildz-server.toml`, generated automatically in your server's config directory on first launch.

---

## Guild Settings

| Key | Default | Range | Description |
|---|---|---|---|
| `MAX_GUILD_NAME_LENGTH` | `32` | 3–64 | Maximum character length for guild names |
| `MAX_GUILD_TAG_LENGTH` | `6` | 2–10 | Maximum character length for guild tags |
| `BASE_MAX_MEMBERS` | `10` | 1–500 | Base member cap before upgrade bonuses |
| `MAX_GUILD_LEVEL` | `25` | 5–100 | Guild level cap |
| `HOME_COOLDOWN_SECONDS` | `30` | 0–3600 | Cooldown between `/guild home` uses (0 = no cooldown) |

---

## Economy

| Key | Default | Range | Description |
|---|---|---|---|
| `CURRENCY_NAME` | `"Guild Coins"` | — | Display name for the currency |
| `CURRENCY_SYMBOL` | `"GC"` | — | Short symbol used in the UI |
| `CURRENCY_ITEM` | `"minecraft:gold_ingot"` | — | Item that represents 1 coin |
| `MAX_BANK_BALANCE` | `1000000` | 100–max | Maximum guild bank balance |
| `CURRENCY_PER_KILL` | `10` | 0–10000 | Coins earned per player kill |
| `CURRENCY_PER_LOGIN` | `5` | 0–10000 | Coins earned per member login |

---

## XP & Leveling

| Key | Default | Range | Description |
|---|---|---|---|
| `XP_PER_KILL` | `15` | 0–100000 | Guild XP per player kill by a member |
| `XP_PER_MEMBER_LOGIN` | `5` | 0–100000 | Guild XP per member login |
| `XP_PER_WAR_WIN` | `500` | 0–100000 | Guild XP per war win |

---

## Wars

| Key | Default | Range | Description |
|---|---|---|---|
| `WAR_ACCEPT_TIMEOUT_SECONDS` | `120` | 10–600 | Time the defending guild has to accept/deny |
| `WAR_COOLDOWN_MINUTES` | `60` | 0–10080 | Cooldown between wars for a guild |
| `ARENA_WAR_DURATION_SECONDS` | `300` | 30–3600 | Duration of arena wars |
| `ARENA_SIZE` | `40` | 10–200 | Radius of the generated arena |

---

## Duels

| Key | Default | Range | Description |
|---|---|---|---|
| `DUEL_TIMEOUT_SECONDS` | `30` | 5–300 | Time to accept/deny a duel challenge |
| `DUEL_RADIUS_BLOCKS` | `20` | 5–100 | Max movement before duel auto-forfeits |

---

## Alliances

| Key | Default | Range | Description |
|---|---|---|---|
| `MAX_ALLIANCES` | `3` | 1–20 | Maximum alliances per guild |

---

## Example Config

```toml
[guilds]
    MAX_GUILD_NAME_LENGTH = 32
    MAX_GUILD_TAG_LENGTH = 6
    BASE_MAX_MEMBERS = 15
    MAX_GUILD_LEVEL = 30
    HOME_COOLDOWN_SECONDS = 60

[economy]
    CURRENCY_NAME = "Gold Coins"
    CURRENCY_SYMBOL = "G"
    CURRENCY_ITEM = "minecraft:gold_ingot"
    MAX_BANK_BALANCE = 500000
    CURRENCY_PER_KILL = 25
    CURRENCY_PER_LOGIN = 10

[xp]
    XP_PER_KILL = 20
    XP_PER_MEMBER_LOGIN = 10
    XP_PER_WAR_WIN = 1000

[wars]
    WAR_ACCEPT_TIMEOUT_SECONDS = 180
    WAR_COOLDOWN_MINUTES = 120
    ARENA_WAR_DURATION_SECONDS = 600
    ARENA_SIZE = 60

[duels]
    DUEL_TIMEOUT_SECONDS = 45
    DUEL_RADIUS_BLOCKS = 30

[alliances]
    MAX_ALLIANCES = 5
```

---

## See Also
- [Upgrade System](Upgrade-System) — Runtime upgrades that override some caps (bank, member count)
- [Datapack Guide](Datapack-Guide) — Upgrade JSON configuration
