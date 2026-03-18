package com.botzguildz.client;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.gui.BankTestMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Fully custom, texture-free screen for Bank Test.
 *
 * Colour palette — fantasy "Guild Treasury":
 *   Midnight blue bg · antique gold frame · parchment text
 *   Green deposit strip · amber withdraw strip · crimson close button
 *
 * Layout (y relative to GUI top-left, GUI_H = 210):
 *   2-32   Header gradient
 *   36-62  Balance panel
 *   64-102 Deposit strip  (label + DEP_Y=78 coins + abbrevs)
 *   104-137 Withdraw strip (label + WD_Y=114 coins + abbrevs)
 *   139-179 Footer hints panel (3 short text lines)
 *   188-208 Close button
 */
public class BankTestScreen extends AbstractContainerScreen<BankTestMenu> {

    // ── Colour palette (ARGB) ─────────────────────────────────────────────────
    private static final int C_BG          = 0xFF0B0D1A;
    private static final int C_PANEL       = 0xFF141828;
    private static final int C_PANEL_LT    = 0xFF1C2438;
    private static final int C_HDR_TOP     = 0xFF1A1440;
    private static final int C_HDR_BOT     = 0xFF0D0F22;
    private static final int C_GOLD        = 0xFFC8A84B;
    private static final int C_GOLD_DIM    = 0xFF7A6520;
    private static final int C_SEP         = 0xFF2A2848;
    private static final int C_TXT_GOLD    = 0xFFFFD700;
    private static final int C_TXT_PARCH   = 0xFFD4B870;
    private static final int C_TXT_DIM     = 0xFF8B7A50;
    private static final int C_TXT_GREEN   = 0xFF4CAF50;
    private static final int C_TXT_AMBER   = 0xFFFFB300;
    private static final int C_SLOT_DEP    = 0xFF0C1C0C;
    private static final int C_SLOT_WD     = 0xFF1C0C06;
    private static final int C_SLOT_EMPTY  = 0xFF1E0C0C;
    private static final int C_SLOT_BDR_D  = 0xFF2A5A2A;
    private static final int C_SLOT_BDR_W  = 0xFF5A3A10;
    private static final int C_SLOT_BDR_E  = 0xFF3A1A1A;
    private static final int C_SLOT_HOVER  = 0x30FFFFFF;
    private static final int C_BTN_CLOSE   = 0xFF1E0808;
    private static final int C_BTN_HOVER   = 0xFF3A1010;
    private static final int C_BTN_BDR     = 0xFF7A1515;

    // ── Tier abbreviations (slot labels + wallet display) ─────────────────────
    // t=0 GB  t=1 GCh  t=2 GT  t=3 GC  t=4 GM  t=5 GS
    private static final String[] TIER_ABBR = { "GB", "GCh", "GT", "GC", "GM", "GS" };

    /** Denomination values mirror ModItems.TIER_VALUES — kept here to avoid
     *  server-side registry access on the client render thread. */
    private static final long[] TIER_VALUES_CLIENT = { 1L, 8L, 64L, 512L, 4_096L, 32_768L };

    // ── Layout constants (derived from BankTestMenu, repeated here for clarity) ─
    private static final int DEP_Y  = BankTestMenu.DEP_Y;   // 78
    private static final int WD_Y   = BankTestMenu.WD_Y;    // 114

    // ── Close button geometry (GUI-relative) ──────────────────────────────────
    private static final int BTN_X  = BankTestMenu.CLOSE_X - 22;  // 70
    private static final int BTN_W  = 60;                          // 70..130
    private static final int BTN_Y  = BankTestMenu.CLOSE_Y - 4;   // 186
    private static final int BTN_H  = 18;

    // ── Constructor ───────────────────────────────────────────────────────────

    public BankTestScreen(BankTestMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth      = BankTestMenu.GUI_W;
        this.imageHeight     = BankTestMenu.GUI_H;
        this.titleLabelX     = Integer.MAX_VALUE; // suppress default title
        this.inventoryLabelY = Integer.MAX_VALUE; // suppress inventory label
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gg, int mx, int my, float partial) {
        super.render(gg, mx, my, partial); // → renderBg() + renderLabels()
        renderCoinItems(gg);
        renderCustomTooltip(gg, mx, my);
    }

    // ── Background ────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics gg, float partial, int mx, int my) {
        int lx = leftPos, ty = topPos;
        int W  = BankTestMenu.GUI_W;
        int H  = BankTestMenu.GUI_H;

        // Main fill
        gg.fill(lx, ty, lx + W, ty + H, C_BG);

