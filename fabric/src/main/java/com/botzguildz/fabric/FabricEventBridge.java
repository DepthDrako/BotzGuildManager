package com.botzguildz.fabric;

import com.botzguildz.events.CommonEventLogic;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Registers Fabric API event callbacks and delegates to CommonEventLogic. */
public final class FabricEventBridge {

    private static int serverTickCounter = 0;
    private static int playerTickCounter = 0;

    private FabricEventBridge() {}

    public static void register() {
        registerServerTick();
        registerPlayerLoginLogout();
        registerDatapackSync();
        registerFriendlyFire();
        registerKillTracking();
        registerRaidDamage();
        registerBossDrops();
    }

    private static void registerServerTick() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CommonEventLogic.onServerTick(server, ++serverTickCounter);
            // Check duel radius every 20 ticks for all online players
            if (serverTickCounter % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    CommonEventLogic.onDuelPlayerTick(player);
                }
            }
        });
    }

    private static void registerPlayerLoginLogout() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            CommonEventLogic.onPlayerLogin(handler.player);
            CommonEventLogic.onDatapackSync(handler.player, server.getPlayerList());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CommonEventLogic.onPlayerLogout(handler.player);
        });
    }

    private static void registerDatapackSync() {
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                CommonEventLogic.onDatapackSync(null, server.getPlayerList());
            }
        });
    }

    private static void registerFriendlyFire() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !CommonEventLogic.onLivingAttack(entity, source));
    }

    private static void registerKillTracking() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
                CommonEventLogic.onLivingDeath(entity, source));
    }

    private static void registerRaidDamage() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            CommonEventLogic.onRaidDamage(entity, source, amount);
            return true;
        });
    }

    private static void registerBossDrops() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity.level() instanceof ServerLevel serverLevel)) return;
            if (entity.getMaxHealth() < 100f) return;

            List<ItemEntity> nearby = serverLevel.getEntitiesOfClass(
                    ItemEntity.class, entity.getBoundingBox().inflate(4.0));
            List<ItemStack> drops = new ArrayList<>();
            for (ItemEntity ie : nearby) {
                if (!ie.getItem().isEmpty()) drops.add(ie.getItem().copy());
            }

            if (CommonEventLogic.onBossDrops(entity, drops, serverLevel)) {
                for (ItemEntity ie : nearby) ie.discard();
            }
        });
    }
}
