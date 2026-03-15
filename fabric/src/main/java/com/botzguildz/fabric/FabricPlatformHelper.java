package com.botzguildz.fabric;

import com.botzguildz.fabric.registry.FabricMenuTypes;
import com.botzguildz.gui.UpgradesMenu;
import com.botzguildz.platform.PlatformHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public final class FabricPlatformHelper implements PlatformHelper {

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public void openUpgradesScreen(ServerPlayer player, String categoryId, int page) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer p, FriendlyByteBuf buf) {
                buf.writeUtf(categoryId);
                buf.writeInt(page);
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Guild Upgrades");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) {
                return new UpgradesMenu(syncId, inv, categoryId, page);
            }
        });
    }

    @Override
    public MenuType<UpgradesMenu> getUpgradesMenuType() {
        return FabricMenuTypes.UPGRADES_MENU;
    }
}
