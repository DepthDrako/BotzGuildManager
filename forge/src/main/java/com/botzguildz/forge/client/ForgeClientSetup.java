package com.botzguildz.forge.client;

import com.botzguildz.BotzGuildz;
import com.botzguildz.client.UpgradesScreen;
import com.botzguildz.forge.registry.ForgeModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BotzGuildz.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MenuScreens.register(ForgeModMenuTypes.UPGRADES_MENU.get(), UpgradesScreen::new)
        );
    }
}
