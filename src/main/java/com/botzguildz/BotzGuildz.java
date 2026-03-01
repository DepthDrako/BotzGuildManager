package com.botzguildz;

import com.botzguildz.command.GuildCommand;
import com.botzguildz.command.DuelCommand;
import com.botzguildz.command.RaidCommand;
import com.botzguildz.event.RaidEventHandler;
import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.event.GuildEventHandler;
import com.botzguildz.event.DuelEventHandler;
import com.botzguildz.ftb.FTBBridge;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.upgrade.UpgradeRegistry;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

@Mod(BotzGuildz.MODID)
public class BotzGuildz {

    public static final String MODID  = "botzguildz";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BotzGuildz(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        // Register the Upgrades GUI menu type on the mod event bus
        ModMenuTypes.register(modEventBus);

        // Register server config — creates botzguildz-server.toml in the server config dir
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GuildConfig.SPEC, "botzguildz-server.toml");

        // Register Forge event listeners
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new GuildEventHandler());
        MinecraftForge.EVENT_BUS.register(new DuelEventHandler());
        MinecraftForge.EVENT_BUS.register(new RaidEventHandler());

        // Register datapack reload listeners for the upgrade system
        MinecraftForge.EVENT_BUS.addListener(UpgradeRegistry::registerListeners);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CurrencyManager.init();
            FTBBridge.init();
        });
        LOGGER.info("[BotzGuildz] Common setup complete.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        GuildCommand.register(event.getDispatcher());
        DuelCommand.register(event.getDispatcher());
        RaidCommand.register(event.getDispatcher());
        LOGGER.info("[BotzGuildz] Commands registered.");
    }
}
