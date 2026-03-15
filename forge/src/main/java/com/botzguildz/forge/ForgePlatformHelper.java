package com.botzguildz.forge;

import com.botzguildz.forge.registry.ForgeModMenuTypes;
import com.botzguildz.gui.UpgradesMenu;
import com.botzguildz.platform.PlatformHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkHooks;

public final class ForgePlatformHelper implements PlatformHelper {

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public void openUpgradesScreen(ServerPlayer player, String categoryId, int page) {
        NetworkHooks.openScreen(player,
                new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.literal("Guild Upgrades");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
                        return new UpgradesMenu(windowId, inv, categoryId, page);
                    }
                },
                buf -> {
                    buf.writeUtf(categoryId);
                    buf.writeInt(page);
                });
    }

    @Override
    public MenuType<UpgradesMenu> getUpgradesMenuType() {
        return ForgeModMenuTypes.UPGRADES_MENU.get();
    }
}
