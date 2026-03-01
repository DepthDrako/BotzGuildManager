package com.botzguildz.client;

import com.botzguildz.BotzGuildz;
import com.botzguildz.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only event subscriber that wires MenuTypes to their Screen classes.
 * Only loaded on the Dist.CLIENT — never runs on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = BotzGuildz.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MenuScreens.register(ModMenuTypes.UPGRADES_MENU.get(), UpgradesScreen::new)
        );
    }
}
