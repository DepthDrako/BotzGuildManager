# FTB Integration

BotzGuildz has a built-in soft-dependency bridge for the FTB mod suite. All three integrations are **optional** — the mod works fine without any of them installed. Everything is handled via reflection so no FTB mod is required at launch.

---

## What's Integrated

| FTB Mod | Required? | What it does |
|---|---|---|
| **FTB Teams** | No | Each guild automatically gets its own FTB party team. Membership is kept in sync. |
| **FTB Chunks** | No | Chunk claim and force-load quotas are raised when chunk upgrades are purchased. |
| **FTB Quests** | No | No extra code needed — quest progress is tracked per FTB party, so guild members share quest progress automatically once they share the same FTB team. |

The server log will tell you at startup which integrations loaded successfully:

```
[BotzGuildz] FTB Bridge — Teams=true, Chunks=true, Quests=true
```

---

## FTB Teams — Guild Sync

When a guild is **created**, the mod automatically creates a matching FTB party team named after the guild. From that point on, guild membership changes are mirrored to the FTB party in real time:

| Guild Event | FTB Effect |
|---|---|
| Player joins guild | Added to the FTB party |
| Player leaves / is kicked | Removed from the FTB party |
| Guild is disbanded | FTB party is deleted |
| Player logs in | Verified to be in the correct FTB party (auto-corrects drift) |

The FTB team's **colour** is also synced to the guild's chat colour on every login — so if an admin changes the team colour in FTB Teams, it will be reflected in guild chat automatically.

> ℹ️ If FTB Teams is installed **after** guilds already exist, the FTB party is created the next time each guild's leader logs in.

---

## FTB Chunks — Chunk Claims

Chunk claims are granted to the guild's FTB party by purchasing upgrades. The claim bonus is applied **immediately on purchase** and re-applied automatically on server start to restore saved state.

### Built-in Chunk Upgrades

| Upgrade ID | Extra Claims | Extra Force-Loads |
|---|---|---|
| `CHUNK_CLAIM_I` | +10 | — |
| `CHUNK_CLAIM_II` | +10 *(+20 cumulative)* | — |
| `CHUNK_FORCE_LOAD` | — | +10 |

> `CHUNK_CLAIM_II` stacks with `CHUNK_CLAIM_I` — owning both grants a total of **+20 extra claim slots**. `CHUNK_FORCE_LOAD` requires `CHUNK_CLAIM_I` as a prerequisite in the demo data.

Members claim chunks through FTB Chunks as normal (right-click with the chunk claiming tool). The extra slots provided by guild upgrades are shared across the entire guild FTB party.

---

## Adding Chunk Upgrades in a Datapack

The FTB chunk quota is triggered by **specific upgrade IDs**. When a player purchases an upgrade whose ID is one of `CHUNK_CLAIM_I`, `CHUNK_CLAIM_II`, or `CHUNK_FORCE_LOAD`, the mod automatically calls `applyChunkClaimBonus()` and adjusts the FTB Chunks team data.

### Option A — Reuse the Built-in IDs *(Recommended)*

The simplest approach: define upgrades in your datapack using the exact same IDs. Since the mod has no built-in JSON files of its own, your datapack fully owns those IDs.

**`data/myserver/guild_upgrades/upgrades/chunk_claim_i.json`**
```json
{
  "category": "UTILITY",
  "displayName": "Chunk Claims I",
  "description": "Grants your guild 10 extra FTB chunk claim slots.",
  "icon": "minecraft:grass_block",
  "cost": 500,
  "requiredLevel": 1,
  "prerequisite": "",
  "page": 1,
  "effects": [
    {
      "type": "MODIFIER",
      "key": "chunk_claim_bonus",
      "value": 10
    }
  ]
}
```

> The `MODIFIER` effect here is optional — it's just for display/tracking purposes. The actual FTB quota change happens automatically when the ID matches.

**`data/myserver/guild_upgrades/upgrades/chunk_claim_ii.json`**
```json
{
  "category": "UTILITY",
  "displayName": "Chunk Claims II",
  "description": "Grants your guild 10 more FTB chunk claim slots (20 total).",
  "icon": "minecraft:dirt",
  "cost": 1200,
  "requiredLevel": 3,
  "prerequisite": "CHUNK_CLAIM_I",
  "page": 1,
  "effects": []
}
```

**`data/myserver/guild_upgrades/upgrades/chunk_force_load.json`**
```json
{
  "category": "UTILITY",
  "displayName": "Force Loading",
  "description": "Grants your guild 10 FTB force-load slots to keep chunks loaded 24/7.",
  "icon": "minecraft:redstone_torch",
  "cost": 1000,
  "requiredLevel": 5,
  "prerequisite": "CHUNK_CLAIM_I",
  "page": 2,
  "effects": []
}
```

> **Filenames are case-insensitive when matched to IDs.** `chunk_claim_i.json` → ID `CHUNK_CLAIM_I`. ✅

---

### Option B — Custom ID + COMMAND Effect

If you want a **different upgrade ID** (e.g., `LAND_EXPANSION_I`) and are running a server with a chunk-claim command (like from another plugin or mod), you can use the `COMMAND` effect type instead:

```json
{
  "category": "UTILITY",
  "displayName": "Land Expansion I",
  "description": "Grants your guild 10 extra chunk claim slots.",
  "icon": "minecraft:grass_block",
  "cost": 500,
  "requiredLevel": 1,
  "prerequisite": "",
  "page": 1,
  "effects": [
    {
      "type": "COMMAND",
      "command": "ftbchunks admin extra_claim_chunks %guild% 10",
      "target": "server"
    }
  ]
}
```

> ⚠️ With a custom ID the native FTB quota bridge **will not fire** — you must handle it entirely through the `COMMAND` effect. The exact command syntax depends on what version of FTB Chunks you have installed.

---

## How Quota Values Work

The mod sets **extra** claim/force-load slots on the FTB Chunks team data — it does not override the base quota set by FTB Chunks itself. The final quota a guild sees is:

```
Total claims = FTBChunks base quota + guild upgrade bonus
```

The bonus values are **re-applied on server start** to make sure they survive restarts. The values set are always the **cumulative total** — owning both `CHUNK_CLAIM_I` and `CHUNK_CLAIM_II` sets extra claims to `20`, not `10 + 10` applied twice.

---

## Troubleshooting

**"FTB Bridge — Teams=false" on startup**
- FTB Teams is not installed, or the FTB Teams API class couldn't be found. The mod will still work — chunk upgrades and guild features work independently.

**Chunk quotas not updating after purchase**
- Verify FTB Teams is installed (the bridge needs both Teams + Chunks to locate the correct team data).
- Check that the upgrade ID exactly matches `CHUNK_CLAIM_I`, `CHUNK_CLAIM_II`, or `CHUNK_FORCE_LOAD`.
- Run `/reload` and re-purchase — the quota is applied at purchase time and at server start.

**Members not appearing in the FTB party**
- Have each member log out and log back in. The sync check runs on every login.
- If the guild was created before FTB Teams was installed, the leader must log in first to trigger FTB party creation, then each member must log in to be added.

**Chat colour not syncing from FTB Teams**
- Colour sync happens on login. Have the player relog after changing the team colour in FTB Teams.

---

## See Also
- [Upgrade System](Upgrade-System)
- [Datapack Guide](Datapack-Guide)
- [Configuration](Configuration)
