package com.botzguildz.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

import java.util.*;

/**
 * Represents a guild war — either pending acceptance or actively in progress.
 *
 * Wager system:
 *   - Declaring guild puts up wageredAmountPerSide, challenged guild matches it.
 *   - Total pot = wageredAmountPerSide * 2.
 *   - Winner receives pot + 10% bonus (generated on top, not from losers).
 *
 * Lives system (set by challenged guild on accept):
 *   - Range 1–999. When a player uses all their lives they are sidelined.
 *   - Win condition: elimination (all enemies out of lives) OR time expiry (most kills wins).
 */
public class GuildWar {

    private final UUID warId;
    private final UUID declaringGuildId;
    private final UUID challengedGuildId;
    private final long wageredAmountPerSide;

    // Set when the challenged guild accepts
    private int livesPerPlayer = 1;
    private WarMode mode = WarMode.OPEN_WORLD;

    private WarState state = WarState.PENDING;

    // Kill counts and lives — keyed by player UUID
    private final Map<UUID, Integer> killCounts      = new HashMap<>();
    private final Map<UUID, Integer> remainingLives  = new HashMap<>();

    // Which guild each participant belongs to (cached for fast lookup)
    private final Map<UUID, UUID> participantGuildMap = new HashMap<>();

    private long pendingExpiry = 0;  // System.currentTimeMillis() when pending declaration expires
    private long startTime     = 0;
    private long endTime       = 0;  // Calculated on accept: startTime + configured duration

    private UUID winnerGuildId = null;

    // Arena war: where to return players after the war ends
    private final Map<UUID, BlockPos>  returnPositions   = new HashMap<>();
    private final Map<UUID, String>    returnDimensions  = new HashMap<>();
    private BlockPos arenaCenter = null;

    // ── Constructor ───────────────────────────────────────────────────────────

    public GuildWar(UUID declaringGuildId, UUID challengedGuildId, long wageredAmountPerSide, long pendingExpiry) {
        this.warId                 = UUID.randomUUID();
        this.declaringGuildId      = declaringGuildId;
        this.challengedGuildId     = challengedGuildId;
        this.wageredAmountPerSide  = wageredAmountPerSide;
        this.pendingExpiry         = pendingExpiry;
        this.state                 = WarState.PENDING;
    }

