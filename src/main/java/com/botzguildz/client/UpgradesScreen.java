package com.botzguildz.client;

import com.botzguildz.gui.UpgradesMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Client-side screen for the Guild Upgrades chest GUI.
 *
 * Extends vanilla ContainerScreen rather than AbstractContainerScreen so that:
 *   • The exact two-part blit used by vanilla (generic_54.png top + bottom) is
 *     inherited — slot positions always match the texture perfectly.
 *   • Vanilla's hover detection and tooltip pipeline are reused in their proven
 *     form, guaranteeing that upgrade item names / lore appear on hover.
 *   • The player's own inventory is visible in the lower section, just like
 *     opening any real chest in-game.
 *
 * The only customisation needed is hiding the "Inventory" label that
 * ContainerScreen normally renders in the middle of the chest area.
 */
public class UpgradesScreen extends ContainerScreen {

    /**
     * Constructor signature matches ScreenConstructor<UpgradesMenu, UpgradesScreen>.
     * UpgradesMenu IS-A ChestMenu, so the ChestMenu parameter accepts it without a cast.
     */
    public UpgradesScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.literal("Guild Upgrades"));
    }

    /**
     * Renders the GUI labels.
     * We only draw the title bar text; the "Inventory" label (which ContainerScreen
     * would normally place at inventoryLabelY ≈ 128, right in the middle of our
     * upgrade rows) is intentionally suppressed.
     */
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title,
                this.titleLabelX, this.titleLabelY,
                0x404040, false);
        // inventoryLabelY label deliberately omitted
    }
}
