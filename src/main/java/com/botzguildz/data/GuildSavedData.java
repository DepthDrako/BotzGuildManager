package com.botzguildz.data;

import com.botzguildz.BotzGuildz;
import com.botzguildz.config.GuildConfig;
import com.botzguildz.ftb.FTBBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Server-side persistent storage for all guild data.
 * Saved to the overworld's data folder as "botzguildz_data.dat".
 *
 * Access via GuildSavedData.get(server).
 */
public class GuildSavedData extends SavedData {

    public static final String DATA_NAME = "botzguildz_data";

    // ── Storage Maps ──────────────────────────────────────────────────────────

    /** All guilds, keyed by guild UUID. */
    private final Map<UUID, Guild> guilds = new LinkedHashMap<>();

    /** Lowercase guild name -> guild UUID, for fast name-based lookup. */
    private final Map<String, UUID> guildsByName = new HashMap<>();

    /** Player UUID -> guild UUID. */
    private final Map<UUID, UUID> playerToGuild = new HashMap<>();

    /** All active and pending wars, keyed by war UUID. */
    private final Map<UUID, GuildWar> wars = new LinkedHashMap<>();

    /** All active and pending duels, keyed by duel UUID. */
    private final Map<UUID, DuelData> duels = new LinkedHashMap<>();

    /** Player UUID -> duel UUID, for fast player->duel lookup. */
    private final Map<UUID, UUID> playerToDuel = new HashMap<>();

    /** Personal currency wallets (used in physical-item mode). Player UUID -> balance. */
    private final Map<UUID, Long> playerWallets = new HashMap<>();

    /** Player UUID -> lifetime duel wins. */
    private final Map<UUID, Integer> playerDuelWins = new HashMap<>();

    /**
     * When true, guild wars skip arena generation and always battle in the
     * fixed production arena at (CUSTOM_ARENA_CX, CUSTOM_ARENA_CZ).
     * Admins enable this via /guild arena edit and disable via /guild arena reset.
     */
    private boolean customArena = false;

    // ── Access ────────────────────────────────────────────────────────────────

