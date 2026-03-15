package com.botzguildz.data;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Represents a single member's record inside a guild.
 */
public class GuildMember {

    private final UUID playerUUID;
    private String playerName;        // Cached display name
    private String rankName;          // Name of their GuildRank
    private final long joinTime;      // System.currentTimeMillis() when they joined
    private long contribution;        // Currency contributed to guild bank lifetime total
    private long lastLoginTime;       // Used for daily currency/XP reward
    private boolean onlineToday;      // Prevents double-rewarding per day

    public GuildMember(UUID playerUUID, String playerName, String rankName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.rankName = rankName;
        this.joinTime = System.currentTimeMillis();
        this.contribution = 0;
        this.lastLoginTime = 0;
        this.onlineToday = false;
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", playerUUID);
        tag.putString("name", playerName);
        tag.putString("rank", rankName);
        tag.putLong("joinTime", joinTime);
        tag.putLong("contribution", contribution);
        tag.putLong("lastLoginTime", lastLoginTime);
        tag.putBoolean("onlineToday", onlineToday);
        return tag;
    }

    public static GuildMember fromNBT(CompoundTag tag) {
        UUID uuid = tag.getUUID("uuid");
        String name = tag.getString("name");
        String rank = tag.getString("rank");
        GuildMember m = new GuildMember(uuid, name, rank);
        m.contribution = tag.getLong("contribution");
        m.lastLoginTime = tag.getLong("lastLoginTime");
        m.onlineToday = tag.getBoolean("onlineToday");
        return m;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String name) { this.playerName = name; }
    public String getRankName() { return rankName; }
    public void setRankName(String rankName) { this.rankName = rankName; }
    public long getJoinTime() { return joinTime; }
    public long getContribution() { return contribution; }
    public void addContribution(long amount) { this.contribution += amount; }
    public long getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(long t) { this.lastLoginTime = t; }
    public boolean isOnlineToday() { return onlineToday; }
    public void setOnlineToday(boolean b) { this.onlineToday = b; }
}
