# BotzGuildz Wiki

Welcome to the BotzGuildz wiki! This mod adds a full guild system to your Minecraft Forge 1.20.1 server.

---

## Pages

### Core Systems
- **[Guild Commands](Guild-Commands)** — Create, manage, and interact with guilds
- **[Ranks & Permissions](Ranks-and-Permissions)** — Custom rank tiers and permission nodes
- **[Bank & Economy](Bank-and-Economy)** — Shared guild bank and currency integration

### Combat
- **[Wars](Wars)** — Guild vs guild PvP with wagers and arena battles
- **[Duels](Duels)** — 1v1 PvP combat with optional currency stakes
- **[Raid System](Raid-System)** — Party up to fight bosses and split loot by damage

### Progression
- **[Upgrade System](Upgrade-System)** — Datapack-driven upgrade shop
- **[Datapack Guide](Datapack-Guide)** — Create your own custom upgrades with JSON
- **[Leaderboards](Leaderboards)** — Guild and player rankings

### Integrations & Other
- **[Alliances](Alliances)** — Mutual partnerships between guilds
- **[FTB Integration](FTB-Integration)** — FTB Teams/Chunks/Quests sync and chunk claim upgrades
- **[Configuration](Configuration)** — Server config reference (`botzguildz-server.toml`)

---

## Requirements

| Requirement | Version |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.16+ |
| Java | 17+ |
| FTB Teams *(optional)* | Any compatible version |
| FTB Chunks *(optional)* | Any compatible version |
| FTB Quests *(optional)* | Any compatible version |

---

## Key Concepts

**Guilds** are persistent groups tied to a server. Each player can be in one guild at a time. Guilds have:
- A **Name** and a short **Tag** shown in chat
- A **Level** that increases with XP (earned through logins, kills, and war wins)
- A **Bank** for shared currency
- A **Home** teleport location
- **Ranks** with customizable permissions
- **Upgrades** that unlock gameplay features

**Upgrades** are purchased from the guild bank and persist forever. Many upgrades are required to unlock other features (e.g., the `ALLIANCE` upgrade is required before `/guild ally` works).

**Wars** are declared between guilds and can be fought in the open world or in the dedicated arena dimension. The loser's bank wager is transferred to the winner.

**Raids** are separate from guild wars — any party of players can form a raid group and fight boss-tier mobs, with drops split proportionally based on damage dealt.
