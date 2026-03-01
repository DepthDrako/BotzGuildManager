package com.botzguildz.raid;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks damage contributions during a single boss fight for one RaidParty.
 */
public class RaidBossFight {

    private final UUID partyId;
    private final UUID bossEntityId;
    private final ResourceLocation entityType;
    private final String bossName;

    /** player UUID → total damage dealt (post-armor) */
    private final Map<UUID, Float> damage = new HashMap<>();

    public RaidBossFight(UUID partyId,
                         UUID bossEntityId,
                         ResourceLocation entityType,
                         String bossName) {
        this.partyId      = partyId;
        this.bossEntityId = bossEntityId;
        this.entityType   = entityType;
        this.bossName     = bossName;
    }

    // ── mutation ──────────────────────────────────────────────────────────

    /** Add {@code amount} HP of damage for {@code player}. */
    public void recordDamage(UUID player, float amount) {
        damage.merge(player, amount, Float::sum);
    }

    // ── queries ───────────────────────────────────────────────────────────

    public UUID getPartyId()        { return partyId;      }
    public UUID getBossEntityId()   { return bossEntityId; }
    public ResourceLocation getEntityType() { return entityType; }
    public String getBossName()     { return bossName;     }

    /** Set of all players who dealt at least some damage. */
    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(damage.keySet());
    }

    /** Raw damage dealt by {@code player} (0 if they never hit). */
    public float getDamageDealt(UUID player) {
        return damage.getOrDefault(player, 0f);
    }

    /** Sum of all recorded damage. */
    public float getTotalDamage() {
        float total = 0f;
        for (float v : damage.values()) total += v;
        return total;
    }

    /**
     * Fraction of total damage dealt by {@code player} (0–1).
     * Returns 0 if no damage has been recorded yet.
     */
    public float getShare(UUID player) {
        float total = getTotalDamage();
        if (total <= 0f) return 0f;
        return getDamageDealt(player) / total;
    }

    /**
     * Returns the UUID of the participant who dealt the most damage.
     * If the map is empty returns {@code null}.
     */
    public UUID getHighestDamageDealer() {
        UUID best = null;
        float bestVal = -1f;
        for (Map.Entry<UUID, Float> e : damage.entrySet()) {
            if (e.getValue() > bestVal) {
                bestVal = e.getValue();
                best    = e.getKey();
            }
        }
        return best;
    }
}
