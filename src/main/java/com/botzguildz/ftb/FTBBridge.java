package com.botzguildz.ftb;

import com.botzguildz.BotzGuildz;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildMember;
import com.botzguildz.data.GuildSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Soft-dependency bridge for FTB Teams, FTB Quests, and FTB Chunks.
 * Uses pure reflection — none of these mods are required at runtime.
 *
 * Integration overview:
 *  - FTB Teams:  Each guild automatically gets its own FTB party team.
 *                Guild membership changes (join/kick/leave/disband) are mirrored.
 *  - FTB Quests: No extra code required — FTB Quests tracks quest progress per
 *                FTB Team, so guild members complete quests together once they
 *                share the same FTB party.  The mirror above handles this for free.
 *  - FTB Chunks: CHUNK_CLAIM_I, CHUNK_CLAIM_II, and CHUNK_FORCE_LOAD upgrades
 *                raise the guild's FTB team extra-claim / force-load quotas.
 */
public class FTBBridge {

    private static boolean teamsAvailable  = false;
    private static boolean chunksAvailable = false;

    /** Cached FTBTeamsAPI singleton (set during init if mod is present). */
    private static Object teamsApiInstance  = null;

    /** Cached FTBChunksAPI singleton (set during init if mod is present). */
    private static Object chunksApiInstance = null;

    // ── Initialisation ────────────────────────────────────────────────────────

    /** Called once from FMLCommonSetupEvent (via BotzGuildz.commonSetup). */
    public static void init() {
        teamsAvailable  = initTeams();
        chunksAvailable = initChunks();
        boolean questsLoaded = ModList.get().isLoaded("ftbquests");
        BotzGuildz.LOGGER.info(
                "[BotzGuildz] FTB Bridge — Teams={}, Chunks={}, Quests={} " +
                "(quest sync is automatic: guild members share one FTB party).",
                teamsAvailable, chunksAvailable, questsLoaded);
    }

    public static boolean isTeamsAvailable()  { return teamsAvailable; }
    public static boolean isChunksAvailable() { return chunksAvailable; }

    private static boolean initTeams() {
        if (!ModList.get().isLoaded("ftbteams")) return false;
        try {
            Class<?> cls = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Method api   = cls.getMethod("api");
            teamsApiInstance = api.invoke(null);
            BotzGuildz.LOGGER.info("[BotzGuildz] FTB Teams API loaded successfully.");
            return true;
        } catch (Exception e) {
            BotzGuildz.LOGGER.warn("[BotzGuildz] FTB Teams detected but API failed to init: {}", e.getMessage());
            return false;
        }
    }

    private static boolean initChunks() {
        if (!ModList.get().isLoaded("ftbchunks")) return false;
        try {
            Class<?> cls = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
            Method api   = cls.getMethod("api");
            chunksApiInstance = api.invoke(null);
            BotzGuildz.LOGGER.info("[BotzGuildz] FTB Chunks API loaded successfully.");
            return true;
        } catch (Exception e) {
            BotzGuildz.LOGGER.warn("[BotzGuildz] FTB Chunks detected but API failed to init: {}", e.getMessage());
            return false;
        }
    }

    // ── Low-level reflection helpers ──────────────────────────────────────────

