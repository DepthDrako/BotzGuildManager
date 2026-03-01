package com.botzguildz.registry;

import com.botzguildz.BotzGuildz;
import com.botzguildz.gui.UpgradesMenu;
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

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
