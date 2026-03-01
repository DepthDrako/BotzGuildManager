# Duels

Duels are 1v1 PvP matches between any two players, with an optional currency wager and configurable lives. No guild membership required.

---

## Commands

### `/duel <player> [wager] [lives]`
Challenges another player to a duel.

| Argument | Default | Description |
|---|---|---|
| `player` | *(required)* | Target player (must be online) |
| `wager` | 0 | Currency per player; 0 = no wager |
| `lives` | 1 | How many deaths each player is allowed |

The challenged player has **30 seconds** (configurable) to accept or deny.

**Example:** `/duel Steve 500 3` — challenge Steve with a 500-coin wager, 3 lives each.

### `/duel accept`
Accepts a pending duel challenge.
- Both players' wagers are deducted from their wallets immediately
- The duel begins — kill/death tracking starts

### `/duel deny`
Rejects a pending duel challenge. No coins are deducted.

### `/duel cancel`
Cancels a duel challenge **you** sent (challenger only).

### `/duel status`
Shows the current duel state:
- Challenger vs challenged
- Wager per player and total pot
- Kills and lives remaining for both players

### `/duel top`
Shows the top 10 players by total duel wins (includes offline players).

---

## How It Works

1. Player A uses `/duel PlayerB 100 2` — both players are notified
2. Player B has 30 seconds to `/duel accept` or `/duel deny`
3. If accepted, 100 coins are deducted from each player's wallet
4. The duel is live — each kill counts until someone runs out of lives
5. The winner receives the full pot (200 coins in this example)

**Radius enforcement:** Players must stay within a set radius of where they started (default: 20 blocks). Moving too far auto-forfeits the duel.

---

## Economy

| Event | Effect |
|---|---|
| Duel accepted (with wager) | Both wallets deducted |
| Duel won | Winner receives full pot |
| Duel forfeit / flee | Forfeiting player loses their wager |
| Duel denied | No coins deducted |

---

## Configuration

| Setting | Default | Description |
|---|---|---|
| `DUEL_TIMEOUT_SECONDS` | 30 | Time to accept/deny a challenge |
| `DUEL_RADIUS_BLOCKS` | 20 | Max distance before auto-forfeit |

---

## See Also
- [Leaderboards](Leaderboards)
- [Bank & Economy](Bank-and-Economy)
- [Configuration](Configuration)
