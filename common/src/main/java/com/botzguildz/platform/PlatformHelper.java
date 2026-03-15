package com.botzguildz.platform;

import com.botzguildz.gui.UpgradesMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;

import java.util.ServiceLoader;

/**
 * Cross-platform abstraction layer.
 * Each platform module provides exactly one implementation via Java ServiceLoader.
 */
public interface PlatformHelper {

    /** Returns true if the given mod ID is present on the current platform. */
    boolean isModLoaded(String modId);

    /**
     * Opens the Upgrades GUI for the given player using the platform-specific
     * screen-opening mechanism (NetworkHooks on Forge, openHandledScreen on Fabric).
     */
    void openUpgradesScreen(ServerPlayer player, String categoryId, int page);

    /** Returns the registered MenuType for {@link UpgradesMenu} on this platform. */
    MenuType<UpgradesMenu> getUpgradesMenuType();

    /** Singleton resolved via ServiceLoader at class-load time. */
    PlatformHelper INSTANCE = ServiceLoader.load(PlatformHelper.class)
            .findFirst()
            .orElseThrow(() -> new RuntimeException(
                    "[BotzGuildz] No PlatformHelper implementation found! "
                    + "Did you include the platform-specific module?"));
}
