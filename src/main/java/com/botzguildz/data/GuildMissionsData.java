package com.botzguildz.data;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.util.MessageUtils;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Persistent store for all active guild missions.
 * Saved to {@code botzguildz_missions.dat} in the overworld data folder.
 *
 * <p>Three missions are maintained per guild at a time, auto-generated whenever
 * the active count drops below three (on the server tick or on first access).
 * Missions expire after 24 hours and are replaced by newly generated ones.
 */
public class GuildMissionsData extends SavedData {

    private static final String DATA_NAME          = "botzguildz_missions";
    private static final int    MISSIONS_PER_GUILD = 3;
    static final         long   MISSION_DURATION_MS = 86_400_000L; // 24 h

    // ── Mission pool ──────────────────────────────────────────────────────────

    private static final List<String[]> KILL_POOL = List.of(
            new String[]{"minecraft:zombie",          "Zombie"},
            new String[]{"minecraft:skeleton",        "Skeleton"},
            new String[]{"minecraft:spider",          "Spider"},
            new String[]{"minecraft:creeper",         "Creeper"},
            new String[]{"minecraft:enderman",        "Enderman"},
            new String[]{"minecraft:witch",           "Witch"},
            new String[]{"minecraft:blaze",           "Blaze"},
            new String[]{"minecraft:wither_skeleton", "Wither Skeleton"},
            new String[]{"minecraft:drowned",         "Drowned"},
            new String[]{"minecraft:husk",            "Husk"},
            new String[]{"minecraft:pillager",        "Pillager"},
            new String[]{"minecraft:vindicator",      "Vindicator"}
    );

    private static final List<String[]> MINE_POOL = List.of(
            new String[]{"minecraft:iron_ore",            "Iron Ore"},
            new String[]{"minecraft:gold_ore",            "Gold Ore"},
            new String[]{"minecraft:coal_ore",            "Coal Ore"},
            new String[]{"minecraft:diamond_ore",         "Diamond Ore"},
            new String[]{"minecraft:emerald_ore",         "Emerald Ore"},
            new String[]{"minecraft:deepslate_iron_ore",  "Deepslate Iron Ore"},
            new String[]{"minecraft:deepslate_gold_ore",  "Deepslate Gold Ore"},
            new String[]{"minecraft:copper_ore",          "Copper Ore"},
            new String[]{"minecraft:lapis_ore",           "Lapis Ore"},
            new String[]{"minecraft:redstone_ore",        "Redstone Ore"}
    );

    private static final List<String[]> COLLECT_POOL = List.of(
            new String[]{"minecraft:wheat",       "Wheat"},
            new String[]{"minecraft:potato",      "Potato"},
            new String[]{"minecraft:carrot",      "Carrot"},
            new String[]{"minecraft:melon_slice", "Melon Slice"},
            new String[]{"minecraft:sugar_cane",  "Sugar Cane"},
            new String[]{"minecraft:apple",       "Apple"},
            new String[]{"minecraft:string",      "String"},
            new String[]{"minecraft:bone",        "Bone"},
            new String[]{"minecraft:gunpowder",   "Gunpowder"},
            new String[]{"minecraft:feather",     "Feather"},
            new String[]{"minecraft:ink_sac",     "Ink Sac"},
            new String[]{"minecraft:leather",     "Leather"}
    );

    // ── State ─────────────────────────────────────────────────────────────────

    /** All known missions keyed by missionId. */
    private final Map<UUID, GuildMissionEntry> missions     = new LinkedHashMap<>();
    /** guildId → ordered list of missionIds (active + recently completed). */
    private final Map<UUID, List<UUID>>        guildMissions = new LinkedHashMap<>();

    // ── Static access ─────────────────────────────────────────────────────────

