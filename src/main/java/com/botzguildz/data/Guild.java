package com.botzguildz.data;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Core guild data object. Stored and retrieved via GuildSavedData.
 */
public class Guild {

    private final UUID guildId;
    private String name;
    private String tag;
    private UUID leaderUUID;

    private final Map<UUID, GuildMember> members = new LinkedHashMap<>();
    private final List<GuildRank> ranks = new ArrayList<>();

    private long bankBalance = 0;
    private long warEscrow = 0; // Locked portion of bankBalance during an active war

    private int level = 1;
    private long experience = 0;
    private final Set<String> purchasedUpgrades = new LinkedHashSet<>();

    private boolean friendlyFire = false;
    private String motd = "";

    private BlockPos homePos = null;
    private String homeDimensionId = null;
    private final Map<UUID, Long> homeCooldowns = new HashMap<>();

    private final List<UUID> alliedGuildIds = new ArrayList<>();

    private UUID currentWarId = null;
    private long lastWarEndTime = 0;
    private int warWins = 0;

    /**
     * ChatFormatting name used to colour the guild tag in chat (e.g. "GOLD", "RED").
     * Defaults to GOLD; can be synced from the linked FTB team's colour.
     */
    private String chatColorName = "GOLD";

    /**
     * UUID of the FTB Teams party that mirrors this guild's membership.
     * Null when FTB Teams is not installed or the guild predates the integration
     * (set lazily on the leader's next login via FTBBridge).
     */
    private UUID ftbTeamId = null;

    private final List<String> activityLog = new ArrayList<>(); // newest first, max 50

    // ── Constructor ───────────────────────────────────────────────────────────

    public Guild(UUID guildId, String name, String tag, UUID leaderUUID) {
        this.guildId = guildId;
        this.name = name;
        this.tag = tag;
        this.leaderUUID = leaderUUID;

        ranks.add(GuildRank.leader());
        ranks.add(GuildRank.officer());
        ranks.add(GuildRank.member());
        ranks.add(GuildRank.recruit());
    }

    // ── XP & Leveling ─────────────────────────────────────────────────────────

    /** XP required to reach this level from the previous one. */
    public static long xpForLevel(int level) {
        if (level <= 1) return 0;
        return (long)(level - 1) * (level - 1) * 100L;
    }

    /** Cumulative XP required to reach this level from level 1. */
    public static long totalXpForLevel(int level) {
        long total = 0;
        for (int i = 2; i <= level; i++) total += xpForLevel(i);
        return total;
    }

    /**
     * Add XP and handle level-ups.
     * @return number of levels gained
     */
    public int addExperience(long xp, int maxLevel) {
        this.experience += xp;
        int gained = 0;
        while (level < maxLevel && experience >= totalXpForLevel(level + 1)) {
            level++;
            gained++;
        }
        return gained;
    }

    public long xpToNextLevel(int maxLevel) {
        if (level >= maxLevel) return 0;
        return totalXpForLevel(level + 1) - experience;
    }

    // ── Members ───────────────────────────────────────────────────────────────

    public GuildMember getMember(UUID uuid) { return members.get(uuid); }
    public boolean hasMember(UUID uuid)     { return members.containsKey(uuid); }
    public Collection<GuildMember> getAllMembers() { return members.values(); }
    public Map<UUID, GuildMember> getMembersMap()  { return members; }
    public int getMemberCount() { return members.size(); }

