# Datapack Guide

The upgrade system is entirely driven by datapack JSON files. You can create completely custom categories and upgrades without touching any code.

---

## File Structure

```
data/<namespace>/guild_upgrades/
    categories/
        <id>.json       ← one file per category
    upgrades/
        <id>.json       ← one file per upgrade
```

- **Namespace** can be anything (e.g., `mypack`, `myserver`)
- **Filename (without `.json`)** uppercased becomes the ID
  - `combat.json` → ID `COMBAT`
  - `damage_i.json` → ID `DAMAGE_I`

Install your datapack in `<world>/datapacks/` and run `/reload` to apply.

> ⚠️ **Built-in demo upgrades are removed** when any datapack is detected. If no datapack is present, the built-in demo data is used as a fallback.

---

## Category JSON

**Path:** `data/<ns>/guild_upgrades/categories/<id>.json`

```json
{
  "name": "Combat",
  "item": "minecraft:diamond_sword",
  "color": "RED",
  "slot": 0
}
```

| Field | Type | Description |
|---|---|---|
| `name` | String | Display name shown in the GUI tab |
| `item` | ResourceLocation | Item displayed as the tab icon |
| `color` | String | `ChatFormatting` name (see below) |
| `slot` | int (0–8) | Position in the top row of the GUI (0 = leftmost) |

**Rules:**
- Max **9 categories** (one per slot 0–8)
- No duplicate slot numbers
- All 9 slots are optional — leave slots empty to use fewer categories

**Valid `color` values:**
`BLACK`, `DARK_BLUE`, `DARK_GREEN`, `DARK_AQUA`, `DARK_RED`, `DARK_PURPLE`, `GOLD`, `GRAY`, `DARK_GRAY`, `BLUE`, `GREEN`, `AQUA`, `RED`, `LIGHT_PURPLE`, `YELLOW`, `WHITE`

---

## Upgrade JSON

**Path:** `data/<ns>/guild_upgrades/upgrades/<id>.json`

```json
{
  "category": "COMBAT",
  "displayName": "Damage Boost I",
  "description": "Increases damage dealt by guild members by 10%.",
  "icon": "minecraft:iron_sword",
  "cost": 500,
  "requiredLevel": 1,
  "prerequisite": "",
  "page": 1,
  "effects": [
    {
      "type": "MODIFIER",
      "key": "damage_multiplier",
      "value": 0.10
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `category` | String | ID of the parent category (uppercase, matches filename) |
| `displayName` | String | Name shown in GUI and `/guild upgrade info` |
| `description` | String | Lore text shown under the icon |
| `icon` | ResourceLocation | Item used as the upgrade icon |
| `cost` | int | Guild bank cost in coins |
| `requiredLevel` | int | Minimum guild level to purchase |
| `prerequisite` | String | ID of another upgrade that must be owned first (empty = none) |
| `page` | int (1+) | Which page this upgrade appears on within its category |
| `effects` | Array | List of effect objects (see below) |

---

## Effect Types

### `MODIFIER` — Passive stat multiplier
Stored and summed at runtime. Used for damage/defense/XP/earn-rate bonuses.

```json
{
  "type": "MODIFIER",
  "key": "damage_multiplier",
  "value": 0.10
}
```

**Built-in modifier keys used by the mod:**

| Key | Used For |
|---|---|
| `damage_multiplier` | % bonus outgoing damage |
| `defense_multiplier` | % damage reduction |
| `earn_rate_multiplier` | % bonus currency on earn |
| `xp_multiplier` | % bonus guild XP gain |

You can define your own keys and query them with `UpgradeRegistry.INSTANCE.sumModifier(guild, "your_key")`.

---

### `POTION_EFFECT` — Give a potion effect on purchase
One-time application at the moment of purchase.

```json
{
  "type": "POTION_EFFECT",
  "effect": "minecraft:strength",
  "amplifier": 1,
  "duration_ticks": 6000
}
```

| Field | Description |
|---|---|
| `effect` | Potion effect ResourceLocation |
| `amplifier` | Level (0 = Level I, 1 = Level II, …) |
| `duration_ticks` | Duration in game ticks (20 ticks = 1 second) |

---

### `GIVE_ITEM` — Give an item to the purchaser on purchase

```json
{
  "type": "GIVE_ITEM",
  "item": "minecraft:diamond",
  "count": 5
}
```

---

### `COMMAND` — Run a server command on purchase

```json
{
  "type": "COMMAND",
  "command": "give %player% minecraft:nether_star 1",
  "target": "player"
}
```

| Field | Description |
|---|---|
| `command` | Command string. `%player%` is replaced with the buyer's name, `%guild%` with the guild name |
| `target` | `"player"` (run as player) or `"server"` (run as server/console) |

---

## Full Example Datapack

Structure:
```
example_datapack/
  pack.mcmeta
  data/
    myserver/
      guild_upgrades/
        categories/
          magic.json
          warfare.json
        upgrades/
          arcane_shield.json
          siege_weapons.json
          siege_weapons_ii.json
```

**`pack.mcmeta`:**
```json
{
  "pack": {
    "pack_format": 15,
    "description": "MyServer Guild Upgrades"
  }
}
```

**`data/myserver/guild_upgrades/categories/magic.json`:**
```json
{
  "name": "Magic",
  "item": "minecraft:enchanted_book",
  "color": "AQUA",
  "slot": 0
}
```

**`data/myserver/guild_upgrades/upgrades/arcane_shield.json`:**
```json
{
  "category": "MAGIC",
  "displayName": "Arcane Shield",
  "description": "On purchase, grants all online members Resistance II for 5 minutes.",
  "icon": "minecraft:shield",
  "cost": 1000,
  "requiredLevel": 3,
  "prerequisite": "",
  "page": 1,
  "effects": [
    {
      "type": "POTION_EFFECT",
      "effect": "minecraft:resistance",
      "amplifier": 1,
      "duration_ticks": 6000
    }
  ]
}
```

---

## Validation & Errors

When a datapack loads, the mod validates all files. Errors are:
- Printed in server logs
- Shown in red to any OP (permission level ≥ 2) on join and after `/reload`

**Common errors:**
- Slot out of range (must be 0–8)
- Duplicate category slots
- Upgrade references a category ID that doesn't exist
- Malformed JSON

If validation fails, the mod falls back to the built-in demo data until the error is fixed.

---

## See Also
- [Upgrade System](Upgrade-System)
- [Configuration](Configuration)
