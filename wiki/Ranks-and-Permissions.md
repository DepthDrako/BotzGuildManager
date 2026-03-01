# Ranks & Permissions

Ranks define a member's authority within a guild. Each guild starts with four default ranks and can add unlimited custom ones.

---

## Default Ranks

| Rank | Priority | Default Permissions |
|---|---|---|
| **Leader** | 1000 | All permissions (always, cannot be changed) |
| **Officer** | 300 | All except `MANAGE_RANKS` |
| **Member** | 100 | `INVITE`, `SET_HOME` |
| **Recruit** | 50 | `INVITE` |

> **Priority** determines rank order. Higher number = higher authority. The Leader rank always has the highest priority and cannot be deleted or renamed.

---

## Rank Commands

All rank commands require the `MANAGE_RANKS` permission unless otherwise noted.

### `/guild rank list`
Lists all ranks with their priorities and current permission states.

### `/guild rank create <name> <priority>`
Creates a new custom rank.
- Priority must be between 1 and 999
- Example: `/guild rank create Veteran 200`

### `/guild rank delete <name>`
Deletes a rank. Members holding that rank are moved to the default rank.
- Cannot delete the **Leader** rank

### `/guild rank rename <oldName> <newName>`
Renames a rank.
- Cannot rename the **Leader** rank

### `/guild rank setdefault <name>`
Sets which rank new members receive when they join.

### `/guild rank set <player> <rank>`
Assigns a rank to a guild member.
- Cannot change the Leader's rank
- Cannot assign a rank equal to or higher than your own (unless you are the Leader)
- Example: `/guild rank set PlayerName Officer`

### `/guild rank setperm <rank> <permission> <true|false>`
Grants or revokes a permission from a rank.
- Example: `/guild rank setperm Veteran DECLARE_WAR true`

---

## Permission Nodes

| Permission | What it allows |
|---|---|
| `INVITE` | Invite players to the guild |
| `KICK` | Remove members from the guild |
| `SET_HOME` | Set the guild home teleport |
| `MANAGE_RANKS` | Create, delete, rename ranks; assign permissions; set player ranks |
| `MANAGE_BANK` | Withdraw from the guild bank |
| `MANAGE_UPGRADES` | Purchase guild upgrades |
| `MANAGE_ALLIES` | Send, accept, and break alliances |
| `DECLARE_WAR` | Declare, accept, deny, or forfeit wars |
| `TOGGLE_FF` | Toggle friendly fire on/off |

---

## How Priority Works

When rank-gating an action, the mod checks **your priority vs the target's priority**:

- To **kick** someone, your priority must be **higher** than theirs
- To **assign** a rank, you cannot assign a rank at or above your own priority (unless you are the Leader)
- Officers (300) can kick Members (100) but not other Officers

The **Leader** (priority 1000) can always perform any action on any member.

---

## Custom Rank Example

A common setup for a large server guild:

```
Leader     (1000) — All perms
Co-Leader  (900)  — All except MANAGE_RANKS
Officer    (300)  — KICK, DECLARE_WAR, MANAGE_ALLIES, TOGGLE_FF, SET_HOME
Sergeant   (200)  — INVITE, SET_HOME, MANAGE_BANK
Member     (100)  — INVITE, SET_HOME
Recruit    (50)   — (no permissions)
```

To set this up:
```
/guild rank create Co-Leader 900
/guild rank create Sergeant 200
/guild rank setperm Co-Leader KICK true
/guild rank setperm Co-Leader DECLARE_WAR true
... (etc)
```