    public void addMember(GuildMember member) {
        members.put(member.getPlayerUUID(), member);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    // ── Ranks ─────────────────────────────────────────────────────────────────

    public List<GuildRank> getRanks() { return ranks; }

    public GuildRank getRank(String name) {
        return ranks.stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public GuildRank getDefaultRank() {
        return ranks.stream()
                .filter(GuildRank::isDefault)
                .min(Comparator.comparingInt(GuildRank::getPriority))
                .orElseGet(() -> getRank("Recruit") != null ? getRank("Recruit") : ranks.get(ranks.size() - 1));
    }

    public GuildRank getMemberRank(UUID playerUUID) {
        GuildMember member = members.get(playerUUID);
        if (member == null) return null;
        return getRank(member.getRankName());
    }

    public boolean hasPermission(UUID playerUUID, RankPermission perm) {
        if (playerUUID.equals(leaderUUID)) return true;
        GuildRank rank = getMemberRank(playerUUID);
        return rank != null && rank.hasPermission(perm);
    }

    public void addRank(GuildRank rank) { ranks.add(rank); }

    public boolean removeRank(String name) {
        if (name.equalsIgnoreCase("Leader")) return false; // Cannot remove leader rank
        return ranks.removeIf(r -> r.getName().equalsIgnoreCase(name));
    }

    // ── Economy ───────────────────────────────────────────────────────────────

    /** Balance available for spending (excludes locked war escrow). */
    public long getAvailableBalance() { return bankBalance - warEscrow; }

    public boolean canAfford(long amount) { return getAvailableBalance() >= amount; }

    public void deposit(long amount) { this.bankBalance += amount; }

    /** Withdraw from available (non-escrowed) balance. Returns false if insufficient. */
    public boolean withdraw(long amount) {
        if (!canAfford(amount)) return false;
        this.bankBalance -= amount;
        return true;
    }

    /** Lock funds into escrow for a war wager. Returns false if insufficient. */
    public boolean lockEscrow(long amount) {
        if (!canAfford(amount)) return false;
        this.warEscrow += amount;
        return true;
    }

    /** Release escrowed funds (deducted from total balance — paid out). */
    public void payOutEscrow(long amount) {
        this.warEscrow = Math.max(0, warEscrow - amount);
        this.bankBalance = Math.max(0, bankBalance - amount);
    }

    /** Release escrow back to available (war cancelled or refunded). */
    public void releaseEscrow(long amount) {
        this.warEscrow = Math.max(0, warEscrow - amount);
    }

    // ── Upgrades ──────────────────────────────────────────────────────────────

    public boolean hasUpgrade(String upgradeId) { return purchasedUpgrades.contains(upgradeId); }
    public Set<String> getPurchasedUpgrades()   { return purchasedUpgrades; }
    public void addUpgrade(String upgradeId)     { purchasedUpgrades.add(upgradeId); }

    // ── Activity Log ──────────────────────────────────────────────────────────

    public void addLog(String entry) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = String.format("%02d/%02d %02d:%02d",
                now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute());
        activityLog.add(0, "[" + timestamp + "] " + entry);
        while (activityLog.size() > 50) activityLog.remove(activityLog.size() - 1);
    }

    public List<String> getActivityLog() { return activityLog; }

    // ── Allies ────────────────────────────────────────────────────────────────

    public List<UUID> getAlliedGuildIds() { return alliedGuildIds; }
    public boolean isAlliedWith(UUID guildId) { return alliedGuildIds.contains(guildId); }

    // ── NBT Serialization ─────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID("guildId", guildId);
        tag.putString("name", name);
        tag.putString("tag", this.tag);
        tag.putUUID("leaderUUID", leaderUUID);
        tag.putLong("bankBalance", bankBalance);
        tag.putLong("warEscrow", warEscrow);
        tag.putInt("level", level);
        tag.putLong("experience", experience);
        tag.putBoolean("friendlyFire", friendlyFire);
        tag.putString("motd", motd);
        tag.putLong("lastWarEndTime", lastWarEndTime);

        tag.putInt("warWins", warWins);
        tag.putString("chatColor", chatColorName);
        if (currentWarId != null) tag.putUUID("currentWarId", currentWarId);
        if (ftbTeamId   != null) tag.putUUID("ftbTeamId",   ftbTeamId);

        if (homePos != null) {
            tag.putIntArray("homePos", new int[]{homePos.getX(), homePos.getY(), homePos.getZ()});
            tag.putString("homeDim", homeDimensionId != null ? homeDimensionId : "minecraft:overworld");
        }

        // Members
        ListTag memberList = new ListTag();
        for (GuildMember m : members.values()) memberList.add(m.toNBT());
        tag.put("members", memberList);

        // Ranks
        ListTag rankList = new ListTag();
        for (GuildRank r : ranks) rankList.add(r.toNBT());
        tag.put("ranks", rankList);

        // Upgrades
        ListTag upgradeList = new ListTag();
        for (String u : purchasedUpgrades) upgradeList.add(StringTag.valueOf(u));
        tag.put("upgrades", upgradeList);

        // Allies
        ListTag allyList = new ListTag();
        for (UUID ally : alliedGuildIds) {
            CompoundTag a = new CompoundTag();
            a.putUUID("uuid", ally);
            allyList.add(a);
        }
        tag.put("allies", allyList);

        // Home cooldowns
        CompoundTag cdTag = new CompoundTag();
        for (Map.Entry<UUID, Long> e : homeCooldowns.entrySet())
            cdTag.putLong(e.getKey().toString(), e.getValue());
        tag.put("homeCooldowns", cdTag);

        // Activity log
        ListTag logList = new ListTag();
        for (String entry : activityLog) logList.add(StringTag.valueOf(entry));
        tag.put("activityLog", logList);

        return tag;
    }

