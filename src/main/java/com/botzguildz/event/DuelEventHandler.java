package com.botzguildz.event;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.DuelData;
import com.botzguildz.data.DuelState;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.util.MessageUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;

import java.util.UUID;

public class DuelEventHandler {

    private int tickCounter = 0;

    // ── Tick: Radius Enforcement ──────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // Only check every 20 ticks (1s) to save performance
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);
        DuelData duel = data.getDuelByPlayer(player.getUUID());
        if (duel == null || duel.getState() != DuelState.ACTIVE) return;

        // Find this player's starting position
        boolean isChallenger = player.getUUID().equals(duel.getChallengerId());
        net.minecraft.core.BlockPos startPos = isChallenger ? duel.getChallengerStart() : duel.getChallengedStart();
        if (startPos == null) return;

        double dist = player.position().distanceTo(
                net.minecraft.world.phys.Vec3.atCenterOf(startPos));

        int radius = com.botzguildz.config.GuildConfig.DUEL_RADIUS_BLOCKS.get();
        if (dist > radius) {
            // Player left the arena — forfeit
            UUID opponentUUID = duel.getOpponent(player.getUUID());
            resolveDuel(duel, opponentUUID, data, server,
                    player.getName().getString() + " left the duel arena and forfeits!");
        }
    }

    // ── Kill Tracking ─────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();

        if (!(entity instanceof ServerPlayer victim)) return;
        if (!(source.getEntity() instanceof ServerPlayer killer)) return;
        if (victim.getUUID().equals(killer.getUUID())) return;

        MinecraftServer server = killer.getServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);
        DuelData duel = data.getDuelByPlayer(victim.getUUID());

        if (duel == null || duel.getState() != DuelState.ACTIVE) return;
        if (!duel.isParticipant(killer.getUUID()) || !duel.isParticipant(victim.getUUID())) return;

        boolean outOfLives = duel.recordKill(killer.getUUID());
        data.setDirty();

        // Status update
        int killerLivesLeft = duel.getLivesLeft(killer.getUUID());
        int victimLivesLeft = duel.getLivesLeft(victim.getUUID());
        MessageUtils.sendDuelMessage(killer, "You killed " + victim.getName().getString()
                + "! Their lives: " + victimLivesLeft);
        MessageUtils.sendDuelMessage(victim, "You were killed by " + killer.getName().getString()
                + ". Your lives remaining: " + victimLivesLeft);

        if (outOfLives) {
            // Victim is eliminated — killer wins
            resolveDuel(duel, killer.getUUID(), data, server,
                    killer.getName().getString() + " wins the duel against " + victim.getName().getString() + "!");
        }
    }

    // ── Forfeit on Logout ─────────────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);
        DuelData duel = data.getDuelByPlayer(player.getUUID());
        if (duel == null || duel.getState() != DuelState.ACTIVE) return;

        UUID opponentUUID = duel.getOpponent(player.getUUID());
        resolveDuel(duel, opponentUUID, data, server,
                player.getName().getString() + " logged out and forfeits the duel!");
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    private void resolveDuel(DuelData duel, UUID winnerUUID, GuildSavedData data,
                             MinecraftServer server, String announcement) {
        if (duel.getState() == DuelState.ENDED) return;

        ServerPlayer winner = winnerUUID != null ? server.getPlayerList().getPlayer(winnerUUID) : null;
        ServerPlayer loser  = winnerUUID != null ? server.getPlayerList().getPlayer(duel.getOpponent(winnerUUID)) : null;

        // Pay out wager
        if (duel.getWageredAmountPerPlayer() > 0 && winnerUUID != null && winner != null) {
            long payout = duel.getWinnerPayout();
            CurrencyManager.give(winner, payout);
            MessageUtils.sendDuelMessage(winner, "You won the duel! +" + CurrencyManager.format(payout) + " paid out.");
            if (loser != null)
                MessageUtils.sendDuelMessage(loser, "You lost the duel. Wager forfeited.");
        }

        // Announce
        if (winner != null) MessageUtils.sendDuelMessage(winner, announcement);
        if (loser  != null) MessageUtils.sendDuelMessage(loser,  announcement);

        // Log to guilds if applicable
        Guild winnerGuild = winnerUUID != null ? data.getGuildByPlayer(winnerUUID) : null;
        if (winnerGuild != null && winner != null && loser != null) {
            winnerGuild.addLog(winner.getName().getString() + " won a duel against " + loser.getName().getString() + ".");
        }

        data.endDuel(duel, winnerUUID);
    }
}
