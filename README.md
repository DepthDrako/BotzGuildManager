# BotzGuildz

> An in-depth guild system for Minecraft Forge servers — create guilds, manage ranks, level up, go to war, run a player economy, and more.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Forge](https://img.shields.io/badge/Forge-47.4.16-orange)
![Version](https://img.shields.io/badge/Version-1.5.2-blue)
![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red)

---

## Features

| Feature | Description |
|---|---|
| 🏰 **Guilds** | Create and manage guilds with custom names, tags, and messages |
| 🎖️ **Custom Ranks** | Build unlimited rank tiers with fine-grained permission control |
| 💰 **Economy** | Personal bank, guild treasury, auction house, player/guild shops, and bounties |
| 🪙 **Currency** | Tiered denomination system (GB → GCh → GT → GC → GM → GS); Numismatics support |
| 🎨 **Fantasy GUIs** | All economy and guild chest menus use a custom midnight-blue/gold fantasy theme |
| ⚔️ **Guild Wars** | Open-world or arena PvP battles with currency wagers and live tracking |
| 🤝 **Alliances** | Mutual partnerships with shared officer chat and ally teleport |
| 🧪 **Upgrade System** | Datapack-driven upgrades that unlock features and boost stats |
| 🏟️ **Arena Dimension** | Dedicated `guild_arena` dimension with a generated Roman Colosseum |
| ⚔️ **Duels** | 1v1 PvP with optional wagers and a global leaderboard |
| 🐉 **Raid Parties** | Form a raid party, fight bosses together, and split loot by damage dealt |
| 📋 **Missions** | Daily shared guild quests (Kill / Mine / Collect) with currency and XP rewards |
| 🗃️ **Guild Vault** | Shared item storage with permission-gated withdrawals |
| 📊 **Leaderboards** | Guild and player rankings by wealth, war wins, and duel wins |
| 🔗 **FTB Integration** | Chunk-claim upgrades sync automatically with FTB Chunks |

---

## Quick Start

1. Install the mod in your `mods/` folder alongside Forge 47.4.16 for MC 1.20.1
2. Start your server — a `botzguildz-server.toml` config will be generated
3. Create your first guild: `/guild create MyGuild MG`
4. Invite friends: `/guild invite PlayerName`
5. Open the upgrade shop: `/guild upgrade`

---

## Commands Overview

### Guild
| Command | Description |
|---|---|
| `/guild` | Core guild management |
| `/guild rank` | Rank & permission management |
| `/guild bank` | Shared bank operations |
| `/guild ally` | Alliance management |
| `/guild war` | War declaration and tracking |
| `/guild upgrade` | Upgrade shop (GUI) |
| `/guild missions` | Daily guild quests |
| `/guild vault` | Shared item storage |
| `/guild contributions` | View member contribution points |
| `/guild bounty` | Guild item-collection bounty board |
| `/guild shop` | Guild storefront management |
| `/guild top` | Guild leaderboards |
| `/guild arena` | Arena admin tools *(OP only)* |

### Economy
| Command | Description |
|---|---|
| `/bank` | Personal bank GUI — deposit, withdraw, pay |
| `/ah` | Auction House — browse, list, bid on items |
| `/bounty` | Place and collect player bounties |
| `/shop` | Player storefront management |

### Combat
| Command | Description |
|---|---|
| `/duel` | 1v1 duel system |
| `/raid party` | Raid party management |

---

## Wiki

Full documentation is available in the [Wiki](../../wiki):

- [Home](../../wiki/Home)
- [Guild Commands](../../wiki/Guild-Commands)
- [Ranks & Permissions](../../wiki/Ranks-and-Permissions)
- [Bank & Economy](../../wiki/Bank-and-Economy)
- [Auction House](../../wiki/Auction-House)
- [Bounties](../../wiki/Bounties)
- [Shops](../../wiki/Shops)
- [Upgrade System](../../wiki/Upgrade-System)
- [Datapack Guide](../../wiki/Datapack-Guide)
- [Missions](../../wiki/Missions)
- [Wars](../../wiki/Wars)
- [Alliances](../../wiki/Alliances)
- [Duels](../../wiki/Duels)
- [Raid System](../../wiki/Raid-System)
- [Leaderboards](../../wiki/Leaderboards)
- [FTB Integration](../../wiki/FTB-Integration)
- [Configuration](../../wiki/Configuration)

---

## Requirements

- Minecraft **1.20.1**
- Minecraft Forge **47.4.16+**
- Java **17+**
- *(Optional)* Create: Numismatics — for coin-based currency
- *(Optional)* FTB Chunks — for chunk-claim upgrade integration
- *(Optional)* FTB Teams — for team color sync
- *(Optional)* FTB Quests — for quest integration

---

## Building from Source

```bash
./gradlew build
```

Output JAR: `build/libs/botzguildz-1.5.2.jar`

If you get a "stale outputs" error (e.g. the dev client is running), do a clean build:

```bash
rm -rf build && ./gradlew build
```

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full version history.

---

*Made by Botz*
