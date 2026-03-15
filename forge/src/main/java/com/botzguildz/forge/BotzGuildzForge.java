package com.botzguildz.forge;

import com.botzguildz.BotzGuildz;
import com.botzguildz.command.DuelCommand;
import com.botzguildz.command.GuildCommand;
import com.botzguildz.command.RaidCommand;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.forge.config.ForgeGuildConfig;
import com.botzguildz.forge.registry.ForgeModMenuTypes;
import com.botzguildz.ftb.FTBBridge;
import com.botzguildz.upgrade.UpgradeRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

@Mod(BotzGuildz.MODID)
public final class BotzGuildzForge {

    public BotzGuildzForge(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        // Register Forge menu types on the mod bus
        ForgeModMenuTypes.register(modBus);

        // Register server config
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER, ForgeGuildConfig.SPEC, "botzguildz-server.toml");

        // Wire Forge config values into the common GuildConfig facade
        ForgeGuildConfig.bind();

        // Register MinecraftForge event bus listeners
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ForgeEventBridge());

        // Lifecycle
        modBus.addListener(this::commonSetup);

        BotzGuildz.LOGGER.info("[BotzGuildz] Forge module initialised.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CurrencyManager.init();
            FTBBridge.init();
        });
        BotzGuildz.LOGGER.info("[BotzGuildz] Forge common setup complete.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        GuildCommand.register(event.getDispatcher());
        DuelCommand.register(event.getDispatcher());
        RaidCommand.register(event.getDispatcher());
        BotzGuildz.LOGGER.info("[BotzGuildz] Commands registered.");
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        for (SimpleJsonResourceReloadListener listener : UpgradeRegistry.createListeners()) {
            event.addListener(listener);
        }
    }
}
