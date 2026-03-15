package com.botzguildz.fabric;

import com.botzguildz.client.UpgradesScreen;
import com.botzguildz.fabric.registry.FabricMenuTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.MenuScreens;

@Environment(EnvType.CLIENT)
public class BotzGuildzFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(FabricMenuTypes.UPGRADES_MENU, UpgradesScreen::new);
    }
}
