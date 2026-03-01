# Upgrade System

Guild upgrades are purchased from the guild bank and permanently unlock features or boost stats. The upgrade system is **fully datapack-driven** — see [Datapack Guide](Datapack-Guide) to add your own.

---

## Opening the Upgrade Shop

### GUI (recommended)
```
/guild upgrade
/guild upgrade <category>
```
Opens a 6-row chest interface.

**Layout:**

```
[Row 0]  Category tabs (up to 9, defined by datapacks)
[Row 1]  Header / page title
[Rows 2–4]  Up to 27 upgrades per page
[Row 5]  [Close] [◄ Prev] ... [Balance] ... [Next ►] [Close]
```

Click an upgrade icon to purchase it. Icons change color to show status:
- 🟢 **Emerald** — Already owned
- 🟡 **Item icon** — Available and affordable
- 🟠 **Orange dye** — Available but can't afford
- ⬜ **Gray dye** — Locked (missing level, prerequisite, or not owned)

### CLI
```
/guild upgrade info <upgradeId>
/guild upgrade buy <upgradeId>
```

---

## Built-in Demo Upgrades

These upgrades are included when no datapack is installed. Installing a datapack replaces all demo data.

### Combat
| ID | Cost | Req. Level | Effect |
|---|---|---|---|
| `DAMAGE_I` | 500 | 1 | +10% damage dealt |
| `DAMAGE_II` | 1,500 | 5 | +20% damage dealt *(requires DAMAGE_I)* |
| `DEFENSE_I` | 500 | 1 | +10% damage reduction |
| `DEFENSE_II` | 1,500 | 5 | +20% damage reduction *(requires DEFENSE_I)* |
| `ARENA_WAR` | 2,000 | 3 | Enables arena mode for guild wars |

### Utility
| ID | Cost | Req. Level | Effect |
|---|---|---|---|
| `MEMBER_CAP_I` | 300 | 1 | +5 max members |
| `MEMBER_CAP_II` | 800 | 3 | +10 max members *(requires MEMBER_CAP_I)* |
| `CHUNK_CLAIM_I` | 400 | 1 | +10 FTB chunk claims |
| `CHUNK_FORCE_LOAD` | 1,000 | 5 | +2 FTB force-loaded chunks *(requires CHUNK_CLAIM_I)* |
| `HOME_BYPASS` | 600 | 2 | Remove guild home cooldown |

### Economy
| ID | Cost | Req. Level | Effect |
|---|---|---|---|
| `EARN_RATE_I` | 500 | 1 | +25% currency earned per kill/login |
| `EARN_RATE_II` | 1,200 | 4 | +50% currency earned *(requires EARN_RATE_I)* |
| `BANK_CAP_I` | 800 | 2 | Bank cap ×2 |
| `BANK_CAP_II` | 2,000 | 6 | Bank cap ×3 *(requires BANK_CAP_I)* |
| `WAR_SHIELD` | 1,500 | 4 | Temporary war shield (prevents being declared on) |

### Defense
| ID | Cost | Req. Level | Effect |
|---|---|---|---|
| `XP_BONUS_I` | 400 | 1 | +25% guild XP gain |
| `XP_BONUS_II` | 1,000 | 3 | +50% guild XP gain *(requires XP_BONUS_I)* |
| `REDUCED_LOSS` | 1,200 | 5 | Pay only 75% of wager when losing a war |

### Arcane
| ID | Cost | Req. Level | Effect |
|---|---|---|---|
| `ALLIANCE` | 1,000 | 3 | Unlocks the alliance system |
| `CUSTOM_CHAT` | 800 | 2 | Custom guild chat color (synced from FTB team) |

---

## Upgrade Effects

Effects run when an upgrade is **purchased**. For stat modifiers (e.g., +10% damage), the effect is permanent and passive. See [Datapack Guide](Datapack-Guide) for all effect types.

---

## Commands

### `/guild upgrade`
Opens the upgrade GUI starting at the first category.

### `/guild upgrade <category>`
Opens the GUI at a specific category (tab name).

### `/guild upgrade info <upgradeId>`
Prints upgrade details to chat: cost, required level, description, prerequisite, and status (Owned / Available / Locked).

### `/guild upgrade buy <upgradeId>`
Purchases an upgrade from the bank via command.
- **Requires:** `MANAGE_UPGRADES` permission

---

## See Also
- [Datapack Guide](Datapack-Guide) — Create custom upgrade categories and upgrades
- [Bank & Economy](Bank-and-Economy) — How bank costs work
- [Wars](Wars) — Upgrades that affect war behavior
