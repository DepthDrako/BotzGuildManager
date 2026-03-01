# BotzGuildz

> An in-depth guild system for Minecraft Forge servers — create guilds, manage ranks, level up, go to war, and more.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Forge](https://img.shields.io/badge/Forge-47.4.16-orange)
![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red)

---

## Features

| Feature | Description |
|---|---|
| 🏰 **Guilds** | Create and manage guilds with custom names, tags, and messages |
| 🎖️ **Custom Ranks** | Build unlimited rank tiers with fine-grained permission control |
| 💰 **Guild Bank** | Shared currency pool with configurable caps and war escrow |
| ⚔️ **Guild Wars** | Open-world or arena PvP battles with currency wagers and live tracking |
| 🤝 **Alliances** | Mutual partnerships between guilds |
| 🧪 **Upgrade System** | Datapack-driven upgrades that unlock features and boost stats |
| 🏟️ **Arena Dimension** | Dedicated `guild_arena` dimension with a generated Roman Colosseum |
| ⚔️ **Duels** | 1v1 PvP with optional wagers and a global leaderboard |
| 🐉 **Raid Parties** | Form a raid party, fight bosses together, and split loot by damage dealt |
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

| Command | Description |
|---|---|
| `/guild` | Core guild management |
| `/guild rank` | Rank & permission management |
| `/guild bank` | Shared bank operations |
| `/guild ally` | Alliance management |
| `/guild war` | War declaration and tracking |
| `/guild upgrade` | Upgrade shop (GUI + CLI) |
| `/guild top` | Guild leaderboards |
| `/guild arena` | Arena admin tools *(OP only)* |
| `/duel` | 1v1 duel system |
| `/raid party` | Raid party management |

---

## Wiki

Full documentation is available in the [Wiki](../../wiki):

- [Home](../../wiki/Home)
- [Guild Commands](../../wiki/Guild-Commands)
- [Ranks & Permissions](../../wiki/Ranks-and-Permissions)
- [Bank & Economy](../../wiki/Bank-and-Economy)
- [Upgrade System](../../wiki/Upgrade-System)
- [Datapack Guide](../../wiki/Datapack-Guide)
- [Wars](../../wiki/Wars)
- [Alliances](../../wiki/Alliances)
- [Duels](../../wiki/Duels)
- [Raid System](../../wiki/Raid-System)
- [Leaderboards](../../wiki/Leaderboards)
- [Configuration](../../wiki/Configuration)

---

## Requirements

- Minecraft **1.20.1**
- Minecraft Forge **47.4.16+**
- Java **17+**
- *(Optional)* FTB Chunks — for chunk-claim upgrade integration

---

## Building from Source

```bash
./gradlew build
```

Output JAR: `build/libs/botzguildz-1.0.0.jar`

---

*Made by Botz*
