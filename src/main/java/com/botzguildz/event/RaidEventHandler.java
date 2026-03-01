package com.botzguildz.event;

import com.botzguildz.raid.RaidBossFight;
import com.botzguildz.raid.RaidManager;
import com.botzguildz.raid.RaidParty;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Listens for boss damage and death events to implement the raid loot system.
 */
public class RaidEventHandler {

    /** Minimum max-health for an entity to count as a boss. */
    private static final float BOSS_HEALTH_THRESHOLD = 100f; // 50 hearts

    // ── Damage tracking ───────────────────────────────────────────────────

    /**
     * Called after armor / resistance reduction.
     * {@code event.getAmount()} is the actual HP that will be removed.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDamage(LivingDamageEvent event) {
        // Server-side only
        if (!(event.getEntity().level() instanceof ServerLevel)) return;

        // Attacker must be a player
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        // Target must meet the boss-health threshold
        if (event.getEntity().getMaxHealth() < BOSS_HEALTH_THRESHOLD) return;

        UUID bossId = event.getEntity().getUUID();

        // Player must be in a raid party
        Optional<RaidParty> partyOpt = RaidManager.INSTANCE.getPartyOf(attacker.getUUID());
        if (partyOpt.isEmpty()) return;
        RaidParty party = partyOpt.get();

        // Check if this party already has an active fight
        Optional<RaidBossFight> existingFight = RaidManager.INSTANCE.getActiveFightForParty(party);

        if (existingFight.isEmpty()) {
            // Try to claim this boss for the party
            Optional<RaidBossFight> newFight = RaidManager.INSTANCE.tryClaimBoss(party, event.getEntity());
            if (newFight.isEmpty()) return; // boss already owned by another party, or party is busy

            // Announce raid start to all party members
            MinecraftServer server = attacker.getServer();
            String bossName = event.getEntity().getName().getString();
            for (UUID memberId : party.getMembers()) {
                RaidManager.INSTANCE.sendMessage(memberId,
                        ChatFormatting.GOLD + "⚔ Raid fight started against " + bossName + "! Deal damage to earn your share of the loot.",
                        server);
            }
            // Record this hit
            RaidManager.INSTANCE.recordDamage(bossId, attacker.getUUID(), event.getAmount());

        } else if (existingFight.get().getBossEntityId().equals(bossId)) {
            // Same boss — record damage
            RaidManager.INSTANCE.recordDamage(bossId, attacker.getUUID(), event.getAmount());

        }
        // else: party is fighting a different boss — ignore these hits
    }

    // ── Loot redistribution ───────────────────────────────────────────────

    /**
     * Called when the boss dies and would normally drop items.
     * We cancel normal drops and redistribute proportionally.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBossDrops(LivingDropsEvent event) {
        // Server-side only
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;

        UUID bossId = event.getEntity().getUUID();
        Optional<RaidBossFight> fightOpt = RaidManager.INSTANCE.getFight(bossId);
        if (fightOpt.isEmpty()) return; // not a raid fight — normal drops

        RaidBossFight fight = fightOpt.get();

        // Solo fight (only one participant dealt damage) → normal loot
        if (fight.getParticipants().size() < 2) {
            RaidManager.INSTANCE.closeFight(bossId);
            return;
        }

        // Cancel the normal drops
        event.setCanceled(true);

        // Collect all ItemStacks from the event drops
        List<ItemStack> drops = new ArrayList<>();
        for (ItemEntity ie : event.getDrops()) {
            ItemStack stack = ie.getItem();
            if (!stack.isEmpty()) drops.add(stack.copy());
        }

        MinecraftServer server = serverLevel.getServer();
        double bossX = event.getEntity().getX();
        double bossY = event.getEntity().getY();
        double bossZ = event.getEntity().getZ();

        // Per-player item counts for the summary
        Map<UUID, Integer> itemsReceived = new HashMap<>();
        for (UUID uid : fight.getParticipants()) itemsReceived.put(uid, 0);

        // Split each stack proportionally
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

                // Try to give directly to player; else drop at boss position
                ServerPlayer recipient = server.getPlayerList().getPlayer(uid);
                if (recipient != null) {
                    // Add to inventory; if full, drop near player
                    if (!recipient.getInventory().add(slice)) {
                        ItemEntity dropped = new ItemEntity(serverLevel,
                                recipient.getX(), recipient.getY(), recipient.getZ(), slice);
                        serverLevel.addFreshEntity(dropped);
                    }
                } else {
                    // Offline — drop at boss location
                    ItemEntity dropped = new ItemEntity(serverLevel, bossX, bossY, bossZ, slice);
                    serverLevel.addFreshEntity(dropped);
                }

                itemsReceived.merge(uid, give, Integer::sum);
            }
        }

        // Broadcast summary to all participants
        for (UUID uid : fight.getParticipants()) {
            float dmg   = fight.getDamageDealt(uid);
            float share = fight.getShare(uid) * 100f;
            int   items = itemsReceived.getOrDefault(uid, 0);
            String line = ChatFormatting.GOLD + "⚔ Raid complete! " + fight.getBossName()
                    + " | Your damage: " + String.format("%.1f", dmg)
                    + " (" + String.format("%.1f", share) + "%)"
                    + " | Items received: " + items;
            RaidManager.INSTANCE.sendMessage(uid, line, server);
        }

        // Clean up the fight record
        RaidManager.INSTANCE.closeFight(bossId);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /**
     * Splits {@code total} items among all fight participants proportionally.
     * Uses floor-then-remainder algorithm: remainder goes to the highest damage dealer.
     */
    private Map<UUID, Integer> splitProportionally(RaidBossFight fight, int total) {
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
