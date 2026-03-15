package com.botzguildz.events;

import com.botzguildz.BotzGuildz;
import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.*;
import com.botzguildz.dimension.ArenaManager;
import com.botzguildz.ftb.FTBBridge;
import com.botzguildz.raid.RaidBossFight;
import com.botzguildz.raid.RaidManager;
import com.botzguildz.raid.RaidParty;
import com.botzguildz.upgrade.UpgradeRegistry;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.*;

/**
 * All cross-platform event logic as static methods.
 * Platform bridges (Forge / Fabric) call these from their respective event hooks.
 */
public final class CommonEventLogic {

    private CommonEventLogic() {}

    // ── Server Tick ───────────────────────────────────────────────────────────

    /**
     * Called every server tick (END phase).
     * {@code tickCounter} is maintained by the bridge (increments each END tick).
     */
    public static void onServerTick(MinecraftServer server, int tickCounter) {
        if (tickCounter % 20 != 0) return;

        GuildSavedData data = GuildSavedData.get(server);
        data.tick(server);

        // Re-sync FTB team colours every 5 minutes (6000 ticks)
        if (tickCounter % 6000 == 0 && FTBBridge.isTeamsAvailable()) {
            boolean colorChanged = false;
            for (Guild g : data.getAllGuilds()) {
                if (g.getFtbTeamId() == null) continue;
                String before = g.getChatColorName();
                FTBBridge.syncTeamColorToGuild(g, server);
                if (!before.equals(g.getChatColorName())) colorChanged = true;
            }
            if (colorChanged) data.setDirty();
        }

        // Check active war win conditions
        for (GuildWar war : java.util.List.copyOf(data.getAllWars())) {
            if (war.getState() != WarState.ACTIVE) continue;

            UUID winner = war.checkWinCondition();
            if (winner != null || war.isTimeExpired()) {
                Guild winnerGuild = winner != null ? data.getGuildById(winner) : null;
                Guild loserGuild  = winner != null ? data.getGuildById(war.getOpponent(winner)) : null;

                if (war.getMode() == WarMode.ARENA) {
                    ArenaManager.returnAllPlayers(war, server);
                }

                data.endWar(war, winner, server);

                if (winnerGuild != null) {
                    MessageUtils.broadcastToGuild(winnerGuild,
                            MessageUtils.success("Your guild won the war! +"
                                    + CurrencyManager.format(war.getWinnerPayout())
                                    + " added to the guild bank."), server);
                }
                if (loserGuild != null) {
                    MessageUtils.broadcastToGuild(loserGuild,
                            MessageUtils.warn("Your guild lost the war."), server);
                }
                if (winner == null) {
                    Guild g1 = data.getGuildById(war.getDeclaringGuildId());
                    Guild g2 = data.getGuildById(war.getChallengedGuildId());
                    if (g1 != null) MessageUtils.broadcastToGuild(g1,
                            MessageUtils.info("The war ended in a draw. Wagers refunded."), server);
                    if (g2 != null) MessageUtils.broadcastToGuild(g2,
                            MessageUtils.info("The war ended in a draw. Wagers refunded."), server);
                }
            }
        }
    }

    // ── Duel Player Tick (radius enforcement) ────────────────────────────────

    public static void onDuelPlayerTick(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);
        DuelData duel = data.getDuelByPlayer(player.getUUID());
        if (duel == null || duel.getState() != DuelState.ACTIVE) return;

        boolean isChallenger = player.getUUID().equals(duel.getChallengerId());
        BlockPos startPos = isChallenger ? duel.getChallengerStart() : duel.getChallengedStart();
        if (startPos == null) return;

