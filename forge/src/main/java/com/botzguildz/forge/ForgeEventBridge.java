package com.botzguildz.forge;

import com.botzguildz.events.CommonEventLogic;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/** Translates Forge events into calls to CommonEventLogic. */
public final class ForgeEventBridge {

    private int serverTickCounter = 0;
    private int playerTickCounter = 0;

    // Server Tick

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        CommonEventLogic.onServerTick(server, ++serverTickCounter);
    }

    // Player Tick (duel radius)

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        playerTickCounter++;
        if (playerTickCounter % 20 != 0) return;
        CommonEventLogic.onDuelPlayerTick(player);
    }

    // Player Login / Logout

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CommonEventLogic.onPlayerLogin(player);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CommonEventLogic.onPlayerLogout(player);
    }

    // Datapack Sync

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        CommonEventLogic.onDatapackSync(event.getPlayer(), event.getPlayerList());
    }

    // Friendly Fire

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingAttack(LivingAttackEvent event) {
        if (CommonEventLogic.onLivingAttack(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    // Kill Tracking

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        CommonEventLogic.onLivingDeath(event.getEntity(), event.getSource());
    }

    // Damage Multiplier

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurt(LivingHurtEvent event) {
        float newAmount = CommonEventLogic.onLivingHurt(
                event.getEntity(), event.getSource(), event.getAmount());
        event.setAmount(newAmount);
    }

    // Raid Damage Tracking

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDamage(LivingDamageEvent event) {
        CommonEventLogic.onRaidDamage(event.getEntity(), event.getSource(), event.getAmount());
    }

    // Boss Drop Redistribution

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBossDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;

        List<ItemStack> drops = new ArrayList<>();
        for (ItemEntity ie : event.getDrops()) {
            ItemStack stack = ie.getItem();
            if (!stack.isEmpty()) drops.add(stack.copy());
        }

        if (CommonEventLogic.onBossDrops(event.getEntity(), drops, serverLevel)) {
            event.setCanceled(true);
        }
    }
}
