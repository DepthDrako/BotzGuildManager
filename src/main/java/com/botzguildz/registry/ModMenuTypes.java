package com.botzguildz.registry;

import com.botzguildz.BotzGuildz;
import com.botzguildz.gui.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, BotzGuildz.MODID);

    public static final RegistryObject<MenuType<UpgradesMenu>> UPGRADES_MENU =
            MENUS.register("upgrades_menu",
                    () -> IForgeMenuType.create(UpgradesMenu::fromNetwork));

    // ── Economy GUIs ──────────────────────────────────────────────────────────

    public static final RegistryObject<MenuType<AuctionBrowseMenu>> AUCTION_BROWSE_MENU =
            MENUS.register("auction_browse",
                    () -> IForgeMenuType.create(AuctionBrowseMenu::fromNetwork));

    public static final RegistryObject<MenuType<AuctionListMenu>> AUCTION_LIST_MENU =
            MENUS.register("auction_list",
                    () -> IForgeMenuType.create(AuctionListMenu::fromNetwork));

    public static final RegistryObject<MenuType<BountyMenu>> BOUNTY_MENU =
            MENUS.register("bounty",
                    () -> IForgeMenuType.create(BountyMenu::fromNetwork));

    public static final RegistryObject<MenuType<ShopListMenu>> SHOP_LIST_MENU =
            MENUS.register("shop_list",
                    () -> IForgeMenuType.create(ShopListMenu::fromNetwork));

    public static final RegistryObject<MenuType<ShopViewMenu>> SHOP_VIEW_MENU =
            MENUS.register("shop_view",
                    () -> IForgeMenuType.create(ShopViewMenu::fromNetwork));

    public static final RegistryObject<MenuType<ShopCreateMenu>> SHOP_CREATE_MENU =
            MENUS.register("shop_create",
                    () -> IForgeMenuType.create(ShopCreateMenu::fromNetwork));

    // ── Bank GUIs ─────────────────────────────────────────────────────────────

    public static final RegistryObject<MenuType<PersonalBankMenu>> PERSONAL_BANK_MENU =
            MENUS.register("personal_bank",
                    () -> IForgeMenuType.create(PersonalBankMenu::fromNetwork));

    public static final RegistryObject<MenuType<GuildBankMenu>> GUILD_BANK_MENU =
            MENUS.register("guild_bank",
                    () -> IForgeMenuType.create(GuildBankMenu::fromNetwork));

    // ── Guild Admin GUIs ──────────────────────────────────────────────────────

    public static final RegistryObject<MenuType<GuildPermissionsMenu>> GUILD_PERMISSIONS_MENU =
            MENUS.register("guild_permissions",
                    () -> IForgeMenuType.create(GuildPermissionsMenu::fromNetwork));

    // ── Guild Bounty Board GUIs ───────────────────────────────────────────────

    public static final RegistryObject<MenuType<BountyItemPickerMenu>> BOUNTY_ITEM_PICKER_MENU =
            MENUS.register("bounty_item_picker",
                    () -> IForgeMenuType.create(BountyItemPickerMenu::fromNetwork));

    public static final RegistryObject<MenuType<GuildBountyPostMenu>> GUILD_BOUNTY_POST_MENU =
            MENUS.register("guild_bounty_post",
                    () -> IForgeMenuType.create(GuildBountyPostMenu::fromNetwork));

    public static final RegistryObject<MenuType<GuildBountyBoardMenu>> GUILD_BOUNTY_BOARD_MENU =
            MENUS.register("guild_bounty_board",
                    () -> IForgeMenuType.create(GuildBountyBoardMenu::fromNetwork));

    // ── Guild Missions & Vault GUIs ───────────────────────────────────────────

    public static final RegistryObject<MenuType<GuildMissionsMenu>> GUILD_MISSIONS_MENU =
            MENUS.register("guild_missions",
                    () -> IForgeMenuType.create(GuildMissionsMenu::fromNetwork));

    public static final RegistryObject<MenuType<GuildVaultMenu>> GUILD_VAULT_MENU =
            MENUS.register("guild_vault",
                    () -> IForgeMenuType.create(GuildVaultMenu::fromNetwork));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