        double dist = player.position().distanceTo(Vec3.atCenterOf(startPos));
        int radius = GuildConfig.DUEL_RADIUS_BLOCKS.get();
        if (dist > radius) {
            UUID opponentUUID = duel.getOpponent(player.getUUID());
            resolveDuel(duel, opponentUUID, data, server,
                    player.getName().getString() + " left the duel arena and forfeits!");
        }
    }

    // ── Datapack sync ─────────────────────────────────────────────────────────

    public static void onDatapackSync(@Nullable ServerPlayer singlePlayer, PlayerList allPlayers) {
        List<String> errors = UpgradeRegistry.INSTANCE.getErrors();
        if (errors.isEmpty()) return;

        List<ServerPlayer> targets = (singlePlayer != null)
                ? List.of(singlePlayer)
                : allPlayers.getPlayers();

        Component header = Component.literal("[BotzGuildz] Upgrade datapack error — using demo data:")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        for (ServerPlayer player : targets) {
            if (!player.hasPermissions(2)) continue;
            player.sendSystemMessage(header);
            for (String err : errors) {
                player.sendSystemMessage(
                        Component.literal("  - " + err).withStyle(ChatFormatting.RED));
            }
        }
    }

    // ── Player Login ──────────────────────────────────────────────────────────

    public static void onPlayerLogin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);
        Guild guild = data.getGuildByPlayer(player.getUUID());
        if (guild == null) return;

        GuildMember member = guild.getMember(player.getUUID());
        if (member == null) return;

        long now       = System.currentTimeMillis();
        long lastLogin = member.getLastLoginTime();
        boolean newDay = (now - lastLogin) > 86_400_000L;

        if (newDay || lastLogin == 0) {
            long earnRate = GuildUtils.applyEarnRate(guild, GuildConfig.CURRENCY_PER_LOGIN.get());
            guild.deposit(earnRate);
            int xp = GuildConfig.XP_PER_MEMBER_LOGIN.get();
            int levelsGained = guild.addExperience(xp, GuildConfig.MAX_GUILD_LEVEL.get());
            member.setLastLoginTime(now);
            member.setOnlineToday(true);
            data.setDirty();

            if (levelsGained > 0) {
                MessageUtils.broadcastToGuild(guild,
                        MessageUtils.success("Guild leveled up to level " + guild.getLevel() + "!"), server);
            }
        }

        GuildUtils.applyUpgradeEffects(player, guild);

        if (!guild.getMotd().isEmpty()) {
            player.sendSystemMessage(MessageUtils.info("[MOTD] " + guild.getMotd()));
        }

        if (guild.getFtbTeamId() == null && player.getUUID().equals(guild.getLeaderUUID())) {
            UUID ftbId = FTBBridge.createPartyForGuild(guild, player);
            if (ftbId != null) {
                guild.setFtbTeamId(ftbId);
                data.setDirty();
                FTBBridge.addAllOnlineMembersToTeam(guild, server);
            }
        } else {
            FTBBridge.syncPlayerToGuildTeam(guild, player);
        }

        FTBBridge.syncTeamColorToGuild(guild, server);
        data.setDirty();
    }

    // ── Player Logout ─────────────────────────────────────────────────────────

    public static void onPlayerLogout(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);

        Guild guild = data.getGuildByPlayer(player.getUUID());
        if (guild != null) {
            GuildMember member = guild.getMember(player.getUUID());
            if (member != null) member.setOnlineToday(false);
        }

        DuelData duel = data.getDuelByPlayer(player.getUUID());
        if (duel != null && duel.getState() == DuelState.ACTIVE) {
            UUID opponentUUID = duel.getOpponent(player.getUUID());
            resolveDuel(duel, opponentUUID, data, server,
                    player.getName().getString() + " logged out and forfeits the duel!");
        }
    }

    // ── Friendly Fire Prevention ──────────────────────────────────────────────

    public static boolean onLivingAttack(LivingEntity target, DamageSource source) {
        if (!(target instanceof ServerPlayer targetPlayer)) return false;
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return false;

        MinecraftServer server = attacker.getServer();
        if (server == null) return false;

        GuildSavedData data = GuildSavedData.get(server);
        Guild attackerGuild = data.getGuildByPlayer(attacker.getUUID());
        GuildWar attackerWar = data.getActiveWarForGuild(
                attackerGuild != null ? attackerGuild.getGuildId() : UUID.randomUUID());
        if (attackerWar != null
                && attackerWar.isParticipant(attacker.getUUID())
                && attackerWar.isParticipant(targetPlayer.getUUID())
                && !attackerWar.getParticipantGuild(attacker.getUUID())
                        .equals(attackerWar.getParticipantGuild(targetPlayer.getUUID()))) {
            return false;
        }

        return GuildUtils.isFriendlyFireBlocked(attacker, targetPlayer, server);
    }

    // ── Kill Tracking (War + Duel + Currency/XP) ─────────────────────────────

    public static void onLivingDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer victim)) return;
        if (!(source.getEntity() instanceof ServerPlayer killer)) return;
        if (victim.getUUID().equals(killer.getUUID())) return;

        MinecraftServer server = killer.getServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);

        // Duel kill tracking
        DuelData duel = data.getDuelByPlayer(victim.getUUID());
        if (duel != null && duel.getState() == DuelState.ACTIVE
                && duel.isParticipant(killer.getUUID())
                && duel.isParticipant(victim.getUUID())) {

            boolean outOfLives = duel.recordKill(killer.getUUID());
            data.setDirty();

            int victimLivesLeft = duel.getLivesLeft(victim.getUUID());
            MessageUtils.sendDuelMessage(killer,
                    "You killed " + victim.getName().getString()
                    + "! Their lives: " + victimLivesLeft);
            MessageUtils.sendDuelMessage(victim,
                    "You were killed by " + killer.getName().getString()
                    + ". Your lives remaining: " + victimLivesLeft);

            if (outOfLives) {
                resolveDuel(duel, killer.getUUID(), data, server,
                        killer.getName().getString() + " wins the duel against "
                        + victim.getName().getString() + "!");
            }
            return;
        }

        // War kill tracking
        Guild killerGuild = data.getGuildByPlayer(killer.getUUID());
        Guild victimGuild = data.getGuildByPlayer(victim.getUUID());

        if (killerGuild != null) {
            GuildWar war = data.getActiveWarForGuild(killerGuild.getGuildId());
            if (war != null && war.isParticipant(killer.getUUID())
                    && war.isParticipant(victim.getUUID())) {
                boolean eliminated = war.recordKill(killer.getUUID(), victim.getUUID());
                data.setDirty();

                int livesLeft = war.getRemainingLives(victim.getUUID());
                MessageUtils.broadcastWarEvent(killerGuild,
                        killer.getName().getString() + " killed "
                        + victim.getName().getString()
                        + "! (" + (victimGuild != null ? victimGuild.getName() : "?")
                        + " lives left: " + livesLeft + ")", server);
                if (victimGuild != null) {
                    MessageUtils.broadcastWarEvent(victimGuild,
                            victim.getName().getString()
                            + " was killed! Lives left: " + livesLeft, server);
                }
                if (eliminated) {
                    victim.sendSystemMessage(
                            MessageUtils.warn("You have used all your lives and are out of the war!"));
                    if (war.getMode() == WarMode.ARENA) {
                        ArenaManager.returnPlayer(war, victim, server);
                    }
                }
            }
        }

        // Currency and XP on kill
        if (killerGuild != null
                && (victimGuild == null
                    || !victimGuild.getGuildId().equals(killerGuild.getGuildId()))) {
            long earn = GuildUtils.applyEarnRate(killerGuild, GuildConfig.CURRENCY_PER_KILL.get());
            killerGuild.deposit(earn);
            int levelsGained = killerGuild.addExperience(
                    GuildConfig.XP_PER_KILL.get(), GuildConfig.MAX_GUILD_LEVEL.get());
            data.setDirty();

            if (levelsGained > 0) {
                MessageUtils.broadcastToGuild(killerGuild,
                        MessageUtils.success("Guild leveled up to level "
                                + killerGuild.getLevel() + "!"), server);
            }
        }
    }

    // ── Combat Damage Multiplier ──────────────────────────────────────────────

    public static float onLivingHurt(LivingEntity entity, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return amount;
        MinecraftServer server = attacker.getServer();
        if (server == null) return amount;

        Guild guild = GuildSavedData.get(server).getGuildByPlayer(attacker.getUUID());
        if (guild == null) return amount;

        double mult = GuildUtils.getDamageMultiplier(guild);
        return (mult > 1.0) ? (float) (amount * mult) : amount;
    }

    // ── Raid Damage Tracking ──────────────────────────────────────────────────

    public static void onRaidDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity.level() instanceof ServerLevel)) return;
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        if (entity.getMaxHealth() < 100f) return;

        UUID bossId = entity.getUUID();
        Optional<RaidParty> partyOpt = RaidManager.INSTANCE.getPartyOf(attacker.getUUID());
        if (partyOpt.isEmpty()) return;
        RaidParty party = partyOpt.get();

        Optional<RaidBossFight> existingFight = RaidManager.INSTANCE.getActiveFightForParty(party);

        if (existingFight.isEmpty()) {
            Optional<RaidBossFight> newFight = RaidManager.INSTANCE.tryClaimBoss(party, entity);
            if (newFight.isEmpty()) return;

            MinecraftServer server = attacker.getServer();
            String bossName = entity.getName().getString();
            for (UUID memberId : party.getMembers()) {
                RaidManager.INSTANCE.sendMessage(memberId,
                        ChatFormatting.GOLD + "Raid fight started against " + bossName
                        + "! Deal damage to earn your share of the loot.", server);
            }
            RaidManager.INSTANCE.recordDamage(bossId, attacker.getUUID(), amount);

        } else if (existingFight.get().getBossEntityId().equals(bossId)) {
            RaidManager.INSTANCE.recordDamage(bossId, attacker.getUUID(), amount);
        }
    }

    // ── Boss Drop Redistribution ──────────────────────────────────────────────

    /**
     * Redistributes boss loot among raid participants.
     * Returns true if normal drops should be suppressed (raid handled the loot).
     */
    public static boolean onBossDrops(LivingEntity entity,
                                       List<ItemStack> drops,
                                       @Nullable ServerLevel serverLevel) {
        if (serverLevel == null) return false;

        UUID bossId = entity.getUUID();
        Optional<RaidBossFight> fightOpt = RaidManager.INSTANCE.getFight(bossId);
        if (fightOpt.isEmpty()) return false;

        RaidBossFight fight = fightOpt.get();

        if (fight.getParticipants().size() < 2) {
            RaidManager.INSTANCE.closeFight(bossId);
            return false;
        }

        MinecraftServer server = serverLevel.getServer();
        double bossX = entity.getX();
        double bossY = entity.getY();
        double bossZ = entity.getZ();

        Map<UUID, Integer> itemsReceived = new HashMap<>();
        for (UUID uid : fight.getParticipants()) itemsReceived.put(uid, 0);

        for (ItemStack stack : drops) {
            int total = stack.getCount();
            if (total == 0) continue;

            Map<UUID, Integer> alloc = splitProportionally(fight, total);

            for (Map.Entry<UUID, Integer> entry : alloc.entrySet()) {
                UUID uid  = entry.getKey();
                int  give = entry.getValue();
                if (give <= 0) continue;

                ItemStack slice = stack.copy();
                slice.setCount(give);

                ServerPlayer recipient = server.getPlayerList().getPlayer(uid);
                if (recipient != null) {
                    if (!recipient.getInventory().add(slice)) {
                        serverLevel.addFreshEntity(new ItemEntity(serverLevel,
                                recipient.getX(), recipient.getY(), recipient.getZ(), slice));
                    }
                } else {
                    serverLevel.addFreshEntity(
                            new ItemEntity(serverLevel, bossX, bossY, bossZ, slice));
                }
                itemsReceived.merge(uid, give, Integer::sum);
            }
        }

        for (UUID uid : fight.getParticipants()) {
            float dmg   = fight.getDamageDealt(uid);
            float share = fight.getShare(uid) * 100f;
            int   items = itemsReceived.getOrDefault(uid, 0);
            String line = "Raid complete! " + fight.getBossName()
                    + " | Your damage: " + String.format("%.1f", dmg)
                    + " (" + String.format("%.1f", share) + "%)"
                    + " | Items received: " + items;
            RaidManager.INSTANCE.sendMessage(uid, line, server);
        }

        RaidManager.INSTANCE.closeFight(bossId);
        return true;
    }

    // ── Duel resolution ───────────────────────────────────────────────────────

    private static void resolveDuel(DuelData duel, UUID winnerUUID, GuildSavedData data,
                                    MinecraftServer server, String announcement) {
        if (duel.getState() == DuelState.ENDED) return;

        ServerPlayer winner = winnerUUID != null
                ? server.getPlayerList().getPlayer(winnerUUID) : null;
        ServerPlayer loser  = winnerUUID != null
                ? server.getPlayerList().getPlayer(duel.getOpponent(winnerUUID)) : null;

        if (duel.getWageredAmountPerPlayer() > 0 && winnerUUID != null && winner != null) {
            long payout = duel.getWinnerPayout();
            CurrencyManager.give(winner, payout);
            MessageUtils.sendDuelMessage(winner,
                    "You won the duel! +" + CurrencyManager.format(payout) + " paid out.");
            if (loser != null)
                MessageUtils.sendDuelMessage(loser, "You lost the duel. Wager forfeited.");
        }

        if (winner != null) MessageUtils.sendDuelMessage(winner, announcement);
        if (loser  != null) MessageUtils.sendDuelMessage(loser,  announcement);

        Guild winnerGuild = winnerUUID != null ? data.getGuildByPlayer(winnerUUID) : null;
        if (winnerGuild != null && winner != null && loser != null) {
            winnerGuild.addLog(winner.getName().getString()
                    + " won a duel against " + loser.getName().getString() + ".");
        }

        data.endDuel(duel, winnerUUID);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Map<UUID, Integer> splitProportionally(RaidBossFight fight, int total) {
        Map<UUID, Integer> result = new HashMap<>();
        int sumFloors = 0;

        for (UUID uid : fight.getParticipants()) {
            float share = fight.getShare(uid);
            int   floor = (int) Math.floor(total * share);
            result.put(uid, floor);
            sumFloors += floor;
        }

        int remainder = total - sumFloors;
        if (remainder > 0) {
            UUID top = fight.getHighestDamageDealer();
            if (top != null) result.merge(top, remainder, Integer::sum);
        }

        return result;
    }
}
