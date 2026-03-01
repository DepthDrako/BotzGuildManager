# Raid System

The raid system lets players form a party, fight boss-tier mobs together, and receive proportional loot based on how much damage each person dealt. It works automatically with **any mob that has 50+ hearts (100 HP) of max health** — no configuration needed.

---

## Quick Start

1. One player creates a party: `/raid party create`
2. Invite your friends: `/raid party invite <player>`
3. They accept: `/raid party accept`
4. Go find a boss together and start hitting it
5. Boss dies → loot automatically split by damage contribution

---

## Commands

### `/raid party create`
Creates a new raid party. You become the **party leader**.
- You must not already be in a party

### `/raid party invite <player>`
Invites an online player to your party.
- **Requires:** Party leader
- Invite expires after **60 seconds** if not accepted
- Target must not already be in a party

### `/raid party accept`
Joins a party you've been invited to.
- You must have a valid, non-expired invite

### `/raid party decline`
Declines a pending party invite.
- The party leader is notified

### `/raid party leave`
Leaves your current party.
- If the **leader** leaves, the party is automatically disbanded

### `/raid party disband`
Disbands the entire party.
- **Requires:** Party leader
- All members are notified

### `/raid party kick <player>`
Removes a player from the party.
- **Requires:** Party leader
- The kicked player receives a notification

### `/raid party status`
Shows the party roster with online/offline indicators.
- During an active boss fight, shows each member's **damage dealt and percentage share**

---

## Boss Detection

A mob is treated as a "boss" if its **maximum health is ≥ 100 HP (50 hearts)**. This automatically includes:

- Vanilla bosses: Ender Dragon, Wither, Elder Guardian
- Any modded mob tuned to 50+ hearts

Non-boss mobs (e.g., zombies, skeletons) are completely ignored — hitting them with a party does nothing special.

---

## How Fight Claiming Works

When any party member deals the **first qualifying hit** on a boss:
- That party "claims" the boss
- A "⚔ Raid fight started!" message is broadcast to all party members
- All subsequent hits on that boss by party members are tracked

**Only one party can claim a boss.** If a second party tries to hit the same boss, their damage is ignored and that boss's drops will go to the claiming party.

**A party can only fight one boss at a time.** If your party is already engaged with a boss, hitting a different boss-tier mob does nothing until your current fight ends.

---

## Loot Distribution

When the boss dies:

1. Normal drops are **cancelled** (no items fall on the ground from the boss)
2. All drops are collected and split by damage percentage
3. Each player's share is sent **directly to their inventory** (overflow drops near them)
4. If a player is offline, their share drops at the boss's location
5. Each participant receives a summary message:

```
⚔ Raid complete! Wither | Your damage: 340.2 (42.5%) | Items received: 3
```

### Split Algorithm

For each item stack:
- Each player receives `floor(count × share%)`
- Any remainder items (from rounding) go to the **highest damage dealer**

**Example:** 5 nether stars drop, 3 players with 50%/30%/20% share:
- Player A (50%): floor(5 × 0.50) = 2 stars + 1 remainder = **3 stars**
- Player B (30%): floor(5 × 0.30) = 1 star
- Player C (20%): floor(5 × 0.20) = 1 star

### Solo Party Member

If only **1 party member** actually deals damage to the boss (everyone else missed or left), **normal drops occur** — the loot redistribution system does not activate for solo fights.

---

## Party Persistence

- Players **stay in their party** after logging out and back in
- Damage is still tracked if they re-join the fight before the boss dies
- If the leader logs out, they remain leader — other members cannot promote themselves

---

## Tips

- Coordinate who joins before you start attacking — only hits after the party is formed get counted
- A higher-damage player dealing 90% of the work gets 90% of the drops — this is intentional
- Use `/raid party status` during a fight to see live damage numbers
- Parties persist between fights — you don't need to recreate the party for every boss

---

## See Also
- [Wars](Wars) — Guild-level combat
- [Duels](Duels) — 1v1 combat