    public static GuildSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                GuildSavedData::load,
                GuildSavedData::new,
                DATA_NAME
        );
    }

    // ── Guild CRUD ────────────────────────────────────────────────────────────

    /**
     * Create a new guild and register the leader as its first member.
     * Returns null if name or tag already exists, or player is already in a guild.
     */
    public Guild createGuild(String name, String tag, ServerPlayer leader) {
        if (playerToGuild.containsKey(leader.getUUID())) return null;
        if (guildsByName.containsKey(name.toLowerCase())) return null;

        Guild guild = new Guild(UUID.randomUUID(), name, tag, leader.getUUID());
        GuildMember leaderMember = new GuildMember(leader.getUUID(), leader.getName().getString(), "Leader");
        guild.addMember(leaderMember);

        guilds.put(guild.getGuildId(), guild);
        guildsByName.put(name.toLowerCase(), guild.getGuildId());
        playerToGuild.put(leader.getUUID(), guild.getGuildId());
        guild.addLog(leader.getName().getString() + " founded the guild.");

        // FTB Teams integration: create a matching party team for the new guild.
        UUID ftbId = FTBBridge.createPartyForGuild(guild, leader);
        if (ftbId != null) guild.setFtbTeamId(ftbId);

        setDirty();
        return guild;
    }

    /**
     * Disband a guild entirely. Cleans up all references, ends any active war.
     */
    public void disbandGuild(UUID guildId, MinecraftServer server) {
        Guild guild = guilds.get(guildId);
        if (guild == null) return;

        // FTB Teams integration: disband the FTB party before cleaning up guild data.
        FTBBridge.disbandGuildTeam(guild, server);

        // End any active war
        if (guild.getCurrentWarId() != null) {
            GuildWar war = wars.get(guild.getCurrentWarId());
            if (war != null) endWar(war, null, server); // null = draw/cancelled
        }

        // Remove all members from playerToGuild
        for (UUID memberUUID : guild.getMembersMap().keySet()) {
            playerToGuild.remove(memberUUID);
        }

        guildsByName.remove(guild.getName().toLowerCase());
        guilds.remove(guildId);
        setDirty();
    }

    // ── Member Management ─────────────────────────────────────────────────────

    public boolean addMemberToGuild(UUID guildId, ServerPlayer player) {
        Guild guild = guilds.get(guildId);
        if (guild == null) return false;
        if (playerToGuild.containsKey(player.getUUID())) return false;

        // Check member cap (base + upgrade bonuses applied in GuildUtils)
        int maxMembers = GuildConfig.BASE_MAX_MEMBERS.get();
        if (guild.hasUpgrade("MEMBER_SLOTS_I"))   maxMembers += 10;
        if (guild.hasUpgrade("MEMBER_SLOTS_II"))  maxMembers += 20;
        if (guild.hasUpgrade("MEMBER_SLOTS_III")) maxMembers += 30;
        if (guild.getMemberCount() >= maxMembers) return false;

        GuildRank defaultRank = guild.getDefaultRank();
        String rankName = defaultRank != null ? defaultRank.getName() : "Recruit";
        GuildMember member = new GuildMember(player.getUUID(), player.getName().getString(), rankName);
        guild.addMember(member);
        playerToGuild.put(player.getUUID(), guildId);
        guild.addLog(player.getName().getString() + " joined the guild.");

        // FTB Teams integration: add the new member to the guild's FTB party.
        FTBBridge.syncPlayerToGuildTeam(guild, player);

        setDirty();
        return true;
    }

    public boolean removeMemberFromGuild(UUID playerUUID, String reason) {
        UUID guildId = playerToGuild.get(playerUUID);
        if (guildId == null) return false;
        Guild guild = guilds.get(guildId);
        if (guild == null) return false;

        GuildMember member = guild.getMember(playerUUID);
        String name = member != null ? member.getPlayerName() : playerUUID.toString();
        guild.removeMember(playerUUID);
        playerToGuild.remove(playerUUID);
        guild.addLog(name + " " + reason + ".");
        setDirty();
        return true;
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public Guild getGuildById(UUID guildId)     { return guilds.get(guildId); }
    public Collection<Guild> getAllGuilds()      { return guilds.values(); }

    public Guild getGuildByName(String name) {
        UUID id = guildsByName.get(name.toLowerCase());
        return id != null ? guilds.get(id) : null;
    }

    public Guild getGuildByPlayer(UUID playerUUID) {
        UUID guildId = playerToGuild.get(playerUUID);
        return guildId != null ? guilds.get(guildId) : null;
    }

    public boolean isInGuild(UUID playerUUID) { return playerToGuild.containsKey(playerUUID); }

    // ── Wars ──────────────────────────────────────────────────────────────────

    /**
     * Declare a war. Locks escrow on the declaring guild immediately.
     * Returns the GuildWar, or null if either guild is already in a war.
     */
    public GuildWar declareWar(Guild declaring, Guild challenged, long wager) {
        if (declaring.getCurrentWarId() != null) return null;
        if (challenged.getCurrentWarId() != null) return null;
        if (!declaring.lockEscrow(wager)) return null;

        long expiry = System.currentTimeMillis()
                + (GuildConfig.WAR_ACCEPT_TIMEOUT_SECONDS.get() * 1000L);
        GuildWar war = new GuildWar(declaring.getGuildId(), challenged.getGuildId(), wager, expiry);

        wars.put(war.getWarId(), war);
        declaring.setCurrentWarId(war.getWarId());
        challenged.setCurrentWarId(war.getWarId());
        declaring.addLog("Declared war on " + challenged.getName() + " with a " + wager + " wager.");
        challenged.addLog(declaring.getName() + " declared war with a " + wager + " wager.");
        setDirty();
        return war;
    }

    /**
     * Accept a pending war. Locks escrow on the challenged guild.
     * @return false if the challenged guild can't cover the wager.
     */
    public boolean acceptWar(GuildWar war, Guild challenged, int lives, WarMode mode,
                             List<UUID> declaringOnline, List<UUID> challengedOnline,
                             MinecraftServer server) {
        if (war.getState() != WarState.PENDING) return false;
        if (!challenged.lockEscrow(war.getWageredAmountPerSide())) return false;

        long durationMs = GuildConfig.ARENA_WAR_DURATION_SECONDS.get() * 1000L;
        war.activate(lives, mode, durationMs, declaringOnline, challengedOnline);

        Guild declaring = guilds.get(war.getDeclaringGuildId());
        if (declaring != null) declaring.addLog(challenged.getName() + " accepted the war. Lives: " + lives + ".");
        challenged.addLog("War against " + (declaring != null ? declaring.getName() : "unknown") + " started. Lives: " + lives + ".");
        setDirty();
        return true;
    }

    /**
     * End a war, award the pot, and clean up.
     * @param winnerGuildId null = draw (both guilds get their wager back, no bonus).
     */
    public void endWar(GuildWar war, UUID winnerGuildId, MinecraftServer server) {
        war.setState(WarState.ENDED);
        war.setWinnerGuildId(winnerGuildId);

        Guild declaring  = guilds.get(war.getDeclaringGuildId());
        Guild challenged = guilds.get(war.getChallengedGuildId());

        if (winnerGuildId == null) {
            // Draw: refund both sides
            if (declaring  != null) declaring.releaseEscrow(war.getWageredAmountPerSide());
            if (challenged != null) challenged.releaseEscrow(war.getWageredAmountPerSide());
            if (declaring  != null) declaring.addLog("War ended in a draw vs " + (challenged != null ? challenged.getName() : "unknown") + ". Wagers refunded.");
            if (challenged != null) challenged.addLog("War ended in a draw vs " + (declaring  != null ? declaring.getName()  : "unknown") + ". Wagers refunded.");
        } else {
            Guild winner = guilds.get(winnerGuildId);
            Guild loser  = winnerGuildId.equals(war.getDeclaringGuildId()) ? challenged : declaring;

            if (winner != null && loser != null) {
                // Loser pays out their escrow
                loser.payOutEscrow(war.getWageredAmountPerSide());
                // Winner gets their escrow back + full pot + 10%
                winner.releaseEscrow(war.getWageredAmountPerSide());
                winner.deposit(war.getWinnerPayout());

                // Grant XP and record win
                winner.addExperience(GuildConfig.XP_PER_WAR_WIN.get(), GuildConfig.MAX_GUILD_LEVEL.get());
                winner.addWarWin();

                winner.addLog("Won the war against " + loser.getName() + "! Received " + war.getWinnerPayout() + ".");
                loser.addLog("Lost the war against " + winner.getName() + ". Wager forfeited.");
            }
        }

        // Cleanup
        if (declaring  != null) {
            declaring.setCurrentWarId(null);
            declaring.setLastWarEndTime(System.currentTimeMillis());
        }
        if (challenged != null) {
            challenged.setCurrentWarId(null);
            challenged.setLastWarEndTime(System.currentTimeMillis());
        }
        wars.remove(war.getWarId());
        setDirty();
    }

    public GuildWar getWar(UUID warId)   { return wars.get(warId); }
    public Collection<GuildWar> getAllWars() { return wars.values(); }

    /**
     * Remove a war entry from storage without any payout or escrow manipulation.
     * Used when a pending war is cancelled/denied — the caller handles escrow cleanup.
     */
    public void removeWarEntry(UUID warId) {
        wars.remove(warId);
        setDirty();
    }

    /** Get the pending war that was declared against this guild (they need to respond to it). */
    public GuildWar getPendingWarTargetingGuild(UUID guildId) {
        return wars.values().stream()
                .filter(w -> w.getState() == WarState.PENDING && w.getChallengedGuildId().equals(guildId))
                .findFirst().orElse(null);
    }

    /** Get the pending war that this guild declared (they are waiting for acceptance). */
    public GuildWar getPendingWarDeclaredByGuild(UUID guildId) {
        return wars.values().stream()
                .filter(w -> w.getState() == WarState.PENDING && w.getDeclaringGuildId().equals(guildId))
                .findFirst().orElse(null);
    }

    public GuildWar getActiveWarForGuild(UUID guildId) {
        return wars.values().stream()
                .filter(w -> w.getState() == WarState.ACTIVE
                        && (w.getDeclaringGuildId().equals(guildId) || w.getChallengedGuildId().equals(guildId)))
                .findFirst().orElse(null);
    }

    // ── Duels ─────────────────────────────────────────────────────────────────

    /**
     * Create a new duel challenge.
     * Returns null if either player is already in a duel.
     */
    public DuelData createDuel(UUID challengerId, UUID challengedId,
                               long wagerPerPlayer, int lives) {
        if (playerToDuel.containsKey(challengerId)) return null;
        if (playerToDuel.containsKey(challengedId)) return null;

        long expiry = System.currentTimeMillis()
                + (GuildConfig.DUEL_TIMEOUT_SECONDS.get() * 1000L);
        DuelData duel = new DuelData(challengerId, challengedId, wagerPerPlayer, lives, expiry);

        duels.put(duel.getDuelId(), duel);
        playerToDuel.put(challengerId, duel.getDuelId());
        playerToDuel.put(challengedId, duel.getDuelId());
        setDirty();
        return duel;
    }

    public void activateDuel(DuelData duel, net.minecraft.core.BlockPos cPos,
                             net.minecraft.core.BlockPos dPos) {
        duel.activate(cPos, dPos);
        setDirty();
    }

    /**
     * End a duel and award the pot.
     * @param winnerUUID null = draw (wagers refunded from wallet context by caller).
     */
    public void endDuel(DuelData duel, UUID winnerUUID) {
        // Track duel wins only for completed active duels (not expired/cancelled pending ones)
        if (winnerUUID != null && duel.getState() == DuelState.ACTIVE) {
            playerDuelWins.merge(winnerUUID, 1, Integer::sum);
        }
        duel.setState(DuelState.ENDED);
        duel.setWinnerUUID(winnerUUID);

        playerToDuel.remove(duel.getChallengerId());
        playerToDuel.remove(duel.getChallengedId());
        duels.remove(duel.getDuelId());
        setDirty();
    }

    public DuelData getDuel(UUID duelId)          { return duels.get(duelId); }
    public DuelData getDuelByPlayer(UUID playerUUID) {
        UUID duelId = playerToDuel.get(playerUUID);
        return duelId != null ? duels.get(duelId) : null;
    }
    public Collection<DuelData> getAllDuels()      { return duels.values(); }
    public boolean isInDuel(UUID playerUUID)       { return playerToDuel.containsKey(playerUUID); }

    // ── Player Wallets ────────────────────────────────────────────────────────

    public long getWallet(UUID playerUUID) { return playerWallets.getOrDefault(playerUUID, 0L); }

    public void addToWallet(UUID playerUUID, long amount) {
        playerWallets.merge(playerUUID, amount, Long::sum);
        setDirty();
    }

    /** Returns false if insufficient funds. */
    public boolean deductFromWallet(UUID playerUUID, long amount) {
        long current = getWallet(playerUUID);
        if (current < amount) return false;
        playerWallets.put(playerUUID, current - amount);
        setDirty();
        return true;
    }

    // ── Leaderboard Queries ───────────────────────────────────────────────────

    /** Returns top N guilds sorted by available bank balance, descending. */
    public List<Guild> getTopGuildsByBalance(int n) {
        return guilds.values().stream()
                .sorted(Comparator.comparingLong(Guild::getAvailableBalance).reversed())
                .limit(n)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Returns top N guilds sorted by all-time war wins, descending. */
    public List<Guild> getTopGuildsByWarWins(int n) {
        return guilds.values().stream()
                .sorted(Comparator.comparingInt(Guild::getWarWins).reversed())
                .limit(n)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Returns top N players sorted by lifetime duel wins, descending. */
    public List<Map.Entry<UUID, Integer>> getTopPlayersByDuelWins(int n) {
        return playerDuelWins.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(n)
                .collect(java.util.stream.Collectors.toList());
    }

    public int getDuelWins(UUID playerUUID) { return playerDuelWins.getOrDefault(playerUUID, 0); }

    // ── Tick (called from event handler every server tick) ────────────────────

    /**
     * Clean up expired pending wars and duels, resolve time-expired active wars.
     * Should be called on the server tick.
     */
    public void tick(MinecraftServer server) {
        // Expired pending wars
        new ArrayList<>(wars.values()).stream()
                .filter(GuildWar::isPendingExpired)
                .forEach(war -> {
                    Guild declaring = guilds.get(war.getDeclaringGuildId());
                    Guild challenged = guilds.get(war.getChallengedGuildId());
                    if (declaring != null) {
                        declaring.releaseEscrow(war.getWageredAmountPerSide());
                        declaring.setCurrentWarId(null);
                        declaring.addLog("War declaration to " +
                                (challenged != null ? challenged.getName() : "unknown") + " expired.");
                    }
                    if (challenged != null) {
                        challenged.setCurrentWarId(null);
                    }
                    wars.remove(war.getWarId());
                    setDirty();
                });

        // Time-expired active wars
        new ArrayList<>(wars.values()).stream()
                .filter(w -> w.getState() == WarState.ACTIVE && w.isTimeExpired())
                .forEach(war -> {
                    UUID winner = war.checkWinCondition(); // may be null for draw
                    endWar(war, winner, server);
                });

        // Expired pending duels
        new ArrayList<>(duels.values()).stream()
                .filter(DuelData::isPendingExpired)
                .forEach(duel -> endDuel(duel, null));
    }

    // ── NBT Serialization ─────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        // Guilds
        ListTag guildList = new ListTag();
        for (Guild g : guilds.values()) guildList.add(g.toNBT());
        tag.put("guilds", guildList);

        // Player -> Guild map
        CompoundTag p2g = new CompoundTag();
        for (Map.Entry<UUID, UUID> e : playerToGuild.entrySet())
            p2g.putUUID(e.getKey().toString(), e.getValue());
        tag.put("playerToGuild", p2g);

        // Wars
        ListTag warList = new ListTag();
        for (GuildWar w : wars.values()) warList.add(w.toNBT());
        tag.put("wars", warList);

        // Duels
        ListTag duelList = new ListTag();
        for (DuelData d : duels.values()) duelList.add(d.toNBT());
        tag.put("duels", duelList);

        // Player -> Duel map
        CompoundTag p2d = new CompoundTag();
        for (Map.Entry<UUID, UUID> e : playerToDuel.entrySet())
            p2d.putUUID(e.getKey().toString(), e.getValue());
        tag.put("playerToDuel", p2d);

        // Wallets
        CompoundTag wallets = new CompoundTag();
        for (Map.Entry<UUID, Long> e : playerWallets.entrySet())
            wallets.putLong(e.getKey().toString(), e.getValue());
        tag.put("playerWallets", wallets);

        // Duel wins
        CompoundTag duelWinsTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> e : playerDuelWins.entrySet())
            duelWinsTag.putInt(e.getKey().toString(), e.getValue());
        tag.put("playerDuelWins", duelWinsTag);

        // Arena settings
        tag.putBoolean("customArena", customArena);

        return tag;
    }

    public static GuildSavedData load(CompoundTag tag) {
        GuildSavedData data = new GuildSavedData();

        ListTag guildList = tag.getList("guilds", Tag.TAG_COMPOUND);
        for (Tag t : guildList) {
            Guild g = Guild.fromNBT((CompoundTag) t);
            data.guilds.put(g.getGuildId(), g);
            data.guildsByName.put(g.getName().toLowerCase(), g.getGuildId());
        }

        CompoundTag p2g = tag.getCompound("playerToGuild");
        for (String key : p2g.getAllKeys())
            data.playerToGuild.put(UUID.fromString(key), p2g.getUUID(key));

        ListTag warList = tag.getList("wars", Tag.TAG_COMPOUND);
        for (Tag t : warList) {
            GuildWar w = GuildWar.fromNBT((CompoundTag) t);
            data.wars.put(w.getWarId(), w);
        }

        ListTag duelList = tag.getList("duels", Tag.TAG_COMPOUND);
        for (Tag t : duelList) {
            DuelData d = DuelData.fromNBT((CompoundTag) t);
            data.duels.put(d.getDuelId(), d);
        }

        CompoundTag p2d = tag.getCompound("playerToDuel");
        for (String key : p2d.getAllKeys())
            data.playerToDuel.put(UUID.fromString(key), p2d.getUUID(key));

        CompoundTag wallets = tag.getCompound("playerWallets");
        for (String key : wallets.getAllKeys())
            data.playerWallets.put(UUID.fromString(key), wallets.getLong(key));

        CompoundTag duelWinsTag = tag.getCompound("playerDuelWins");
        for (String key : duelWinsTag.getAllKeys())
            data.playerDuelWins.put(UUID.fromString(key), duelWinsTag.getInt(key));

        // Arena settings (graceful default: false = auto-generate per war)
        if (tag.contains("customArena")) data.customArena = tag.getBoolean("customArena");

        return data;
    }

    // ── Arena settings ────────────────────────────────────────────────────────

    public boolean isCustomArena() { return customArena; }

    public void setCustomArena(boolean value) {
        this.customArena = value;
        this.setDirty();
    }
}