    /** Returns the named public method, or null if it doesn't exist. */
    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        try { return cls.getMethod(name, params); }
        catch (NoSuchMethodException e) { return null; }
    }

    /** Unwraps an Optional result; returns it as-is if it is not an Optional. */
    private static Object unwrapOptional(Object o) {
        if (o instanceof Optional<?> opt) return opt.orElse(null);
        return o;
    }

    // ── FTB Teams — server-side manager ───────────────────────────────────────

    /** Returns the FTBTeams server-side TeamManager, or null. */
    private static Object getTeamManager(MinecraftServer server) {
        if (teamsApiInstance == null) return null;
        for (String name : new String[]{"getServerManager", "getManager"}) {
            Method m = findMethod(teamsApiInstance.getClass(), name);
            if (m == null) continue;
            try {
                Object mgr = m.invoke(teamsApiInstance);
                if (mgr != null) return mgr;
            } catch (Exception ignored) {}
        }
        BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: getTeamManager — no suitable method found.");
        return null;
    }

    /** Returns the raw FTBTeams Team object for the given UUID, or null. */
    private static Object getTeamByID(Object manager, UUID teamId) {
        if (manager == null || teamId == null) return null;
        for (String name : new String[]{"getTeamByID", "getTeamById", "getById"}) {
            Method m = findMethod(manager.getClass(), name, UUID.class);
            if (m == null) continue;
            try { return unwrapOptional(m.invoke(manager, teamId)); }
            catch (Exception ignored) {}
        }
        return null;
    }

    /** Returns the raw FTBTeams Team object the player currently belongs to, or null. */
    private static Object getTeamForPlayer(Object manager, UUID playerUUID) {
        if (manager == null) return null;
        for (String name : new String[]{"getTeamForPlayerID", "getTeamForPlayer", "getPlayerTeam"}) {
            Method m = findMethod(manager.getClass(), name, UUID.class);
            if (m == null) continue;
            try { return unwrapOptional(m.invoke(manager, playerUUID)); }
            catch (Exception ignored) {}
        }
        return null;
    }

    /** Returns the UUID of a raw FTBTeams Team object, or null. */
    private static UUID getTeamId(Object team) {
        if (team == null) return null;
        for (String name : new String[]{"getId", "getTeamId"}) {
            Method m = findMethod(team.getClass(), name);
            if (m == null) continue;
            try {
                Object r = m.invoke(team);
                if (r instanceof UUID id) return id;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Returns true if the FTBTeams Team contains the given player UUID. */
    private static boolean hasMemberInTeam(Object team, UUID playerUUID) {
        if (team == null) return false;
        for (String name : new String[]{"hasMember", "containsMember", "isMember"}) {
            Method m = findMethod(team.getClass(), name, UUID.class);
            if (m == null) continue;
            try {
                Object r = m.invoke(team, playerUUID);
                if (r instanceof Boolean b) return b;
            } catch (Exception ignored) {}
        }
        return false;
    }

    // ── FTB Teams — party creation ────────────────────────────────────────────

    /**
     * Create an FTB party team named after the guild, with {@code leader} as owner.
     * The caller is responsible for persisting the returned UUID on {@code guild}.
     *
     * @return the new FTB team UUID, or {@code null} if creation failed or FTB Teams
     *         is not installed.
     */
    public static UUID createPartyForGuild(Guild guild, ServerPlayer leader) {
        if (!teamsAvailable || leader == null) return null;
        try {
            Object manager = getTeamManager(leader.getServer());
            if (manager == null) return null;

            Class<?> mgCls   = manager.getClass();
            String   teamName = guild.getName();
            boolean  created  = false;

            // Try several plausible method names — API versions vary across FTBTeams releases.
            outer:
            for (String methodName : new String[]{"createPartyTeam", "createParty", "createNewTeam", "createPlayerParty"}) {
                // With Component display-name arg
                Method m = findMethod(mgCls, methodName, ServerPlayer.class, Component.class);
                if (m != null) {
                    try { m.invoke(manager, leader, Component.literal(teamName)); created = true; break outer; }
                    catch (Exception ignored) {}
                }
                // With String name arg
                m = findMethod(mgCls, methodName, ServerPlayer.class, String.class);
                if (m != null) {
                    try { m.invoke(manager, leader, teamName); created = true; break outer; }
                    catch (Exception ignored) {}
                }
                // With only ServerPlayer (auto-named)
                m = findMethod(mgCls, methodName, ServerPlayer.class);
                if (m != null) {
                    try { m.invoke(manager, leader); created = true; break outer; }
                    catch (Exception ignored) {}
                }
            }

            if (!created) {
                BotzGuildz.LOGGER.warn("[BotzGuildz] FTBBridge: Could not find party-creation method on {}.", mgCls.getName());
                return null;
            }

            // After creation the leader belongs to the new team — retrieve its ID.
            Object newTeam = getTeamForPlayer(manager, leader.getUUID());
            UUID   id      = getTeamId(newTeam);
            if (id != null) {
                BotzGuildz.LOGGER.info("[BotzGuildz] FTBBridge: Created FTB party '{}' ({}) for guild '{}'.",
                        teamName, id, guild.getName());
            }
            return id;

        } catch (Exception e) {
            BotzGuildz.LOGGER.warn("[BotzGuildz] FTBBridge: createPartyForGuild failed: {}", e.getMessage());
            return null;
        }
    }

    // ── FTB Teams — member synchronisation ───────────────────────────────────

    /**
     * Ensure {@code player} is a member of the guild's FTB party.
     * Safe to call on every login or immediately after a player joins the guild.
     */
    public static void syncPlayerToGuildTeam(Guild guild, ServerPlayer player) {
        if (!teamsAvailable || guild.getFtbTeamId() == null) return;
        try {
            Object manager = getTeamManager(player.getServer());
            if (manager == null) return;

            Object team = getTeamByID(manager, guild.getFtbTeamId());
            if (team == null) {
                BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: FTB team {} no longer exists for guild '{}'.",
                        guild.getFtbTeamId(), guild.getName());
                return;
            }

            if (!hasMemberInTeam(team, player.getUUID())) {
                addPlayerToTeam(manager, team, guild.getFtbTeamId(), player);
            }
        } catch (Exception e) {
            BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: syncPlayerToGuildTeam failed: {}", e.getMessage());
        }
    }

    /** Internal: add an online player to a specific FTB team (tries multiple API surfaces). */
    private static void addPlayerToTeam(Object manager, Object team, UUID teamId, ServerPlayer player) {
        Class<?> mgCls = manager.getClass();

        // manager.addMemberToTeam / joinTeam with (UUID, ServerPlayer) or (UUID, UUID)
        for (String name : new String[]{"addMemberToTeam", "joinTeam", "addPlayer", "addToTeam"}) {
            Method m = findMethod(mgCls, name, UUID.class, ServerPlayer.class);
            if (m != null) { try { m.invoke(manager, teamId, player); return; } catch (Exception ignored) {} }

            m = findMethod(mgCls, name, UUID.class, UUID.class);
            if (m != null) { try { m.invoke(manager, teamId, player.getUUID()); return; } catch (Exception ignored) {} }
        }

        // team.addMember / addPlayer with (ServerPlayer) or (UUID)
        if (team != null) {
            for (String name : new String[]{"addMember", "addPlayer", "join"}) {
                Method m = findMethod(team.getClass(), name, ServerPlayer.class);
                if (m != null) { try { m.invoke(team, player); return; } catch (Exception ignored) {} }

                m = findMethod(team.getClass(), name, UUID.class);
                if (m != null) { try { m.invoke(team, player.getUUID()); return; } catch (Exception ignored) {} }
            }
        }

        BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: addPlayerToTeam — no suitable method found on {}.", mgCls.getName());
    }

    /**
     * Remove a player (online or offline) from the guild's FTB party.
     * Called after kick or leave events.
     *
     * @param guild      the guild whose FTB team to modify
     * @param playerUUID the player being removed
     * @param server     the running server (provides online-player lookup)
     */
    public static void removePlayerFromGuildTeam(Guild guild, UUID playerUUID, MinecraftServer server) {
        if (!teamsAvailable || guild.getFtbTeamId() == null || server == null) return;
        try {
            Object manager = getTeamManager(server);
            if (manager == null) return;

            Class<?> mgCls  = manager.getClass();
            UUID     teamId = guild.getFtbTeamId();

            // UUID-based removal (works even when the player is offline)
            for (String name : new String[]{"kickMember", "removeMember", "removePlayerFromTeam", "kickPlayer"}) {
                Method m = findMethod(mgCls, name, UUID.class, UUID.class);
                if (m != null) { try { m.invoke(manager, teamId, playerUUID); return; } catch (Exception ignored) {} }
            }

            // Team-object-based removal
            Object team = getTeamByID(manager, teamId);
            if (team != null) {
                for (String name : new String[]{"removeMember", "kickMember", "removePlayer"}) {
                    Method m = findMethod(team.getClass(), name, UUID.class);
                    if (m != null) { try { m.invoke(team, playerUUID); return; } catch (Exception ignored) {} }
                }
            }

            // Fall back to ServerPlayer-based leave (only if the player is currently online)
            ServerPlayer online = server.getPlayerList().getPlayer(playerUUID);
            if (online != null) {
                for (String name : new String[]{"leaveTeam", "playerLeavesTeam", "quitTeam"}) {
                    Method m = findMethod(mgCls, name, ServerPlayer.class);
                    if (m != null) { try { m.invoke(manager, online); return; } catch (Exception ignored) {} }
                }
            }

            BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: removePlayerFromGuildTeam — no suitable method found.");
        } catch (Exception e) {
            BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: removePlayerFromGuildTeam failed: {}", e.getMessage());
        }
    }

    /**
     * Disband / delete the guild's FTB party.  Called when a guild is disbanded.
     */
    public static void disbandGuildTeam(Guild guild, MinecraftServer server) {
        if (!teamsAvailable || guild.getFtbTeamId() == null || server == null) return;
        try {
            Object manager = getTeamManager(server);
            if (manager == null) return;

            Class<?> mgCls  = manager.getClass();
            UUID     teamId = guild.getFtbTeamId();

            for (String name : new String[]{"disbandTeam", "deleteTeam", "removeTeam", "disbandParty"}) {
                // (UUID) overload
                Method m = findMethod(mgCls, name, UUID.class);
                if (m != null) { try { m.invoke(manager, teamId); return; } catch (Exception ignored) {} }

                // (Team) overload — try with the raw team object
                Object team = getTeamByID(manager, teamId);
                if (team != null) {
                    // Probe every interface the team object implements (Team, PartyTeam, etc.)
                    for (Class<?> iface : team.getClass().getInterfaces()) {
                        m = findMethod(mgCls, name, iface);
                        if (m != null) { try { m.invoke(manager, team); return; } catch (Exception ignored) {} }
                    }
                    m = findMethod(mgCls, name, team.getClass());
                    if (m != null) { try { m.invoke(manager, team); return; } catch (Exception ignored) {} }
                }
            }

            BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: disbandGuildTeam — no suitable method found.");
        } catch (Exception e) {
            BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: disbandGuildTeam failed: {}", e.getMessage());
        }
    }

    /**
     * Add all currently online guild members to the guild's FTB party.
     * Used when the FTB team is first created for a pre-existing guild (e.g. the
     * first time the leader logs in after FTB Teams is installed).
     */
    public static void addAllOnlineMembersToTeam(Guild guild, MinecraftServer server) {
        if (!teamsAvailable || guild.getFtbTeamId() == null || server == null) return;
        try {
            Object manager = getTeamManager(server);
            if (manager == null) return;
            Object team = getTeamByID(manager, guild.getFtbTeamId());

            for (GuildMember member : guild.getAllMembers()) {
                if (member.getPlayerUUID().equals(guild.getLeaderUUID())) continue; // already the owner
                ServerPlayer online = server.getPlayerList().getPlayer(member.getPlayerUUID());
                if (online != null) addPlayerToTeam(manager, team, guild.getFtbTeamId(), online);
            }
        } catch (Exception e) {
            BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: addAllOnlineMembersToTeam failed: {}", e.getMessage());
        }
    }

    // ── FTB Teams — colour sync ───────────────────────────────────────────────

    /**
     * Read the FTB team's current colour and write it to {@code guild.chatColorName}.
     * No-op when FTB Teams is not installed or the guild has no FTB team yet.
     * Call this on player login and periodically from the server tick.
     */
    public static void syncTeamColorToGuild(Guild guild, MinecraftServer server) {
        if (!teamsAvailable || guild.getFtbTeamId() == null || server == null) return;
        try {
            Object manager = getTeamManager(server);
            if (manager == null) return;
            Object team = getTeamByID(manager, guild.getFtbTeamId());
            if (team == null) return;

            ChatFormatting color = readTeamColor(team);
            if (color != null && color.isColor()) {
                guild.setChatColorName(color.name());
                BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: Synced team color {} to guild '{}'.",
                        color.name(), guild.getName());
            }
        } catch (Exception e) {
            BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: syncTeamColorToGuild failed: {}", e.getMessage());
        }
    }

    /**
     * Try to extract a {@link ChatFormatting} colour from a raw FTB Teams Team object.
     * Probes {@code getColor()}, {@code getChatColor()}, and {@code getChatFormatting()}
     * and handles both direct {@code ChatFormatting} returns and intermediate {@code TeamColor} enums.
     */
    private static ChatFormatting readTeamColor(Object team) {
        for (String name : new String[]{"getColor", "getChatColor", "getChatFormatting"}) {
            Method m = findMethod(team.getClass(), name);
            if (m == null) continue;
            try {
                Object result = m.invoke(team);
                if (result instanceof ChatFormatting cf) return cf;
                if (result != null) {
                    // Intermediate TeamColor enum — try to extract ChatFormatting from it
                    ChatFormatting cf = extractChatFormatting(result);
                    if (cf != null) return cf;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Given an unknown colour object (likely a {@code TeamColor} enum), attempt to
     * obtain the corresponding {@link ChatFormatting} via several plausible methods,
     * or by mapping the enum constant name directly.
     */
    private static ChatFormatting extractChatFormatting(Object colorObj) {
        // Direct accessor methods
        for (String name : new String[]{"getChatFormatting", "getFormatting", "toChatFormatting", "chatFormatting"}) {
            Method m = findMethod(colorObj.getClass(), name);
            if (m == null) continue;
            try {
                Object result = m.invoke(colorObj);
                if (result instanceof ChatFormatting cf) return cf;
            } catch (Exception ignored) {}
        }
        // Enum name → ChatFormatting lookup (e.g. TeamColor.RED → ChatFormatting.RED)
        if (colorObj.getClass().isEnum()) {
            try {
                String enumName = ((Enum<?>) colorObj).name();
                ChatFormatting cf = ChatFormatting.getByName(enumName);
                if (cf != null && cf.isColor()) return cf;
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ── FTB Chunks — chunk-claim bonus ───────────────────────────────────────

    /**
     * Apply the guild's current chunk-upgrade bonuses to its FTB party's claim quotas.
     *
     * <p>Upgrade → extra chunks granted:
     * <ul>
     *   <li>CHUNK_CLAIM_I  → +10 claim slots</li>
     *   <li>CHUNK_CLAIM_II → +10 more (= +20 cumulative)</li>
     *   <li>CHUNK_FORCE_LOAD → +10 force-load slots</li>
     * </ul>
     *
     * <p>Call this after any of those upgrades is purchased, and on server start to
     * re-apply saved state.  No-op if FTBChunks or FTBTeams is not installed.
     */
    public static void applyChunkClaimBonus(Guild guild, MinecraftServer server) {
        if (!chunksAvailable || !teamsAvailable || guild.getFtbTeamId() == null || server == null) return;
        try {
            // ── Resolve FTBTeams team ──
            Object teamsManager = getTeamManager(server);
            if (teamsManager == null) return;

            Object team = getTeamByID(teamsManager, guild.getFtbTeamId());
            if (team == null) return;

            // ── Resolve FTBChunks manager ──
            Object chunksMgr = getChunksManager();
            if (chunksMgr == null) return;

            // ── Resolve per-team chunk data ──
            Object teamData = getChunkTeamData(chunksMgr, team, guild.getFtbTeamId());
            if (teamData == null) {
                BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: Could not get FTBChunks team data for guild '{}'.", guild.getName());
                return;
            }

            // ── Compute desired quotas ──
            int extraClaims    = 0;
            if (guild.hasUpgrade("CHUNK_CLAIM_I"))  extraClaims += 10;
            if (guild.hasUpgrade("CHUNK_CLAIM_II")) extraClaims += 10; // cumulative: 20 total
            int extraForceLoad = guild.hasUpgrade("CHUNK_FORCE_LOAD") ? 10 : 0;

            // ── Apply ──
            setChunkProperty(teamData, extraClaims,
                    "setExtraClaimChunks", "setMaxClaimChunks", "setExtraClaims", "setClaimedChunksLimit");
            setChunkProperty(teamData, extraForceLoad,
                    "setExtraForceLoadChunks", "setMaxForceLoadChunks", "setExtraForceLoads", "setForceLoadedChunksLimit");

            // ── Persist ──
            for (String name : new String[]{"save", "markDirty", "setDirty"}) {
                Method m = findMethod(teamData.getClass(), name);
                if (m != null) { try { m.invoke(teamData); break; } catch (Exception ignored) {} }
            }

            BotzGuildz.LOGGER.debug("[BotzGuildz] FTBBridge: Applied chunk bonus +{} claims / +{} force-loads to guild '{}'.",
                    extraClaims, extraForceLoad, guild.getName());

        } catch (Exception e) {
            BotzGuildz.LOGGER.warn("[BotzGuildz] FTBBridge: applyChunkClaimBonus failed: {}", e.getMessage());
        }
    }

    /** Returns the FTBChunks server-side ClaimedChunkManager, or null. */
    private static Object getChunksManager() {
        if (chunksApiInstance == null) return null;
        for (String name : new String[]{"getServerManager", "getManager"}) {
            Method m = findMethod(chunksApiInstance.getClass(), name);
            if (m == null) continue;
            try {
                Object mgr = m.invoke(chunksApiInstance);
                if (mgr != null) return mgr;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Returns the ClaimedChunkTeamData object for the given FTBTeams team object,
     * trying both the team-object and the team-UUID overloads.
     */
    private static Object getChunkTeamData(Object chunksMgr, Object team, UUID teamId) {
        Class<?> mgCls = chunksMgr.getClass();

        // Try every interface the team object implements (Team, PartyTeam, …)
        Class<?>[] ifaces = team.getClass().getInterfaces();
        for (Class<?> iface : ifaces) {
            for (String name : new String[]{"getTeamData", "getChunkTeamData", "getData"}) {
                Method m = findMethod(mgCls, name, iface);
                if (m != null) {
                    try { Object d = m.invoke(chunksMgr, team); if (d != null) return d; }
                    catch (Exception ignored) {}
                }
            }
        }
        // Raw class
        for (String name : new String[]{"getTeamData", "getChunkTeamData", "getData"}) {
            Method m = findMethod(mgCls, name, team.getClass());
            if (m != null) {
                try { Object d = m.invoke(chunksMgr, team); if (d != null) return d; }
                catch (Exception ignored) {}
            }
        }
        // UUID overload
        for (String name : new String[]{"getTeamData", "getChunkTeamData", "getData"}) {
            Method m = findMethod(mgCls, name, UUID.class);
            if (m != null) {
                try { Object d = m.invoke(chunksMgr, teamId); if (d != null) return d; }
                catch (Exception ignored) {}
            }
        }
        return null;
    }

    /** Try each method name in order until one succeeds at setting an int value. */
    private static void setChunkProperty(Object teamData, int value, String... methodNames) {
        for (String name : methodNames) {
            Method m = findMethod(teamData.getClass(), name, int.class);
            if (m != null) { try { m.invoke(teamData, value); return; } catch (Exception ignored) {} }
        }
    }
}
