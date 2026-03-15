package com.botzguildz.data;

/**
 * All permission nodes that can be assigned to a guild rank.
 */
public enum RankPermission {
    INVITE,           // Can invite new members
    KICK,             // Can kick members of lower rank
    MANAGE_BANK,      // Can deposit and withdraw from the guild bank
    DECLARE_WAR,      // Can declare or accept wars
    MANAGE_RANKS,     // Can create, delete, and rename ranks (and assign permissions)
    MANAGE_UPGRADES,  // Can purchase guild upgrades
    TOGGLE_FF,        // Can toggle friendly fire
    SET_HOME,         // Can set the guild home
    MANAGE_ALLIES     // Can invite or break alliances
}
