# Guild Wars

Guild wars are large-scale PvP conflicts between two guilds, with currency wagers, kill tracking, and two battle modes.

---

## Overview

1. One guild **declares** war on another with a wager
2. The defending guild has 120 seconds to **accept or deny**
3. If accepted, the war begins — kill tracking starts immediately
4. The war ends when one guild's lives run out (or time expires for arena wars)
5. The winner receives both guilds' wagers from escrow

---

## Requirements

- Both guilds must not currently be in a war
- Neither guild can be on war cooldown
- Neither guild can have an active **War Shield** upgrade
- The declaring guild's bank must have enough available balance to cover the wager

---

## War Commands

### `/guild war declare <targetGuild> <wager>`
Declares war on another guild.
- **Requires:** `DECLARE_WAR` permission
- Wager is escrowed from your bank immediately
- Target guild receives a notification with 120 seconds to respond

### `/guild war accept <lives> [mode]`
Accepts a war declaration.
- **Requires:** `DECLARE_WAR` permission
- `lives` — number of deaths allowed per player (1–999)
- `mode` — `openworld` (default) or `arena`
  - Arena mode requires **both** guilds to own the `ARENA_WAR` upgrade

### `/guild war deny`
Rejects a war declaration. The declaring guild's wager is fully refunded.

### `/guild war status`
Shows current war state: mode, lives remaining, wager, time (arena only), and kill counts for both guilds.

### `/guild war forfeit`
Ends the war immediately with your guild as the loser.
- **Requires:** `DECLARE_WAR` permission
- Only usable during an active war
- If you own the `REDUCED_LOSS` upgrade, you keep 25% of your wager (pay 75%)

---

## Battle Modes

### Open World
- No time limit
- Combat happens anywhere in the world
- Members of both guilds can damage each other regardless of normal friendly fire settings
- War ends when total kill count reaches the lives threshold

### Arena
- Both guilds are teleported into the dedicated `guild_arena` dimension
- Lasts 300 seconds by default (configurable)
- The guild with more kills when time expires wins
- If both guilds hit zero lives simultaneously, the war is a draw (wagers returned)
- After the war, all participants are returned to their pre-war positions

---

## Win Conditions

| Condition | Winner |
|---|---|
| Opponent's total lives depleted | Your guild |
| Arena timer expires, you have more kills | Your guild |
| Opponent forfeits | Your guild |
| Draw (equal kills or simultaneous zero lives) | Wagers returned |

---

## Economy

| Event | Effect |
|---|---|
| War declared | Both wagers escrowed |
| War won | Winner receives both wagers |
| War lost | Loser's wager transferred to winner |
| Forfeit (no upgrade) | Loser pays full wager |
| Forfeit (`REDUCED_LOSS` upgrade) | Loser keeps 25%, winner gets 75% |
| War denied | Declaring guild's wager fully refunded |

---

## Relevant Upgrades

| Upgrade | Effect |
|---|---|
| `ARENA_WAR` | Enables arena mode when accepting a war |
| `WAR_SHIELD` | Prevents your guild from being declared on (temporary) |
| `REDUCED_LOSS` | Pay only 75% of the wager when losing or forfeiting |
| `DAMAGE_I` / `DAMAGE_II` | Bonus damage to enemies during war |
| `DEFENSE_I` / `DEFENSE_II` | Damage reduction during war |

---

## Cooldowns & Limits

| Setting | Default |
|---|---|
| War accept timeout | 120 seconds |
| War cooldown between wars | 60 minutes |
| Arena war duration | 300 seconds |

All configurable in `botzguildz-server.toml`. See [Configuration](Configuration).

---

## Arena

The arena is a Roman Colosseum generated in the dedicated `guild_arena` dimension. Server admins can:
- Regenerate it with `/guild arena reset`
- Customize it with `/guild arena edit` (enables custom arena mode)

See [Guild Commands](Guild-Commands#arena-admin-commands) for details.

---

## See Also
- [Upgrade System](Upgrade-System)
- [Bank & Economy](Bank-and-Economy)
- [Configuration](Configuration)
