package com.botzguildz.fabric;

import com.botzguildz.BotzGuildz;
import com.botzguildz.command.DuelCommand;
import com.botzguildz.command.GuildCommand;
import com.botzguildz.command.RaidCommand;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.fabric.config.FabricGuildConfig;
import com.botzguildz.fabric.registry.FabricMenuTypes;
import com.botzguildz.ftb.FTBBridge;
import com.botzguildz.upgrade.UpgradeRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class BotzGuildzFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Config
        FabricGuildConfig.load();
        FabricGuildConfig.bind();

        // Menu types (static init)
        FabricMenuTypes.register();

        // Datapack reload listeners for the upgrade system
        ResourceManagerHelper rm = ResourceManagerHelper.get(PackType.SERVER_DATA);
        for (SimpleJsonResourceReloadListener listener : UpgradeRegistry.createListeners()) {
            String simpleName = listener.getClass().getSimpleName().toLowerCase();
            ResourceLocation id = new ResourceLocation(BotzGuildz.MODID, simpleName);
            rm.registerReloadListener(new IdentifiableResourceReloadListener() {
                @Override
                public ResourceLocation getFabricId() { return id; }

                @Override
                public CompletableFuture<Void> reload(
                        PreparationBarrier barrier,
                        net.minecraft.server.packs.resources.ResourceManager manager,
                        net.minecraft.util.profiling.ProfilerFiller workerProfiler,
                        net.minecraft.util.profiling.ProfilerFiller mainProfiler,
                        Executor workerExecutor, Executor mainExecutor) {
                    return listener.reload(barrier, manager, workerProfiler,
                            mainProfiler, workerExecutor, mainExecutor);
                }
            });
        }

        // Game events
        FabricEventBridge.register();

        // Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GuildCommand.register(dispatcher);
            DuelCommand.register(dispatcher);
            RaidCommand.register(dispatcher);
            BotzGuildz.LOGGER.info("[BotzGuildz] Commands registered.");
        });

        // Post-init
        CurrencyManager.init();
        FTBBridge.init();

        BotzGuildz.LOGGER.info("[BotzGuildz] Fabric module initialised.");
    }
}