    public static Guild fromNBT(CompoundTag tag) {
        UUID guildId    = tag.getUUID("guildId");
        String name     = tag.getString("name");
        String guildTag = tag.getString("tag");
        UUID leaderUUID = tag.getUUID("leaderUUID");

        Guild guild = new Guild(guildId, name, guildTag, leaderUUID);
        guild.members.clear();
        guild.ranks.clear();

        guild.bankBalance    = tag.getLong("bankBalance");
        guild.warEscrow      = tag.getLong("warEscrow");
        guild.level          = tag.getInt("level");
        guild.experience     = tag.getLong("experience");
        guild.friendlyFire   = tag.getBoolean("friendlyFire");
        guild.motd           = tag.getString("motd");
        guild.lastWarEndTime = tag.getLong("lastWarEndTime");

        guild.warWins        = tag.getInt("warWins");
        if (tag.contains("chatColor")) guild.chatColorName = tag.getString("chatColor");
        if (tag.hasUUID("currentWarId")) guild.currentWarId = tag.getUUID("currentWarId");
        if (tag.hasUUID("ftbTeamId"))   guild.ftbTeamId   = tag.getUUID("ftbTeamId");

        if (tag.contains("homePos")) {
            int[] pos = tag.getIntArray("homePos");
            guild.homePos = new BlockPos(pos[0], pos[1], pos[2]);
            guild.homeDimensionId = tag.getString("homeDim");
        }

        ListTag memberList = tag.getList("members", Tag.TAG_COMPOUND);
        for (Tag t : memberList) {
            GuildMember m = GuildMember.fromNBT((CompoundTag) t);
            guild.members.put(m.getPlayerUUID(), m);
        }

        ListTag rankList = tag.getList("ranks", Tag.TAG_COMPOUND);
        for (Tag t : rankList) guild.ranks.add(GuildRank.fromNBT((CompoundTag) t));

        ListTag upgradeList = tag.getList("upgrades", Tag.TAG_STRING);
        for (Tag t : upgradeList) guild.purchasedUpgrades.add(t.getAsString());

        ListTag allyList = tag.getList("allies", Tag.TAG_COMPOUND);
        for (Tag t : allyList) guild.alliedGuildIds.add(((CompoundTag) t).getUUID("uuid"));

        CompoundTag cdTag = tag.getCompound("homeCooldowns");
        for (String key : cdTag.getAllKeys())
            guild.homeCooldowns.put(UUID.fromString(key), cdTag.getLong(key));

        ListTag logList = tag.getList("activityLog", Tag.TAG_STRING);
        for (Tag t : logList) guild.activityLog.add(t.getAsString());

        return guild;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getGuildId()          { return guildId; }
    public String getName()           { return name; }
    public void setName(String n)     { this.name = n; }
    public String getTag()            { return tag; }
    public void setTag(String t)      { this.tag = t; }
    public UUID getLeaderUUID()       { return leaderUUID; }
    public void setLeaderUUID(UUID u) { this.leaderUUID = u; }
    public long getBankBalance()      { return bankBalance; }
    public void setBankBalance(long b){ this.bankBalance = b; }
    public long getWarEscrow()        { return warEscrow; }
    public int getLevel()             { return level; }
    public long getExperience()       { return experience; }

    /**
     * Directly overwrite the raw XP and recompute level from scratch.
     * Intended for admin commands only — normal gameplay should use addExperience().
     * @param xp       new total XP (clamped to ≥ 0)
     * @param maxLevel server's max guild level cap
     * @return number of levels gained (positive) or lost (negative) vs. old level
     */
    public int setExperienceAndRecalculate(long xp, int maxLevel) {
        int oldLevel  = this.level;
        this.experience = Math.max(0, xp);
        this.level      = 1;
        // Re-derive level from the new XP total
        while (this.level < maxLevel && this.experience >= totalXpForLevel(this.level + 1)) {
            this.level++;
        }
        return this.level - oldLevel;
    }
    public boolean isFriendlyFire()   { return friendlyFire; }
    public void setFriendlyFire(boolean ff) { this.friendlyFire = ff; }
    public String getMotd()           { return motd; }
    public void setMotd(String m)     { this.motd = m; }
    public BlockPos getHomePos()      { return homePos; }
    public void setHomePos(BlockPos p){ this.homePos = p; }
    public String getHomeDimensionId(){ return homeDimensionId; }
    public void setHomeDimensionId(String id) { this.homeDimensionId = id; }
    public Map<UUID, Long> getHomeCooldowns() { return homeCooldowns; }
    public UUID getCurrentWarId()     { return currentWarId; }
    public void setCurrentWarId(UUID id) { this.currentWarId = id; }
    public long getLastWarEndTime()   { return lastWarEndTime; }
    public void setLastWarEndTime(long t) { this.lastWarEndTime = t; }
    public UUID getFtbTeamId()        { return ftbTeamId; }
    public void setFtbTeamId(UUID id) { this.ftbTeamId = id; }
    public int getWarWins()            { return warWins; }
    public void addWarWin()            { warWins++; }
    public String getChatColorName()   { return chatColorName; }
    public void setChatColorName(String name) { this.chatColorName = name; }

    /** Resolves {@link #chatColorName} to a {@link ChatFormatting}, falling back to GOLD. */
    public ChatFormatting getChatColor() {
        ChatFormatting cf = ChatFormatting.getByName(chatColorName);
        return (cf != null && cf.isColor()) ? cf : ChatFormatting.GOLD;
    }
}
