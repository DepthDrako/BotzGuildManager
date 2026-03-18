# Changelog

All notable changes to BotzGuildz are documented here.

---

## [1.5.2] — 2026-03-18

### Added
- **Fantasy GUI Theme** — All economy and guild chest GUIs (Auction House, Bounties, Shops, Banks, Vault, Missions, Permissions, etc.) now use a fully custom programmatic renderer (`FantasyContainerScreen`) replacing the vanilla chest texture. Features a midnight-blue background, antique-gold frame with corner accents, gradient header, and themed slot backgrounds.
- **Personal Bank GUI** (`/bank`) — New custom fantasy-styled bank screen (`BankTestScreen`) with a dedicated wallet panel, tiered deposit/withdraw rows for each denomination, and a live balance display.
- **Currency Abbreviations in GUIs** — All GUI lore text and slot names now display abbreviated denomination labels:
  | Full Name | Abbreviation | Value |
  |---|---|---|
  | Guild Bit | GB | 1 |
  | Guild Chip | GCh | 8 |
  | Guild Token | GT | 64 |
  | Guild Coin | GC | 512 |
  | Guild Mark | GM | 4,096 |
  | Guild Seal | GS | 32,768 |

### Changed
- `CurrencyManager.formatShort()` — New method (delegates through `ICurrencyProvider.formatShort()`) produces compact denomination strings (e.g. `2GM  3GC  1GB`) for GUI contexts. `format()` (full names) is still used in chat messages and server logs.
- `BankTestScreen` layout refinements — Deposit and withdraw rows no longer overlap; wallet balance uses abbreviated format; close button text is centered inside the button; footer displays a clean 3-line hint without decorative item icons.
- All 13 economy chest menus now registered against `FantasyContainerScreen` instead of the vanilla `UpgradesScreen`.

### Internal
- Added `ICurrencyProvider.formatShort()` default method; `PhysicalItemProvider` overrides it with tier-aware abbreviation logic.
- `FantasyContainerScreen` dynamically resolves the player-inventory separator position at render time — compatible with any `ChestMenu` row count (3-row, 6-row, etc.).

---

## [1.5.1] — (internal)

Skipped — internal build only.

---

## [1.5.0] — 2026-03-01

### Added
- **Economy System** — Full market ecosystem: Auction House (`/ah`), player bounties (`/bounty`), guild bounty board (`/guild bounty`), personal bank (`/bank`), guild bank (`/guild bank`), player shops (`/shop`), and guild shops (`/guild shop`).
- **Guild Vault** — Shared item storage accessible to all members; withdrawals require the `MANAGE_VAULT` permission. Command: `/guild vault`.
- **Guild Missions** — Daily shared KILL / MINE / COLLECT quests auto-generated per guild, with configurable currency and XP rewards. Command: `/guild missions`.
- **Contribution Points** — Per-member effort tracking, awarded on mission completion and vault deposits. Command: `/guild contributions`.
- **Join Requests** — Players can apply to guilds without a direct invite. Commands: `/guild apply`, `/guild accept`, `/guild deny`, `/guild applications`.
- **Alliance Network Effects** — Cross-guild officer chat channel (`/guild ally chat`), ally teleport (`/guild ally tp`), and FTB chunk visibility sync.
- **Datapack-Driven Upgrade System** — Upgrades defined entirely in JSON datapacks; categories and upgrade metadata hot-reloaded on `/reload`. See [Datapack Guide](../../wiki/Datapack-Guide).
- **Guild Tags in Chat** — `[TAG]` automatically prepended to all guild member chat messages.
- **Leaderboards** — Guild and player rankings by wealth, war wins, and duel wins. Commands: `/guild leaderboard`, `/guild top`.
- **Roman Colosseum Arena** — Procedurally generated in the `guild_arena` dimension. Admin commands: `/guild arena`, `/guild arena edit`, `/guild arena reset`.
- **FTB Team Color Sync** — FTB team colour is applied to guild chat colour on login.

### Changed
- Guild XP is now awarded for daily logins, player kills, and war wins (configurable).
- War escrow now locks both guilds' wagers until the war resolves; `REDUCED_LOSS` upgrade reduces loser payout to 75%.

---

## [1.0.0] — Initial Release

- Guild creation, naming, tagging
- Custom rank tiers with permission nodes
- Guild bank (shared treasury)
- Guild wars (open-world and arena)
- Alliances
- Basic upgrade shop
- Duels with optional wagers
- Raid parties with damage-proportional loot split
