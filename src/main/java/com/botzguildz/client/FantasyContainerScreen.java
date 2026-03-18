package com.botzguildz.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Generic fantasy-themed screen for all economy chest GUIs.
 *
 * Replaces the vanilla chest texture entirely with programmatic rendering:
 *   • Midnight-blue background + antique-gold 2-px frame + corner accents
 *   • Gradient header showing the menu title in bright gold
 *   • Separator between the 54-slot content area and the player inventory
 *   • Individual slot backgrounds drawn for both content and inventory sections
 *
 * Works with any 6-row ChestMenu (54 content slots + 36 player inventory slots).
 * All slot item rendering is handled by AbstractContainerScreen as normal.
 */
public class FantasyContainerScreen extends ContainerScreen {

    // ── Colour palette (mirrors BankTestScreen) ───────────────────────────────
    private static final int C_BG       = 0xFF0B0D1A;
    private static final int C_PANEL    = 0xFF141828;
    private static final int C_INV_BG   = 0xFF0A0E1A;
    private static final int C_HDR_TOP  = 0xFF1A1440;
    private static final int C_HDR_BOT  = 0xFF0D0F22;
    private static final int C_GOLD     = 0xFFC8A84B;
    private static final int C_GOLD_DIM = 0xFF7A6520;
    private static final int C_SEP      = 0xFF2A2848;
    private static final int C_TXT_GOLD = 0xFFFFD700;
    private static final int C_TXT_DIM  = 0xFF8B7A50;
    private static final int C_SLOT_BG  = 0xFF111525;
    private static final int C_SLOT_BDR = 0xFF283050;
    private static final int C_INV_BDR  = 0xFF252540;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FantasyContainerScreen(ChestMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.titleLabelX     = Integer.MAX_VALUE; // suppress default title render
        this.inventoryLabelY = Integer.MAX_VALUE; // suppress default inv label
    }

    // ── Background ────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics gg, float partial, int mx, int my) {
        int lx = leftPos, ty = topPos;
        int W  = imageWidth;   // 176 for standard ChestMenu
        int H  = imageHeight;  // 222 for 6-row, 166 for 3-row, etc.

        // ── Main fill ─────────────────────────────────────────────────────────
        gg.fill(lx, ty, lx + W, ty + H, C_BG);

        // ── Outer gold frame (2 px) ───────────────────────────────────────────
        fillBorder(gg, lx, ty, W, H, C_GOLD, 2);

        // ── Corner accent squares ─────────────────────────────────────────────
        gg.fill(lx + 2,     ty + 2,     lx + 5,     ty + 5,     C_TXT_GOLD);
        gg.fill(lx + W - 5, ty + 2,     lx + W - 2, ty + 5,     C_TXT_GOLD);
        gg.fill(lx + 2,     ty + H - 5, lx + 5,     ty + H - 2, C_TXT_GOLD);
        gg.fill(lx + W - 5, ty + H - 5, lx + W - 2, ty + H - 2, C_TXT_GOLD);

        // ── Header gradient (rows 2–22) ───────────────────────────────────────
        gg.fillGradient(lx + 2, ty + 2, lx + W - 2, ty + 22, C_HDR_TOP, C_HDR_BOT);
        gg.fill(lx + 2, ty + 22, lx + W - 2, ty + 23, C_GOLD_DIM);

        // ── Determine inventory separator position dynamically ─────────────────
        // The player-inventory section always occupies the last 36 slots.
        int totalSlots  = this.menu.slots.size();
        int invStart    = totalSlots - 36;           // index of first player-inv slot
        int invSlotY    = this.menu.slots.get(invStart).y; // GUI-relative y of that slot
        int sepAbsY     = ty + invSlotY - 12;        // 12 px gap before the section

        // ── Content panel fill (below header, above separator) ────────────────
        gg.fill(lx + 2, ty + 23, lx + W - 2, sepAbsY, C_PANEL);

        // ── Separator + inventory panel ───────────────────────────────────────
        gg.fill(lx + 2, sepAbsY,     lx + W - 2, sepAbsY + 1, C_SEP);
        gg.fill(lx + 2, sepAbsY + 1, lx + W - 2, ty + H - 2,  C_INV_BG);

        // ── Slot backgrounds ───────────────────────────────────────────────────
        for (int i = 0; i < totalSlots; i++) {
            Slot slot     = this.menu.slots.get(i);
            int  sx       = lx + slot.x - 1;
            int  sy       = ty + slot.y - 1;
            boolean isInv = i >= invStart;
            int  bg       = isInv ? C_INV_BG  : C_SLOT_BG;
            int  bdr      = isInv ? C_INV_BDR : C_SLOT_BDR;
            gg.fill(sx, sy, sx + 18, sy + 18, bg);
            fillBorder(gg, sx, sy, 18, 18, bdr, 1);
        }
    }

    // ── Labels ────────────────────────────────────────────────────────────────
    // Coordinates are GUI-relative (already translated to leftPos, topPos).

    @Override
    protected void renderLabels(GuiGraphics gg, int mx, int my) {
        // Title in header — centred, bright gold
        String display = "\u2726 " + this.title.getString() + " \u2726";
        int    w       = this.font.width(display);
        gg.drawString(this.font, display, (imageWidth - w) / 2, 7, C_TXT_GOLD, false);

        // "Inventory" label above the player-inventory section
        int totalSlots = this.menu.slots.size();
        int invSlotY   = this.menu.slots.get(totalSlots - 36).y;
        gg.drawString(this.font, "Inventory", 8, invSlotY - 10, C_TXT_DIM, false);
    }

    // ── Border helper ─────────────────────────────────────────────────────────

    private static void fillBorder(GuiGraphics gg, int x, int y,
                                   int w, int h, int color, int t) {
        gg.fill(x,         y,         x + w,     y + t,     color);
        gg.fill(x,         y + h - t, x + w,     y + h,     color);
        gg.fill(x,         y,         x + t,     y + h,     color);
        gg.fill(x + w - t, y,         x + w,     y + h,     color);
    }
}