        // Outer gold frame 2 px
        fillBorder(gg, lx, ty, W, H, C_GOLD, 2);

        // Corner accents
        gg.fill(lx + 2,     ty + 2,     lx + 5,     ty + 5,     C_TXT_GOLD);
        gg.fill(lx + W - 5, ty + 2,     lx + W - 2, ty + 5,     C_TXT_GOLD);
        gg.fill(lx + 2,     ty + H - 5, lx + 5,     ty + H - 2, C_TXT_GOLD);
        gg.fill(lx + W - 5, ty + H - 5, lx + W - 2, ty + H - 2, C_TXT_GOLD);

        // Header gradient (y 2-32)
        gg.fillGradient(lx + 2, ty + 2, lx + W - 2, ty + 30, C_HDR_TOP, C_HDR_BOT);
        gg.fill(lx + 2, ty + 30, lx + W - 2, ty + 32, C_GOLD_DIM);

        // Balance panel (y 36-62)
        int bx = lx + 8, by = ty + 36;
        gg.fill(bx, by, bx + (W - 16), by + 26, C_PANEL_LT);
        fillBorder(gg, bx, by, W - 16, 26, C_GOLD_DIM, 1);
        gg.fill(bx + 1, by + 1, bx + (W - 18), by + 2, 0x18FFD700);

        // Sep before deposit strip
        gg.fill(lx + 2, ty + 63, lx + W - 2, ty + 64, C_SEP);

        // Deposit strip bg (y 64-102): label + coins + abbrevs
        gg.fill(lx + 4, ty + 64, lx + W - 4, ty + 102, 0xFF0A1008);
        fillBorder(gg, lx + 4, ty + 64, W - 8, 38, 0xFF1A3A1A, 1);
        // Thin separator between label and coins
        gg.fill(lx + 5, ty + 73, lx + W - 5, ty + 74, 0xFF0F2A0F);

        // Sep between deposit and withdraw
        gg.fill(lx + 2, ty + 103, lx + W - 2, ty + 104, C_SEP);

        // Withdraw strip bg (y 104-140)
        gg.fill(lx + 4, ty + 104, lx + W - 4, ty + 140, 0xFF150A04);
        fillBorder(gg, lx + 4, ty + 104, W - 8, 36, 0xFF3A2008, 1);
        gg.fill(lx + 5, ty + 111, lx + W - 5, ty + 112, 0xFF2A1A04);

        // Sep before footer
        gg.fill(lx + 2, ty + 141, lx + W - 2, ty + 142, C_SEP);

        // Footer panel (y 143-179)
        int fx = lx + 8, fy = ty + 143;
        gg.fill(fx, fy, fx + (W - 16), fy + 36, C_PANEL);
        fillBorder(gg, fx, fy, W - 16, 36, C_GOLD_DIM, 1);

        // ── Coin slot backgrounds + hover highlights ───────────────────────────
        long[] denoms = CurrencyManager.getDenominations();
        long   wallet = menu.getWalletBalance();
        for (int t = 0; t < 6 && t < denoms.length; t++) {
            int sx = lx + BankTestMenu.COIN_X0 + t * 18;

            // Deposit slot
            long depCount = menu.getSrcCount(t);
            int slotBgD   = depCount > 0 ? C_SLOT_DEP : C_SLOT_EMPTY;
            int slotBdD   = depCount > 0 ? C_SLOT_BDR_D : C_SLOT_BDR_E;
            gg.fill(sx, ty + DEP_Y, sx + 16, ty + DEP_Y + 16, slotBgD);
            fillBorder(gg, sx, ty + DEP_Y, 16, 16, slotBdD, 1);
            if (mx >= sx && mx < sx + 16 && my >= ty + DEP_Y && my < ty + DEP_Y + 16)
                gg.fill(sx, ty + DEP_Y, sx + 16, ty + DEP_Y + 16, C_SLOT_HOVER);

            // Withdraw slot
            long canAfford = denoms[t] > 0 ? wallet / denoms[t] : 0;
            int slotBgW   = canAfford > 0 ? C_SLOT_WD : C_SLOT_EMPTY;
            int slotBdW   = canAfford > 0 ? C_SLOT_BDR_W : C_SLOT_BDR_E;
            gg.fill(sx, ty + WD_Y, sx + 16, ty + WD_Y + 16, slotBgW);
            fillBorder(gg, sx, ty + WD_Y, 16, 16, slotBdW, 1);
            if (mx >= sx && mx < sx + 16 && my >= ty + WD_Y && my < ty + WD_Y + 16)
                gg.fill(sx, ty + WD_Y, sx + 16, ty + WD_Y + 16, C_SLOT_HOVER);
        }