    public static GuildMissionsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                GuildMissionsData::load,
                GuildMissionsData::new,
                DATA_NAME
        );
    }

    // ── Generation ────────────────────────────────────────────────────────────

    /**
     * Fills a guild's active missions up to {@link #MISSIONS_PER_GUILD}.
     * Uses a deterministic seed so the same guild gets the same missions
     * for the same 24 h window across restarts.
     */
    public void generateMissionsForGuild(Guild guild) {
        UUID guildId = guild.getGuildId();
        long now     = System.currentTimeMillis();
        long window  = now / MISSION_DURATION_MS; // changes every 24 h
        long seed    = guildId.getLeastSignificantBits() ^ window;
        Random rng   = new Random(seed);

        List<UUID> list = guildMissions.computeIfAbsent(guildId, k -> new ArrayList<>());

        // Prune dead entries
        list.removeIf(id -> { GuildMissionEntry m = missions.get(id); return m == null || !m.isActive(); });

        int needed = MISSIONS_PER_GUILD - list.size();
        if (needed <= 0) return;

        Set<String> usedTargets = new HashSet<>();
        for (UUID id : list) { GuildMissionEntry m = missions.get(id); if (m != null) usedTargets.add(m.getTargetId()); }

        GuildMissionEntry.MissionType[] types = GuildMissionEntry.MissionType.values();

        for (int i = 0; i < needed; i++) {
            GuildMissionEntry.MissionType type = types[rng.nextInt(types.length)];
            List<String[]> pool = switch (type) {
                case KILL    -> KILL_POOL;
                case MINE    -> MINE_POOL;
                case COLLECT -> COLLECT_POOL;
            };

            // Pick a unique target for this guild window
            String[] entry = null;
            for (int attempt = 0; attempt < 20; attempt++) {
                String[] candidate = pool.get(rng.nextInt(pool.size()));
                if (!usedTargets.contains(candidate[0])) { entry = candidate; break; }
            }
            if (entry == null) entry = pool.get(rng.nextInt(pool.size()));
            usedTargets.add(entry[0]);

            int guildLevel = guild.getLevel();
            int base = switch (type) {
                case KILL    -> 15 + rng.nextInt(20);
                case MINE    -> 20 + rng.nextInt(30);
                case COLLECT -> 25 + rng.nextInt(40);
            };
            int required = Math.max(5, base + (guildLevel - 1) * 5);

            long guildReward  = (100L + (long) guildLevel * 50 + rng.nextInt(200))
                    * (type == GuildMissionEntry.MissionType.MINE ? 2 : 1);
            long playerReward = 25L + (long) guildLevel * 10;

            GuildMissionEntry mission = new GuildMissionEntry(
                    UUID.randomUUID(), guildId, type,
                    entry[0], entry[1],
                    required, guildReward, playerReward,
                    now + MISSION_DURATION_MS
            );
            missions.put(mission.getMissionId(), mission);
            list.add(mission.getMissionId());
        }
        setDirty();
    }

    /** Returns active missions for the guild, generating new ones if needed. */
    public List<GuildMissionEntry> getOrGenerateMissions(Guild guild) {
        List<UUID> ids = guildMissions.computeIfAbsent(guild.getGuildId(), k -> new ArrayList<>());
        long active = ids.stream().map(missions::get).filter(m -> m != null && m.isActive()).count();
        if (active < MISSIONS_PER_GUILD) generateMissionsForGuild(guild);
        return guildMissions.getOrDefault(guild.getGuildId(), List.of()).stream()
                .map(missions::get).filter(m -> m != null && m.isActive()).toList();
    }

    /** Returns active missions without auto-generating. */
    public List<GuildMissionEntry> getActiveMissions(UUID guildId) {
        return guildMissions.getOrDefault(guildId, List.of()).stream()
                .map(missions::get).filter(m -> m != null && m.isActive()).toList();
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    /**
     * Records {@code amount} units of progress for a player toward any
     * matching active missions for their guild.
     *
     * @return missions that were <em>just</em> completed by this update.
     */
    public List<GuildMissionEntry> recordProgress(UUID guildId,
                                                  GuildMissionEntry.MissionType type,
                                                  String targetId,
                                                  UUID playerUUID,
                                                  int amount) {
        List<GuildMissionEntry> completed = new ArrayList<>();
        for (UUID id : guildMissions.getOrDefault(guildId, List.of())) {
            GuildMissionEntry m = missions.get(id);
            if (m == null || !m.isActive()) continue;
            if (m.getType() == type && m.getTargetId().equals(targetId)) {
                if (m.addProgress(playerUUID, amount)) completed.add(m);
                setDirty();
            }
        }
        return completed;
    }

    /** Awards rewards for a completed mission, then broadcasts to the guild. */
    public void awardMission(GuildMissionEntry mission, Guild guild, MinecraftServer server) {
        guild.deposit(mission.getGuildReward());
        guild.addLog("Mission complete: " + mission.getType().label + " " + mission.getDisplayName()
                + " — +" + CurrencyManager.format(mission.getGuildReward()) + " to guild bank.");

        for (UUID playerUUID : mission.getContributions().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                CurrencyManager.give(player, mission.getPlayerReward());
                player.sendSystemMessage(MessageUtils.success(
                        "✔ Guild mission complete: " + mission.getType().label + " "
                        + mission.getDisplayName() + "! You received "
                        + CurrencyManager.format(mission.getPlayerReward()) + "."));
            }
            // Award contribution points to every contributor (online or not)
            guild.addContribution(playerUUID, mission.getContributions().getOrDefault(playerUUID, 0));
        }

        MessageUtils.broadcastToGuild(guild,
                MessageUtils.success("✔ Mission complete: " + mission.getType().label
                        + " " + mission.getDisplayName() + "! +"
                        + CurrencyManager.format(mission.getGuildReward()) + " added to guild bank."),
                server);

        GuildSavedData.get(server).setDirty();
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    /**
     * Called periodically from the server tick.
     * Cleans up old entries and auto-generates missions for guilds that need them.
     */
    public void tick(MinecraftServer server) {
        long cutoff = System.currentTimeMillis() - MISSION_DURATION_MS * 2; // 48 h
        missions.values().removeIf(m -> !m.isActive() && m.getExpiresAt() < cutoff);
        for (List<UUID> ids : guildMissions.values()) ids.removeIf(id -> !missions.containsKey(id));

        for (Guild guild : GuildSavedData.get(server).getAllGuilds()) {
            if (getActiveMissions(guild.getGuildId()).size() < MISSIONS_PER_GUILD) {
                generateMissionsForGuild(guild);
            }
        }
    }

    // ── SavedData ─────────────────────────────────────────────────────────────

    public static GuildMissionsData load(CompoundTag tag) {
        GuildMissionsData data = new GuildMissionsData();
        ListTag mList = tag.getList("missions", Tag.TAG_COMPOUND);
        for (Tag t : mList) {
            GuildMissionEntry m = GuildMissionEntry.fromNBT((CompoundTag) t);
            data.missions.put(m.getMissionId(), m);
        }
        CompoundTag gMap = tag.getCompound("guildMissions");
        for (String key : gMap.getAllKeys()) {
            UUID guildId = UUID.fromString(key);
            ListTag idList = gMap.getList(key, Tag.TAG_STRING);
            List<UUID> ids = new ArrayList<>();
            for (Tag t : idList) ids.add(UUID.fromString(t.getAsString()));
            data.guildMissions.put(guildId, ids);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag mList = new ListTag();
        for (GuildMissionEntry m : missions.values()) mList.add(m.toNBT());
        tag.put("missions", mList);

        CompoundTag gMap = new CompoundTag();
        for (Map.Entry<UUID, List<UUID>> e : guildMissions.entrySet()) {
            ListTag idList = new ListTag();
            for (UUID id : e.getValue()) idList.add(StringTag.valueOf(id.toString()));
            gMap.put(e.getKey().toString(), idList);
        }
        tag.put("guildMissions", gMap);
        return tag;
    }
}
