package com.botzguildz.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

import java.util.UUID;

/**
 * Represents a 1v1 duel between two players.
 *
 * Wager & lives system mirrors guild wars:
 *   - Both players put up equal wagers.
 *   - Total pot = wageredAmountPerPlayer * 2.
 *   - Winner receives pot + 10% bonus.
 *   - Lives range: 1–999 (set by challenger, default 1).
 *   - Stepping outside the duel radius or logging out = forfeit.
 */
public class DuelData {

    private final UUID duelId;
    private final UUID challengerId;
    private final UUID challengedId;
    private final long wageredAmountPerPlayer; // 0 = no wager
    private final int  livesPerPlayer;

    private DuelState state = DuelState.PENDING;

    private int challengerKills  = 0;
    private int challengedKills  = 0;
    private int challengerLivesLeft;
    private int challengedLivesLeft;

    private long pendingExpiry = 0;  // When the pending challenge expires (ms)
    private long startTime     = 0;

    private UUID winnerUUID = null;

    // Starting positions — used for radius enforcement
    private BlockPos challengerStart = null;
    private BlockPos challengedStart = null;

    // ── Constructor ───────────────────────────────────────────────────────────

    public DuelData(UUID challengerId, UUID challengedId, long wageredAmountPerPlayer,
                    int livesPerPlayer, long pendingExpiry) {
        this.duelId                 = UUID.randomUUID();
        this.challengerId           = challengerId;
        this.challengedId           = challengedId;
        this.wageredAmountPerPlayer = wageredAmountPerPlayer;
        this.livesPerPlayer         = livesPerPlayer;
        this.challengerLivesLeft    = livesPerPlayer;
        this.challengedLivesLeft    = livesPerPlayer;
        this.pendingExpiry          = pendingExpiry;
    }

    // Private constructor for fromNBT
    private DuelData(UUID duelId, UUID challengerId, UUID challengedId,
                     long wageredAmountPerPlayer, int livesPerPlayer) {
        this.duelId                 = duelId;
        this.challengerId           = challengerId;
        this.challengedId           = challengedId;
        this.wageredAmountPerPlayer = wageredAmountPerPlayer;
        this.livesPerPlayer         = livesPerPlayer;
        this.challengerLivesLeft    = livesPerPlayer;
        this.challengedLivesLeft    = livesPerPlayer;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void activate(BlockPos challengerPos, BlockPos challengedPos) {
        this.state            = DuelState.ACTIVE;
        this.startTime        = System.currentTimeMillis();
        this.challengerStart  = challengerPos;
        this.challengedStart  = challengedPos;
    }

    // ── Kill & Lives Tracking ─────────────────────────────────────────────────

    /**
     * Record a kill for the given player.
     * @return true if the victim is now out of lives (duel should end)
     */
    public boolean recordKill(UUID killer) {
        if (killer.equals(challengerId)) {
            challengerKills++;
            challengedLivesLeft = Math.max(0, challengedLivesLeft - 1);
            return challengedLivesLeft <= 0;
        } else if (killer.equals(challengedId)) {
            challengedKills++;
            challengerLivesLeft = Math.max(0, challengerLivesLeft - 1);
            return challengerLivesLeft <= 0;
        }
        return false;
    }

    public UUID getOpponent(UUID playerUUID) {
        if (playerUUID.equals(challengerId))  return challengedId;
        if (playerUUID.equals(challengedId))  return challengerId;
        return null;
    }

    public boolean isParticipant(UUID uuid) {
        return uuid.equals(challengerId) || uuid.equals(challengedId);
    }

    /** Total pot = both players' wagers combined. */
    public long getTotalPot()     { return wageredAmountPerPlayer * 2; }

    /** Winner payout = pot + 10% bonus. */
    public long getWinnerPayout() { return (long)(getTotalPot() * 1.10); }

    public boolean isPendingExpired() { return state == DuelState.PENDING && System.currentTimeMillis() >= pendingExpiry; }

    // ── NBT Serialization ─────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("duelId", duelId);
        tag.putUUID("challengerId", challengerId);
        tag.putUUID("challengedId", challengedId);
        tag.putLong("wager", wageredAmountPerPlayer);
        tag.putInt("livesPerPlayer", livesPerPlayer);
        tag.putString("state", state.name());
        tag.putInt("challengerKills", challengerKills);
        tag.putInt("challengedKills", challengedKills);
        tag.putInt("challengerLivesLeft", challengerLivesLeft);
        tag.putInt("challengedLivesLeft", challengedLivesLeft);
        tag.putLong("pendingExpiry", pendingExpiry);
        tag.putLong("startTime", startTime);
        if (winnerUUID != null) tag.putUUID("winnerUUID", winnerUUID);
        if (challengerStart != null) tag.putIntArray("challengerStart",
                new int[]{challengerStart.getX(), challengerStart.getY(), challengerStart.getZ()});
        if (challengedStart != null) tag.putIntArray("challengedStart",
                new int[]{challengedStart.getX(), challengedStart.getY(), challengedStart.getZ()});
        return tag;
    }

