package com.botzguildz.upgrade;

/**
 * All purchasable guild upgrades.
 *
 * Fields: displayName, description, category, cost (currency units), requiredLevel, prerequisite (null if none).
 *
 * Effects are resolved at runtime by GuildUtils.applyUpgradeEffects() and checked
 * wherever the upgrade's benefit is used (e.g., GuildEventHandler for potion effects,
 * GuildSavedData for member cap, GuildConfig earn-rate multipliers, etc.).
 */
public enum GuildUpgrade {

    // ── Combat ────────────────────────────────────────────────────────────────
    COMBAT_DAMAGE_I(
            "Combat Boost I",
            "+5% damage for all guild members.",
            UpgradeCategory.COMBAT, 500, 3, null),
    COMBAT_DAMAGE_II(
            "Combat Boost II",
            "+10% damage for all guild members (stacks with I).",
            UpgradeCategory.COMBAT, 1500, 7, "COMBAT_DAMAGE_I"),
    COMBAT_ARMOR_I(
            "Fortified I",
            "Grants Resistance I to all online guild members on login.",
            UpgradeCategory.COMBAT, 500, 3, null),
    COMBAT_ARMOR_II(
            "Fortified II",
            "Upgrades login buff to Resistance II.",
            UpgradeCategory.COMBAT, 1500, 8, "COMBAT_ARMOR_I"),
    COMBAT_REGEN(
            "Battle Regen",
            "Grants Regeneration I to all online members on login.",
            UpgradeCategory.COMBAT, 2000, 12, null),

    // ── Utility ───────────────────────────────────────────────────────────────
    MEMBER_SLOTS_I(
            "Expanded Roster I",
            "+10 max member slots.",
            UpgradeCategory.UTILITY, 300, 2, null),
    MEMBER_SLOTS_II(
            "Expanded Roster II",
            "+20 additional max member slots.",
            UpgradeCategory.UTILITY, 900, 6, "MEMBER_SLOTS_I"),
    MEMBER_SLOTS_III(
            "Expanded Roster III",
            "+30 additional max member slots.",
            UpgradeCategory.UTILITY, 2700, 14, "MEMBER_SLOTS_II"),
    HOME_COOLDOWN_I(
            "Swift Return I",
            "Reduces /guild home cooldown by 50%.",
            UpgradeCategory.UTILITY, 400, 4, null),
    HOME_COOLDOWN_II(
            "Swift Return II",
            "Reduces /guild home cooldown by 75% total.",
            UpgradeCategory.UTILITY, 1200, 10, "HOME_COOLDOWN_I"),
    CHUNK_CLAIM_I(
            "Expanded Territory I",
            "Grants the guild's FTB team +10 extra chunk claim slots (requires FTBChunks).",
            UpgradeCategory.UTILITY, 400, 5, null),
    CHUNK_CLAIM_II(
            "Expanded Territory II",
            "Grants +10 additional chunk claims (20 total). Requires FTBChunks.",
            UpgradeCategory.UTILITY, 1200, 10, "CHUNK_CLAIM_I"),
    CHUNK_FORCE_LOAD(
            "Force Loading",
            "Grants the guild's FTB team +10 force-loadable chunk slots (requires FTBChunks).",
            UpgradeCategory.UTILITY, 800, 8, "CHUNK_CLAIM_I"),

    // ── Economy ───────────────────────────────────────────────────────────────
    EARN_RATE_I(
            "Prosperous I",
            "+25% currency earn rate from all sources.",
            UpgradeCategory.ECONOMY, 600, 5, null),
    EARN_RATE_II(
            "Prosperous II",
            "+50% currency earn rate from all sources (total).",
            UpgradeCategory.ECONOMY, 1800, 12, "EARN_RATE_I"),
    BANK_CAP_I(
            "Expanded Vault I",
            "Doubles the maximum guild bank balance.",
            UpgradeCategory.ECONOMY, 800, 6, null),
    BANK_CAP_II(
            "Expanded Vault II",
            "Triples the maximum guild bank balance (total).",
            UpgradeCategory.ECONOMY, 2400, 15, "BANK_CAP_I"),

    // ── Defense ───────────────────────────────────────────────────────────────
    WAR_SHIELD(
            "War Shield",
            "After losing a war, your guild is immune to war declarations for 48 hours.",
            UpgradeCategory.DEFENSE, 2000, 10, null),
    REDUCED_LOSS(
            "Resilient",
            "Reduces the wager paid out on a forfeit by 25% (you keep 25% back).",
            UpgradeCategory.DEFENSE, 1000, 8, null),

    // ── Arcane ────────────────────────────────────────────────────────────────
    ARENA_WAR(
            "Arena Warfare",
            "Unlocks arena war mode — summon both guilds to the ruins dimension to fight.",
            UpgradeCategory.ARCANE, 3000, 15, null),
    ALLIANCE(
            "Diplomatic Relations",
            "Unlocks the alliance system — form pacts with other guilds.",
            UpgradeCategory.ARCANE, 2500, 12, null);

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String displayName;
    private final String description;
    private final UpgradeCategory category;
    private final long cost;
    private final int requiredLevel;
    private final String prerequisiteId; // null if no prereq

    GuildUpgrade(String displayName, String description, UpgradeCategory category,
                 long cost, int requiredLevel, String prerequisiteId) {
        this.displayName    = displayName;
        this.description    = description;
        this.category       = category;
        this.cost           = cost;
        this.requiredLevel  = requiredLevel;
        this.prerequisiteId = prerequisiteId;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getDisplayName()   { return displayName; }
    public String getDescription()   { return description; }
    public UpgradeCategory getCategory() { return category; }
    public long getCost()            { return cost; }
    public int getRequiredLevel()    { return requiredLevel; }
    public String getPrerequisiteId(){ return prerequisiteId; }

    public GuildUpgrade getPrerequisite() {
        if (prerequisiteId == null) return null;
        try { return GuildUpgrade.valueOf(prerequisiteId); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** Whether a guild meets the level and prereq requirements to purchase this upgrade. */
    public boolean isAvailableTo(com.botzguildz.data.Guild guild) {
        if (guild.getLevel() < requiredLevel) return false;
        if (prerequisiteId != null && !guild.hasUpgrade(prerequisiteId)) return false;
        return !guild.hasUpgrade(this.name());
    }

    /** Earn-rate multiplier granted by economy upgrades (applied additively). */
    public static double getEarnRateMultiplier(com.botzguildz.data.Guild guild) {
        double mult = 1.0;
        if (guild.hasUpgrade("EARN_RATE_I"))  mult += 0.25;
        if (guild.hasUpgrade("EARN_RATE_II")) mult += 0.25; // cumulative → 1.50 total
        return mult;
    }
}
