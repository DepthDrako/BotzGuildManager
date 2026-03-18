package com.botzguildz.data;

import net.minecraft.nbt.*;

import java.util.*;

/**
 * One item-collection, kill, or mining mission assigned to a guild.
 * Shared progress is tracked across all contributing members.
 */
public class GuildMissionEntry {

    public enum MissionType {
        KILL("Kill"), MINE("Mine"), COLLECT("Collect");

        public final String label;
        MissionType(String label) { this.label = label; }
    }

    private final UUID       missionId;
    private final UUID       guildId;
    private final MissionType type;
    private final String     targetId;       // registry ID e.g. "minecraft:zombie"
    private final String     displayName;    // friendly name e.g. "Zombie"
    private final int        required;
    private       int        progress;
    private final Map<UUID, Integer> contributions = new LinkedHashMap<>();
    private final long       guildReward;    // deposited to guild bank on complete
    private final long       playerReward;   // flat amount given to each contributor
    private       boolean    completed = false;
    private final long       expiresAt;

    public GuildMissionEntry(UUID missionId, UUID guildId, MissionType type,
                             String targetId, String displayName,
                             int required, long guildReward, long playerReward,
                             long expiresAt) {
        this.missionId   = missionId;
        this.guildId     = guildId;
        this.type        = type;
        this.targetId    = targetId;
        this.displayName = displayName;
        this.required    = required;
        this.guildReward = guildReward;
        this.playerReward = playerReward;
        this.expiresAt   = expiresAt;
    }

    /**
     * Adds progress for a player.
     * @return {@code true} if this call completed the mission.
     */
    public boolean addProgress(UUID playerUUID, int amount) {
        if (completed) return false;
        contributions.merge(playerUUID, amount, Integer::sum);
        progress = Math.min(progress + amount, required);
        if (progress >= required) {
            completed = true;
            return true;
        }
        return false;
    }

    /** Simple ASCII progress bar, e.g. "████░░░░░░ 40/100" */
    public String progressBar() {
        int filled = required == 0 ? 10 : (int) (10.0 * progress / required);
        return "█".repeat(filled) + "░".repeat(10 - filled) + " " + progress + "/" + required;
    }

    /** Time remaining as a human-readable string, e.g. "18h 32m". */
    public String timeRemaining() {
        long ms = Math.max(0, expiresAt - System.currentTimeMillis());
        long hours = ms / 3_600_000;
        long mins  = (ms % 3_600_000) / 60_000;
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    public boolean isExpired()  { return System.currentTimeMillis() > expiresAt; }
    public boolean isActive()   { return !completed && !isExpired(); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID        getMissionId()    { return missionId; }
    public UUID        getGuildId()      { return guildId; }
    public MissionType getType()         { return type; }
    public String      getTargetId()     { return targetId; }
    public String      getDisplayName()  { return displayName; }
    public int         getRequired()     { return required; }
    public int         getProgress()     { return progress; }
    public Map<UUID, Integer> getContributions() { return contributions; }
    public long        getGuildReward()  { return guildReward; }
    public long        getPlayerReward() { return playerReward; }
    public boolean     isCompleted()     { return completed; }
    public long        getExpiresAt()    { return expiresAt; }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("missionId",   missionId);
        tag.putUUID("guildId",     guildId);
        tag.putString("type",      type.name());
        tag.putString("targetId",  targetId);
        tag.putString("displayName", displayName);
        tag.putInt("required",     required);
        tag.putInt("progress",     progress);
        tag.putLong("guildReward",  guildReward);
        tag.putLong("playerReward", playerReward);
        tag.putBoolean("completed", completed);
        tag.putLong("expiresAt",   expiresAt);
        CompoundTag contribs = new CompoundTag();
        for (Map.Entry<UUID, Integer> e : contributions.entrySet())
            contribs.putInt(e.getKey().toString(), e.getValue());
        tag.put("contributions", contribs);
        return tag;
    }

    public static GuildMissionEntry fromNBT(CompoundTag tag) {
        GuildMissionEntry e = new GuildMissionEntry(
                tag.getUUID("missionId"),
                tag.getUUID("guildId"),
                MissionType.valueOf(tag.getString("type")),
                tag.getString("targetId"),
                tag.getString("displayName"),
                tag.getInt("required"),
                tag.getLong("guildReward"),
                tag.getLong("playerReward"),
                tag.getLong("expiresAt")
        );
        e.progress  = tag.getInt("progress");
        e.completed = tag.getBoolean("completed");
        CompoundTag contribs = tag.getCompound("contributions");
        for (String key : contribs.getAllKeys())
            e.contributions.put(UUID.fromString(key), contribs.getInt(key));
        return e;
    }
}