    // Private constructor used by fromNBT
    private GuildWar(UUID warId, UUID declaringGuildId, UUID challengedGuildId, long wageredAmountPerSide) {
        this.warId                = warId;
        this.declaringGuildId     = declaringGuildId;
        this.challengedGuildId    = challengedGuildId;
        this.wageredAmountPerSide = wageredAmountPerSide;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Activate the war after the challenged guild accepts.
     * Initializes kill and lives tracking for all participants.
     */
    public void activate(int livesPerPlayer, WarMode mode, long durationMs,
                         List<UUID> declaringMembers, List<UUID> challengedMembers) {
        this.livesPerPlayer = livesPerPlayer;
        this.mode           = mode;
        this.state          = WarState.ACTIVE;
        this.startTime      = System.currentTimeMillis();
        this.endTime        = startTime + durationMs;

        for (UUID uuid : declaringMembers) {
            killCounts.put(uuid, 0);
            remainingLives.put(uuid, livesPerPlayer);
            participantGuildMap.put(uuid, declaringGuildId);
        }
        for (UUID uuid : challengedMembers) {
            killCounts.put(uuid, 0);
            remainingLives.put(uuid, livesPerPlayer);
            participantGuildMap.put(uuid, challengedGuildId);
        }
    }

    // ── Kill & Lives Tracking ─────────────────────────────────────────────────

    /**
     * Record a kill. Deducts a life from the victim.
     * @return true if the victim is now out of lives
     */
    public boolean recordKill(UUID killer, UUID victim) {
        if (!participantGuildMap.containsKey(killer) || !participantGuildMap.containsKey(victim)) return false;
        // No kills within same guild
        if (participantGuildMap.get(killer).equals(participantGuildMap.get(victim))) return false;

        killCounts.merge(killer, 1, Integer::sum);
        int livesLeft = remainingLives.merge(victim, -1, Integer::sum);
        return livesLeft <= 0;
    }

    public boolean isParticipant(UUID uuid) { return participantGuildMap.containsKey(uuid); }
    public boolean isEliminated(UUID uuid)  { return remainingLives.getOrDefault(uuid, 0) <= 0; }

    public UUID getParticipantGuild(UUID playerUUID) { return participantGuildMap.get(playerUUID); }

    public int getKills(UUID uuid)          { return killCounts.getOrDefault(uuid, 0); }
    public int getRemainingLives(UUID uuid) { return remainingLives.getOrDefault(uuid, livesPerPlayer); }

    /** Total kills for a guild across all its members. */
    public int getGuildKills(UUID guildId) {
        return participantGuildMap.entrySet().stream()
                .filter(e -> e.getValue().equals(guildId))
                .mapToInt(e -> killCounts.getOrDefault(e.getKey(), 0))
                .sum();
    }

    /** Returns true if all members of a guild have been eliminated (no lives left). */
    public boolean isGuildEliminated(UUID guildId) {
        return participantGuildMap.entrySet().stream()
                .filter(e -> e.getValue().equals(guildId))
                .allMatch(e -> remainingLives.getOrDefault(e.getKey(), 0) <= 0);
    }

    /** Check if the war should end due to time or elimination. Returns the winning guild ID, or null if no winner yet. */
    public UUID checkWinCondition() {
        if (state != WarState.ACTIVE) return null;

        // Elimination win
        if (isGuildEliminated(declaringGuildId)) return challengedGuildId;
        if (isGuildEliminated(challengedGuildId)) return declaringGuildId;

        // Time expiry
        if (System.currentTimeMillis() >= endTime) {
            int declaringKills  = getGuildKills(declaringGuildId);
            int challengedKills = getGuildKills(challengedGuildId);
            if (declaringKills > challengedKills)  return declaringGuildId;
            if (challengedKills > declaringKills)  return challengedGuildId;
            return null; // Draw — returns null, handled as a draw by GuildSavedData
        }

        return null;
    }

    public boolean isTimeExpired() { return state == WarState.ACTIVE && System.currentTimeMillis() >= endTime; }
    public boolean isPendingExpired() { return state == WarState.PENDING && System.currentTimeMillis() >= pendingExpiry; }

    /** Total pot = both sides' wagers combined. */
    public long getTotalPot()   { return wageredAmountPerSide * 2; }

    /** Payout to winner = pot + 10% bonus. */
    public long getWinnerPayout() { return (long)(getTotalPot() * 1.10); }

    // ── Return Positions (Arena War) ──────────────────────────────────────────

    public void saveReturnPosition(UUID playerUUID, BlockPos pos, String dimensionId) {
        returnPositions.put(playerUUID, pos);
        returnDimensions.put(playerUUID, dimensionId);
    }

    public BlockPos  getReturnPosition(UUID playerUUID)  { return returnPositions.get(playerUUID); }
    public String    getReturnDimension(UUID playerUUID) { return returnDimensions.get(playerUUID); }

    // ── NBT Serialization ─────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("warId", warId);
        tag.putUUID("declaringGuildId", declaringGuildId);
        tag.putUUID("challengedGuildId", challengedGuildId);
        tag.putLong("wageredAmountPerSide", wageredAmountPerSide);
        tag.putInt("livesPerPlayer", livesPerPlayer);
        tag.putString("mode", mode.name());
        tag.putString("state", state.name());
        tag.putLong("pendingExpiry", pendingExpiry);
        tag.putLong("startTime", startTime);
        tag.putLong("endTime", endTime);
        if (winnerGuildId != null) tag.putUUID("winnerGuildId", winnerGuildId);
        if (arenaCenter != null) tag.putIntArray("arenaCenter",
                new int[]{arenaCenter.getX(), arenaCenter.getY(), arenaCenter.getZ()});

        // Kill counts
        CompoundTag kills = new CompoundTag();
        for (Map.Entry<UUID, Integer> e : killCounts.entrySet())
            kills.putInt(e.getKey().toString(), e.getValue());
        tag.put("killCounts", kills);

        // Remaining lives
        CompoundTag lives = new CompoundTag();
        for (Map.Entry<UUID, Integer> e : remainingLives.entrySet())
            lives.putInt(e.getKey().toString(), e.getValue());
        tag.put("remainingLives", lives);

        // Participant guild map
        CompoundTag pgm = new CompoundTag();
        for (Map.Entry<UUID, UUID> e : participantGuildMap.entrySet())
            pgm.putUUID(e.getKey().toString(), e.getValue());
        tag.put("participantGuildMap", pgm);

        // Return positions
        ListTag retPosList = new ListTag();
        for (Map.Entry<UUID, BlockPos> e : returnPositions.entrySet()) {
            CompoundTag rt = new CompoundTag();
            rt.putUUID("uuid", e.getKey());
            BlockPos p = e.getValue();
            rt.putIntArray("pos", new int[]{p.getX(), p.getY(), p.getZ()});
            rt.putString("dim", returnDimensions.getOrDefault(e.getKey(), "minecraft:overworld"));
            retPosList.add(rt);
        }
        tag.put("returnPositions", retPosList);

        return tag;
    }

