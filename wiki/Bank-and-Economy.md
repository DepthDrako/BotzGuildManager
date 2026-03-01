# Bank & Economy

The guild bank is a shared currency pool that funds upgrades, wars, and alliances.

---

## Currency

By default the mod uses a built-in currency system. If **Numismatics** is installed on the server it will use that mod's currency instead.

| Config Key | Default | Description |
|---|---|---|
| `CURRENCY_NAME` | `"Guild Coins"` | Display name used in messages |
| `CURRENCY_SYMBOL` | `"GC"` | Short symbol used in UI |
| `CURRENCY_ITEM` | `minecraft:gold_ingot` | Physical item that represents 1 coin |
| `MAX_BANK_BALANCE` | 1,000,000 | Maximum coins the bank can hold |

---

## Earning Currency

Players earn currency automatically:

| Source | Default Amount |
|---|---|
| Logging in | 5 coins |
| Killing a player | 10 coins (to killer) + XP for guild |

These values are set in `botzguildz-server.toml`.

---

## Bank Commands

### `/guild bank balance`
Shows three values:
- **Total balance** — all coins in the bank
- **Escrowed** — coins locked for an active war wager (cannot be withdrawn)
- **Available** — total minus escrowed (usable balance)

### `/guild bank deposit <amount>`
Deposits coins from your personal wallet into the guild bank.
- Fails if the deposit would exceed `MAX_BANK_BALANCE`
- Logged in the guild activity log

### `/guild bank deposit all`
Deposits everything you have up to the bank cap. Any excess stays with you.

### `/guild bank withdraw <amount>`
Withdraws coins from the guild bank to your wallet.
- **Requires:** `MANAGE_BANK` permission
- Cannot withdraw escrowed (war-locked) funds

---

## Bank Cap Upgrades

The bank cap can be increased by purchasing Economy upgrades:

| Upgrade | Effect |
|---|---|
| `BANK_CAP_I` | Bank cap ×2 (default: 2,000,000) |
| `BANK_CAP_II` | Bank cap ×3 (default: 3,000,000) |

Both upgrades can be active simultaneously for a ×3 multiplier on the base cap.

---

## War Escrow

When a war is declared, both guilds' wagers are **escrowed** — locked and unavailable for withdrawal. The escrow releases automatically when the war ends:

- **Winner:** Receives both guilds' escrowed amounts (net gain = opponent's wager)
- **Loser:** Loses their escrowed wager
- **Forfeit with `REDUCED_LOSS` upgrade:** Loser pays only 75% of their wager; 25% is returned

---

## Upgrade Costs

Upgrades are purchased directly from the guild bank. Cost is defined per upgrade in the datapack JSON. See [Upgrade System](Upgrade-System) for details.

---

## See Also
- [Upgrade System](Upgrade-System)
- [Wars](Wars)
- [Configuration](Configuration)
