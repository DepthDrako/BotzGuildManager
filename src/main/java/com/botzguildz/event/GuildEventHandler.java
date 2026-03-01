package com.botzguildz.event;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.*;
import com.botzguildz.dimension.ArenaManager;
import com.botzguildz.ftb.FTBBridge;
import com.botzguildz.upgrade.GuildUpgrade;
import com.botzguildz.upgrade.UpgradeRegistry;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;

import java.util.List;
import java.util.UUID;

public class GuildEventHandler {

    private int tickCounter = 0;

    // ── Server Tick ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;

        // Run war/duel cleanup every 20 ticks (1 second)
        if (tickCounter % 20 != 0) return;

        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);
        data.tick(server);

        // Re-sync FTB team colours every 5 minutes (6000 ticks) so in-session
        // colour changes made via the FTB Teams interface propagate to guild chat.
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
                    MessageUtils.broadcastToGuild(winnerGuild, MessageUtils.success("⚔ Your guild won the war! +" + CurrencyManager.format(war.getWinnerPayout()) + " added to the guild bank."), server);
                }
                if (loserGuild != null) {
                    MessageUtils.broadcastToGuild(loserGuild, MessageUtils.warn("⚔ Your guild lost the war."), server);
                }
                if (winner == null) {
                    // Draw
                    Guild g1 = data.getGuildById(war.getDeclaringGuildId());
                    Guild g2 = data.getGuildById(war.getChallengedGuildId());
                    if (g1 != null) MessageUtils.broadcastToGuild(g1, MessageUtils.info("⚔ The war ended in a draw. Wagers refunded."), server);
                    if (g2 != null) MessageUtils.broadcastToGuild(g2, MessageUtils.info("⚔ The war ended in a draw. Wagers refunded."), server);
                }
            }
        }
    }

    // ── Datapack sync — show upgrade validation errors to operators ───────────

    /**
     * Fires when datapacks are synced to a player (on join) or to all players (after /reload).
     * If the last upgrade-datapack reload had validation errors, we send them to any operator
     * so they know the mod fell back to demo data and why.
     */
    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        List<String> errors = UpgradeRegistry.INSTANCE.getErrors();
        if (errors.isEmpty()) return;

        // getPlayer() is non-null when syncing to a single player (e.g. on join).
        // It is null when syncing to everyone (e.g. after /reload).
        List<ServerPlayer> targets = (event.getPlayer() != null)
                ? List.of(event.getPlayer())
                : event.getPlayerList().getPlayers();

        Component header = Component.literal("[BotzGuildz] Upgrade datapack error — using demo data:")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        for (ServerPlayer player : targets) {
            if (!player.hasPermissions(2)) continue; // ops only
            player.sendSystemMessage(header);
            for (String err : errors) {
                player.sendSystemMessage(
                        Component.literal("  • " + err).withStyle(ChatFormatting.RED));
            }
        }
    }

    // ── Player Login ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerLogin(PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);
        Guild guild = data.getGuildByPlayer(player.getUUID());
        if (guild == null) return;

        GuildMember member = guild.getMember(player.getUUID());
        if (member == null) return;

        // Daily login rewards
        long now      = System.currentTimeMillis();
        long lastLogin = member.getLastLoginTime();
        boolean newDay = (now - lastLogin) > 86_400_000L; // 24 hours

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

        // Apply upgrade effects (Resistance, Regen on login)
        GuildUtils.applyUpgradeEffects(player, guild);

        // Show MOTD
        if (!guild.getMotd().isEmpty()) {
            player.sendSystemMessage(MessageUtils.info("[MOTD] " + guild.getMotd()));
        }

        // FTB Teams integration: ensure the player is in the guild's FTB party.
        // If the guild has no FTB team yet (e.g. created before FTB Teams was installed),
        // and this player is the leader, create the FTB team now and add all online members.
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

        // Sync FTB team colour → guild chat colour on every login so changes
        // made via the FTB Teams interface are immediately reflected.
        FTBBridge.syncTeamColorToGuild(guild, server);
        data.setDirty();
    }

    // ── Player Logout ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Reset daily flag on logout for next session
        GuildSavedData data = GuildSavedData.get(server);
        Guild guild = data.getGuildByPlayer(player.getUUID());
        if (guild != null) {
            GuildMember member = guild.getMember(player.getUUID());
            if (member != null) member.setOnlineToday(false);
        }
    }

    // ── Friendly Fire Prevention ──────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        if (!(target instanceof ServerPlayer targetPlayer)) return;
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;

        MinecraftServer server = attacker.getServer();
        if (server == null) return;

        // Always allow combat inside active war (arena or open world)
        GuildSavedData data = GuildSavedData.get(server);
        GuildWar attackerWar = data.getActiveWarForGuild(
                data.getGuildByPlayer(attacker.getUUID()) != null
                        ? data.getGuildByPlayer(attacker.getUUID()).getGuildId() : UUID.randomUUID());
        if (attackerWar != null && attackerWar.isParticipant(attacker.getUUID())
                && attackerWar.isParticipant(targetPlayer.getUUID())
                && !attackerWar.getParticipantGuild(attacker.getUUID())
                        .equals(attackerWar.getParticipantGuild(targetPlayer.getUUID()))) {
            return; // Enemy combatants during war — allow damage
        }

        // Friendly fire check
        if (GuildUtils.isFriendlyFireBlocked(attacker, targetPlayer, server)) {
            event.setCanceled(true);
        }
    }

    // ── Kill Tracking (War & Damage Multipliers) ──────────────────────────────

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();

        if (!(entity instanceof ServerPlayer victim)) return;
        if (!(source.getEntity() instanceof ServerPlayer killer)) return;

        MinecraftServer server = killer.getServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);

        // ── War kill tracking ──
        Guild killerGuild = data.getGuildByPlayer(killer.getUUID());
        Guild victimGuild = data.getGuildByPlayer(victim.getUUID());

        if (killerGuild != null) {
            GuildWar war = data.getActiveWarForGuild(killerGuild.getGuildId());
            if (war != null && war.isParticipant(killer.getUUID()) && war.isParticipant(victim.getUUID())) {
                boolean eliminated = war.recordKill(killer.getUUID(), victim.getUUID());
                data.setDirty();

                int livesLeft = war.getRemainingLives(victim.getUUID());
                MessageUtils.broadcastWarEvent(killerGuild,
                        killer.getName().getString() + " killed " + victim.getName().getString()
                                + "! (" + (victimGuild != null ? victimGuild.getName() : "?") + " lives left: " + livesLeft + ")",
                        server);
                if (victimGuild != null) {
                    MessageUtils.broadcastWarEvent(victimGuild,
                            victim.getName().getString() + " was killed! Lives left: " + livesLeft, server);
                }

                if (eliminated) {
                    victim.sendSystemMessage(MessageUtils.warn("You've used all your lives and are out of the war!"));
                    // In arena mode, teleport eliminated player back
                    if (war.getMode() == WarMode.ARENA) {
                        ArenaManager.returnPlayer(war, victim, server);
                    }
                }
            }
        }

        // ── Currency & XP on kill ──
        if (killerGuild != null && (victimGuild == null || !victimGuild.getGuildId().equals(killerGuild.getGuildId()))) {
            long earn = GuildUtils.applyEarnRate(killerGuild, GuildConfig.CURRENCY_PER_KILL.get());
            killerGuild.deposit(earn);
            int levelsGained = killerGuild.addExperience(GuildConfig.XP_PER_KILL.get(), GuildConfig.MAX_GUILD_LEVEL.get());
            data.setDirty();

            if (levelsGained > 0) {
                MessageUtils.broadcastToGuild(killerGuild,
                        MessageUtils.success("Guild leveled up to level " + killerGuild.getLevel() + "!"), server);
            }
        }
    }

    // ── Combat Damage Multiplier ──────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onAttackDamage(net.minecraftforge.event.entity.player.CriticalHitEvent event) {
        // Note: Full damage multiplier application uses LivingHurtEvent for accuracy
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        MinecraftServer server = attacker.getServer();
        if (server == null) return;

        Guild guild = GuildSavedData.get(server).getGuildByPlayer(attacker.getUUID());
        if (guild == null) return;

        double mult = GuildUtils.getDamageMultiplier(guild);
        if (mult > 1.0) {
            event.setAmount((float)(event.getAmount() * mult));
        }
    }
}
