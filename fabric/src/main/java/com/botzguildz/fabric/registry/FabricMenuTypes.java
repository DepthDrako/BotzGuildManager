package com.botzguildz.fabric.registry;

import com.botzguildz.BotzGuildz;
import com.botzguildz.gui.UpgradesMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

public final class FabricMenuTypes {

    public static final MenuType<UpgradesMenu> UPGRADES_MENU;

    static {
        UPGRADES_MENU = Registry.register(
                BuiltInRegistries.MENU,
                new ResourceLocation(BotzGuildz.MODID, "upgrades_menu"),
                new ExtendedScreenHandlerType<>(UpgradesMenu::fromNetwork));
    }

    private FabricMenuTypes() {}

    /** Call once during mod init to trigger static registration. */
    public static void register() {}
}
