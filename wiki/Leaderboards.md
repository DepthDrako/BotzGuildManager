# Leaderboards

BotzGuildz includes three leaderboards — two guild-level and one player-level.

---

## Guild Leaderboards

### `/guild top` or `/guild top money`
Top 10 guilds by **available bank balance** (excludes escrowed war wagers).

Example output:
```
═══════ Top Guilds — Wealth ═══════
  #1  DragonsClaw [DC]  — 145,320 GC
  #2  IronFist [IF]     — 98,000 GC
  #3  Wanderers [WND]   — 51,700 GC
  ...
```

### `/guild top wars`
Top 10 guilds by **total war wins**.

Example output:
```
═══════ Top Guilds — War Wins ═══════
  #1  IronFist [IF]     — 14 wins
  #2  DragonsClaw [DC]  — 9 wins
  ...
```

Only guilds with at least one win are listed.

---

## Player Leaderboard

### `/duel top`
Top 10 players by **total duel wins**.

Example output:
```
═══════ Top Players — Duel Wins ═══════
  #1  Steve     — 27 wins
  #2  Alex      — 19 wins
  #3  Notch     — 12 wins
  ...
```

Resolves display names for offline players using the server's profile cache.

---

## See Also
- [Guild Commands](Guild-Commands)
- [Duels](Duels)
