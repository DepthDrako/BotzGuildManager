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
            // ── Unique custom screens ──────────────────────────────────────────
            MenuScreens.register(ModMenuTypes.BANK_TEST_MENU.get(), BankTestScreen::new);
            MenuScreens.register(ModMenuTypes.UPGRADES_MENU.get(),  UpgradesScreen::new);

            // ── All economy / guild chest GUIs → FantasyContainerScreen ───────
            // Economy
            MenuScreens.register(ModMenuTypes.AUCTION_BROWSE_MENU.get(),   FantasyContainerScreen::new);
            MenuScreens.register(ModMenuTypes.AUCTION_LIST_MENU.get(),     FantasyContainerScreen::new);
            MenuScreens.register(ModMenuTypes.BOUNTY_MENU.get(),           FantasyContainerScreen::new);
            MenuScreens.register(ModMenuTypes.SHOP_LIST_MENU.get(),        FantasyContainerScreen::new);
            MenuScreens.register(ModMenuTypes.SHOP_VIEW_MENU.get(),        FantasyContainerScreen::new);
            MenuScreens.register(ModMenuTypes.SHOP_CREATE_MENU.get(),      FantasyContainerScreen::new);
            // Bank
            MenuScreens.register(ModMenuTypes.PERSONAL_BANK_MENU.get(),    FantasyContainerScreen::new);
            MenuScreens.register(ModMenuTypes.GUILD_BANK_MENU.get(),       FantasyContainerScreen::new);
            // Guild admin
            MenuScreens.register(ModMenuTypes.GUILD_PERMISSIONS_MENU.get(), FantasyContainerScreen::new);
            // Guild bounty board
            MenuScreens.register(ModMenuTypes.BOUNTY_ITEM_PICKER_MENU.get(),  FantasyContainerScreen::new);
            MenuScreens.register(ModMenuTypes.GUILD_BOUNTY_POST_MENU.get(),   FantasyContainerScreen::new);
            MenuScreens.register(ModMenuTypes.GUILD_BOUNTY_BOARD_MENU.get(),  FantasyContainerScreen::new);
            // Guild missions & vault
            MenuScreens.register(ModMenuTypes.GUILD_MISSIONS_MENU.get(),      FantasyContainerScreen::new);
            MenuScreens.register(ModMenuTypes.GUILD_VAULT_MENU.get(),         FantasyContainerScreen::new);
        });
    }
}