        // ── Close button ───────────────────────────────────────────────────────
        int cbx = lx + BTN_X, cby = ty + BTN_Y;
        boolean hoverClose = mx >= cbx && mx < cbx + BTN_W
                          && my >= cby && my < cby + BTN_H;
        gg.fill(cbx, cby, cbx + BTN_W, cby + BTN_H,
                hoverClose ? C_BTN_HOVER : C_BTN_CLOSE);
        fillBorder(gg, cbx, cby, BTN_W, BTN_H, C_BTN_BDR, 1);
        gg.fillGradient(cbx + 1, cby + 1, cbx + BTN_W - 1, cby + BTN_H / 2,
                0x18FF4444, 0x00FF4444);
    }

    // ── Coin items (rendered after super.render so they appear on top) ─────────

    private void renderCoinItems(GuiGraphics gg) {
        int    lx     = leftPos;
        int    ty     = topPos;
        long[] denoms = CurrencyManager.getDenominations();
        long   wallet = menu.getWalletBalance();

        // Balance panel icons — stay within y 36-62
        gg.renderItem(new ItemStack(Items.ENDER_CHEST),  lx + 12,       ty + 40);
        gg.renderItem(CurrencyManager.getDisplayItem(),   lx + BankTestMenu.GUI_W - 28, ty + 40);

        // Coin tiles
        for (int t = 0; t < 6 && t < denoms.length; t++) {
            int sx = lx + BankTestMenu.COIN_X0 + t * 18;

            // Deposit
            long depCount = menu.getSrcCount(t);
            ItemStack depItem;
            if (depCount > 0) {
                depItem = CurrencyManager.getTierItem(t).copy();
                depItem.setCount((int) Math.min(depCount, 64));
            } else {
                depItem = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            }
            gg.renderItem(depItem, sx, ty + DEP_Y);
            gg.renderItemDecorations(this.font, depItem, sx, ty + DEP_Y);

            // Withdraw
            long canAfford = denoms[t] > 0 ? wallet / denoms[t] : 0;
            ItemStack wdItem;
            if (canAfford > 0) {
                wdItem = CurrencyManager.getTierItem(t).copy();
                wdItem.setCount((int) Math.min(canAfford, 64));
            } else {
                wdItem = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            }
            gg.renderItem(wdItem, sx, ty + WD_Y);
            gg.renderItemDecorations(this.font, wdItem, sx, ty + WD_Y);
        }
    }

    // ── Labels ────────────────────────────────────────────────────────────────
    // Coordinates here are GUI-relative (translated by super).

    @Override
    protected void renderLabels(GuiGraphics gg, int mx, int my) {
        int W = BankTestMenu.GUI_W;

        // Header
        String hdr = "\u2726 GUILD TREASURY \u2726";
        gg.drawString(this.font, hdr, (W - this.font.width(hdr)) / 2, 11, C_TXT_GOLD, false);

        // Balance panel (y 36-62) — icons at 40, text at 38 / 48
        long   wallet = menu.getWalletBalance();
        String balStr = formatShort(wallet);       // abbreviated: "69GS 1GM 1GC …"
        gg.drawString(this.font, "Wallet",   30, 38, C_TXT_DIM,  false);
        gg.drawString(this.font, balStr,      30, 48, C_TXT_GOLD, false);

        // ── Deposit strip (y 64-102) ───────────────────────────────────────────
        String dep = "\u2b06  DEPOSIT";
        gg.drawString(this.font, dep, (W - this.font.width(dep)) / 2, 66, C_TXT_GREEN, false);

        // Abbrevs below deposit coins (DEP_Y + 17 = 95)
        for (int t = 0; t < 6; t++) {
            String abbr    = TIER_ABBR[t];
            int    centerX = BankTestMenu.COIN_X0 + t * 18 + 8;
            gg.drawString(this.font, abbr, centerX - this.font.width(abbr) / 2,
                    DEP_Y + 17, C_TXT_DIM, false);
        }

        // ── Withdraw strip (y 104-140) ─────────────────────────────────────────
        String wd = "\u2b07  WITHDRAW";
        gg.drawString(this.font, wd, (W - this.font.width(wd)) / 2, 106, C_TXT_AMBER, false);

        // Abbrevs below withdraw coins (WD_Y + 17 = 131)
        for (int t = 0; t < 6; t++) {
            String abbr    = TIER_ABBR[t];
            int    centerX = BankTestMenu.COIN_X0 + t * 18 + 8;
            gg.drawString(this.font, abbr, centerX - this.font.width(abbr) / 2,
                    WD_Y + 17, C_TXT_DIM, false);
        }

        // ── Footer hints (y 143-179) ───────────────────────────────────────────
        gg.drawString(this.font, "Left-click: 1 coin  \u00b7  Shift: full stack",
                13, 148, C_TXT_DIM, false);
        gg.drawString(this.font, "Grey slot = nothing available",
                13, 159, C_TXT_DIM, false);
        gg.drawString(this.font, "Values reflect your wallet balance",
                13, 170, C_TXT_DIM, false);

        // ── Close button label (centered inside button) ────────────────────────
        int  guiMx    = mx - this.leftPos;
        int  guiMy    = my - this.topPos;
        boolean hover = guiMx >= BTN_X && guiMx < BTN_X + BTN_W
                     && guiMy >= BTN_Y && guiMy < BTN_Y + BTN_H;
        String closeLbl = "\u2715  CLOSE";
        int    closeTxt  = hover ? 0xFF5555 : 0xCC2222;
        gg.drawString(this.font, closeLbl,
                BTN_X + (BTN_W - this.font.width(closeLbl)) / 2,
                BTN_Y + (BTN_H - 8) / 2,
                closeTxt, false);
    }

    // ── Tooltips ──────────────────────────────────────────────────────────────

    private void renderCustomTooltip(GuiGraphics gg, int mx, int my) {
        long[] denoms = CurrencyManager.getDenominations();
        long   wallet = menu.getWalletBalance();

        for (int t = 0; t < 6 && t < denoms.length; t++) {
            int sx = leftPos + BankTestMenu.COIN_X0 + t * 18;
            int sy = topPos  + DEP_Y;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                long count = menu.getSrcCount(t);
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal("Deposit  " + BankTestMenu.tierName(t))
                        .withStyle(ChatFormatting.GREEN));
                tip.add(Component.literal("In inventory: " + count)
                        .withStyle(ChatFormatting.YELLOW));
                if (count > 0)
                    tip.add(Component.literal("Worth: " + CurrencyManager.format(count * denoms[t]))
                            .withStyle(ChatFormatting.GOLD));
                tip.add(Component.empty());
                tip.add(Component.literal("Click: deposit 64   Shift: all")
                        .withStyle(ChatFormatting.GRAY));
                gg.renderComponentTooltip(this.font, tip, mx, my);
                return;
            }
        }

        for (int t = 0; t < 6 && t < denoms.length; t++) {
            int sx = leftPos + BankTestMenu.COIN_X0 + t * 18;
            int sy = topPos  + WD_Y;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                long canAfford = denoms[t] > 0 ? wallet / denoms[t] : 0;
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal("Withdraw  " + BankTestMenu.tierName(t))
                        .withStyle(ChatFormatting.YELLOW));
                tip.add(Component.literal("Each costs: " + CurrencyManager.format(denoms[t]))
                        .withStyle(ChatFormatting.GRAY));
                tip.add(Component.literal("Can afford: " + canAfford)
                        .withStyle(canAfford > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED));
                tip.add(Component.empty());
                tip.add(Component.literal("Click: 1   Shift: 64")
                        .withStyle(ChatFormatting.GRAY));
                gg.renderComponentTooltip(this.font, tip, mx, my);
                return;
            }
        }
    }

    // ── Mouse click ───────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cbx = leftPos + BTN_X, cby = topPos + BTN_Y;
        if (mouseX >= cbx && mouseX < cbx + BTN_W
         && mouseY >= cby && mouseY < cby + BTN_H) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Compact wallet formatter ──────────────────────────────────────────────

    /**
     * Formats a wallet value using abbreviated tier names so the text fits
     * inside the narrow balance panel, e.g. "69GS 1GM 1GC 1GT 5GB".
     * Uses the locally cached TIER_VALUES_CLIENT to avoid server-registry access.
     */
    private static String formatShort(long amount) {
        if (amount == 0) return "0 GB";
        StringBuilder sb = new StringBuilder();
        long rem = amount;
        // Descend from highest tier (GS=5) to lowest (GB=0)
        for (int t = TIER_ABBR.length - 1; t >= 0; t--) {
            long denom = TIER_VALUES_CLIENT[t];
            long count = rem / denom;
            if (count == 0) continue;
            rem %= denom;
            if (sb.length() > 0) sb.append("  ");
            sb.append(count).append(TIER_ABBR[t]);
        }
        return sb.toString();
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
