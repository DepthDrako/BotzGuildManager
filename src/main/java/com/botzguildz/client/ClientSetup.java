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
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.UPGRADES_MENU.get(),     UpgradesScreen::new);
            // Economy GUIs — all reuse the generic UpgradesScreen (suppresses inventory label)
            MenuScreens.register(ModMenuTypes.AUCTION_BROWSE_MENU.get(), UpgradesScreen::new);
            MenuScreens.register(ModMenuTypes.AUCTION_LIST_MENU.get(),   UpgradesScreen::new);
            MenuScreens.register(ModMenuTypes.BOUNTY_MENU.get(),         UpgradesScreen::new);
            MenuScreens.register(ModMenuTypes.SHOP_LIST_MENU.get(),      UpgradesScreen::new);
            MenuScreens.register(ModMenuTypes.SHOP_VIEW_MENU.get(),      UpgradesScreen::new);
            MenuScreens.register(ModMenuTypes.SHOP_CREATE_MENU.get(),   UpgradesScreen::new);
            // Bank GUIs
            MenuScreens.register(ModMenuTypes.PERSONAL_BANK_MENU.get(),  UpgradesScreen::new);
            MenuScreens.register(ModMenuTypes.GUILD_BANK_MENU.get(),     UpgradesScreen::new);
            // Guild Admin GUIs
            MenuScreens.register(ModMenuTypes.GUILD_PERMISSIONS_MENU.get(), UpgradesScreen::new);
            // Guild Bounty Board GUIs
            MenuScreens.register(ModMenuTypes.BOUNTY_ITEM_PICKER_MENU.get(),  UpgradesScreen::new);
            MenuScreens.register(ModMenuTypes.GUILD_BOUNTY_POST_MENU.get(),   UpgradesScreen::new);
            MenuScreens.register(ModMenuTypes.GUILD_BOUNTY_BOARD_MENU.get(),  UpgradesScreen::new);
            // Guild Missions & Vault GUIs
            MenuScreens.register(ModMenuTypes.GUILD_MISSIONS_MENU.get(),      UpgradesScreen::new);
            MenuScreens.register(ModMenuTypes.GUILD_VAULT_MENU.get(),         UpgradesScreen::new);
        });
    }
}