    public static GuildWar fromNBT(CompoundTag tag) {
        UUID warId               = tag.getUUID("warId");
        UUID declaringGuildId    = tag.getUUID("declaringGuildId");
        UUID challengedGuildId   = tag.getUUID("challengedGuildId");
        long wageredPerSide      = tag.getLong("wageredAmountPerSide");

        GuildWar war = new GuildWar(warId, declaringGuildId, challengedGuildId, wageredPerSide);
        war.livesPerPlayer  = tag.getInt("livesPerPlayer");
        war.mode            = WarMode.valueOf(tag.getString("mode"));
        war.state           = WarState.valueOf(tag.getString("state"));
        war.pendingExpiry   = tag.getLong("pendingExpiry");
        war.startTime       = tag.getLong("startTime");
        war.endTime         = tag.getLong("endTime");
        if (tag.hasUUID("winnerGuildId")) war.winnerGuildId = tag.getUUID("winnerGuildId");
        if (tag.contains("arenaCenter")) {
            int[] ac = tag.getIntArray("arenaCenter");
            war.arenaCenter = new BlockPos(ac[0], ac[1], ac[2]);
        }

        CompoundTag kills = tag.getCompound("killCounts");
        for (String key : kills.getAllKeys())
            war.killCounts.put(UUID.fromString(key), kills.getInt(key));

        CompoundTag lives = tag.getCompound("remainingLives");
        for (String key : lives.getAllKeys())
            war.remainingLives.put(UUID.fromString(key), lives.getInt(key));

        CompoundTag pgm = tag.getCompound("participantGuildMap");
        for (String key : pgm.getAllKeys())
            war.participantGuildMap.put(UUID.fromString(key), pgm.getUUID(key));

        ListTag retPosList = tag.getList("returnPositions", Tag.TAG_COMPOUND);
        for (Tag t : retPosList) {
            CompoundTag rt = (CompoundTag) t;
            UUID uuid = rt.getUUID("uuid");
            int[] pos = rt.getIntArray("pos");
            war.returnPositions.put(uuid, new BlockPos(pos[0], pos[1], pos[2]));
            war.returnDimensions.put(uuid, rt.getString("dim"));
        }

        return war;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getWarId()               { return warId; }
    public UUID getDeclaringGuildId()    { return declaringGuildId; }
    public UUID getChallengedGuildId()   { return challengedGuildId; }
    public long getWageredAmountPerSide(){ return wageredAmountPerSide; }
    public int  getLivesPerPlayer()      { return livesPerPlayer; }
    public WarMode getMode()             { return mode; }
    public WarState getState()           { return state; }
    public void setState(WarState s)     { this.state = s; }
    public long getPendingExpiry()       { return pendingExpiry; }
    public long getStartTime()           { return startTime; }
    public long getEndTime()             { return endTime; }
    public long getTimeRemainingMs()     { return Math.max(0, endTime - System.currentTimeMillis()); }
    public UUID getWinnerGuildId()       { return winnerGuildId; }
    public void setWinnerGuildId(UUID id){ this.winnerGuildId = id; }
    public BlockPos getArenaCenter()     { return arenaCenter; }
    public void setArenaCenter(BlockPos p){ this.arenaCenter = p; }
    public Map<UUID, Integer> getKillCounts()     { return killCounts; }
    public Map<UUID, Integer> getRemainingLives() { return remainingLives; }
    public Map<UUID, UUID> getParticipantGuildMap(){ return participantGuildMap; }

    /** Returns the opponent guild ID for a given guild ID. */
    public UUID getOpponent(UUID guildId) {
        if (guildId.equals(declaringGuildId))  return challengedGuildId;
        if (guildId.equals(challengedGuildId)) return declaringGuildId;
        return null;
    }
}