    public static DuelData fromNBT(CompoundTag tag) {
        UUID duelId      = tag.getUUID("duelId");
        UUID challenger  = tag.getUUID("challengerId");
        UUID challenged  = tag.getUUID("challengedId");
        long wager       = tag.getLong("wager");
        int  lives       = tag.getInt("livesPerPlayer");

        DuelData d = new DuelData(duelId, challenger, challenged, wager, lives);
        d.state               = DuelState.valueOf(tag.getString("state"));
        d.challengerKills     = tag.getInt("challengerKills");
        d.challengedKills     = tag.getInt("challengedKills");
        d.challengerLivesLeft = tag.getInt("challengerLivesLeft");
        d.challengedLivesLeft = tag.getInt("challengedLivesLeft");
        d.pendingExpiry       = tag.getLong("pendingExpiry");
        d.startTime           = tag.getLong("startTime");
        if (tag.hasUUID("winnerUUID")) d.winnerUUID = tag.getUUID("winnerUUID");
        if (tag.contains("challengerStart")) {
            int[] p = tag.getIntArray("challengerStart");
            d.challengerStart = new BlockPos(p[0], p[1], p[2]);
        }
        if (tag.contains("challengedStart")) {
            int[] p = tag.getIntArray("challengedStart");
            d.challengedStart = new BlockPos(p[0], p[1], p[2]);
        }
        return d;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getDuelId()                  { return duelId; }
    public UUID getChallengerId()            { return challengerId; }
    public UUID getChallengedId()            { return challengedId; }
    public long getWageredAmountPerPlayer()  { return wageredAmountPerPlayer; }
    public int  getLivesPerPlayer()          { return livesPerPlayer; }
    public DuelState getState()              { return state; }
    public void setState(DuelState s)        { this.state = s; }
    public int  getChallengerKills()         { return challengerKills; }
    public int  getChallengedKills()         { return challengedKills; }
    public int  getChallengerLivesLeft()     { return challengerLivesLeft; }
    public int  getChallengedLivesLeft()     { return challengedLivesLeft; }
    public long getPendingExpiry()           { return pendingExpiry; }
    public long getStartTime()               { return startTime; }
    public UUID getWinnerUUID()              { return winnerUUID; }
    public void setWinnerUUID(UUID id)       { this.winnerUUID = id; }
    public BlockPos getChallengerStart()     { return challengerStart; }
    public BlockPos getChallengedStart()     { return challengedStart; }

    public int getKills(UUID uuid) {
        if (uuid.equals(challengerId)) return challengerKills;
        if (uuid.equals(challengedId)) return challengedKills;
        return 0;
    }

    public int getLivesLeft(UUID uuid) {
        if (uuid.equals(challengerId)) return challengerLivesLeft;
        if (uuid.equals(challengedId)) return challengedLivesLeft;
        return 0;
    }
}
