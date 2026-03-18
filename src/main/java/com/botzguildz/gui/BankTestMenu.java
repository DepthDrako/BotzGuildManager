package com.botzguildz.gui;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.registry.ModItems;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.MessageUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side menu for the Bank Test Custom GUI.
 *
 * Does NOT extend ChestMenu — uses AbstractContainerMenu directly so the client
 * screen can render a completely custom layout with no texture dependency.
 *
 * Data sync uses ContainerData (8 int slots):
 *   0 = wallet balance high 32 bits
 *   1 = wallet balance low  32 bits
 *   2-7 = inventory item count for currency tiers 0-5
 *
 * Slot layout (positions are relative to GUI top-left corner):
 *   Slots 0-5  : deposit  coin tiles  (COIN_X0 + t*18, DEP_Y)
 *   Slots 6-11 : withdraw coin tiles  (COIN_X0 + t*18, WD_Y)
 *   Slot  12   : close button         (CLOSE_X, CLOSE_Y)
 */
public class BankTestMenu extends AbstractContainerMenu {

    // ── GUI size (read by the Screen) ─────────────────────────────────────────
    public static final int GUI_W   = 200;
    public static final int GUI_H   = 210;

    // ── Slot positions relative to GUI top-left ───────────────────────────────
    /** X origin of the first coin column; 6 tiles × 18 px = 108 px centred in 200 px. */
    public static final int COIN_X0  = 46;
    public static final int DEP_Y    = 78;   // deposit row  (36 px below header)
    public static final int WD_Y     = 114;  // withdraw row (36 px below deposit)
    public static final int CLOSE_X  = 92;   // centre of close button 16×16 icon
    public static final int CLOSE_Y  = 190;  // adjusted for taller GUI

    // ── Slot indices ──────────────────────────────────────────────────────────
    public static final int SLOT_DEP_FIRST = 0;   // 0-5
    public static final int SLOT_WD_FIRST  = 6;   // 6-11
    public static final int SLOT_CLOSE     = 12;
    private static final int TOTAL_SLOTS   = 13;

    // ── ContainerData indices ─────────────────────────────────────────────────
    static final int D_WALLET_HI = 0;
    static final int D_WALLET_LO = 1;
    static final int D_SRC_FIRST = 2;  // D_SRC_FIRST + t = inventory count for tier t
    static final int D_COUNT     = 8;

    private final ContainerData data;
    /** Backing container — items are never placed; positions serve as click targets. */
    private final SimpleContainer display = new SimpleContainer(TOTAL_SLOTS);

    // ── Server-side constructor ───────────────────────────────────────────────

    public BankTestMenu(int id, Inventory playerInv) {
        super(ModMenuTypes.BANK_TEST_MENU.get(), id);
        if (playerInv.player instanceof ServerPlayer sp) {
            data = new ContainerData() {
                @Override
                public int get(int i) {
                    MinecraftServer server = sp.getServer();
                    if (server == null) return 0;
                    long wallet = GuildSavedData.get(server).getWallet(sp.getUUID());
                    if (i == D_WALLET_HI) return (int) (wallet >> 32);
                    if (i == D_WALLET_LO) return (int) (wallet & 0xFFFFFFFFL);
                    int t = i - D_SRC_FIRST;
                    if (t < 0 || t >= 6) return 0;
                    long count = countInInv(sp, tierItems()[t]);
                    return (int) Math.min(count, Integer.MAX_VALUE);
                }
                @Override public void set(int i, int v) { /* read-only */ }
                @Override public int getCount() { return D_COUNT; }
            };
        } else {
            data = new SimpleContainerData(D_COUNT);
        }
        addDataSlots(data);
        buildSlots();
    }

    // ── Client (fromNetwork) constructor ──────────────────────────────────────

    public static BankTestMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        return new BankTestMenu(id, playerInv, new SimpleContainerData(D_COUNT));
    }

    private BankTestMenu(int id, Inventory playerInv, SimpleContainerData clientData) {
        super(ModMenuTypes.BANK_TEST_MENU.get(), id);
        this.data = clientData;
        addDataSlots(data);
        buildSlots();
    }

    private void buildSlots() {
        for (int t = 0; t < 6; t++)
            addSlot(noPickup(t,      COIN_X0 + t * 18, DEP_Y));
        for (int t = 0; t < 6; t++)
            addSlot(noPickup(t + 6,  COIN_X0 + t * 18, WD_Y));
        addSlot(noPickup(12, CLOSE_X, CLOSE_Y));
    }

    private Slot noPickup(int idx, int x, int y) {
        return new Slot(display, idx, x, y) {
            @Override public boolean mayPickup(Player p) { return false; }
            @Override public boolean mayPlace(ItemStack s) { return false; }
        };
    }

    // ── Data accessors (called from the client Screen) ────────────────────────

    public long getWalletBalance() {
        return ((long) data.get(D_WALLET_HI) << 32)
                | (data.get(D_WALLET_LO) & 0xFFFFFFFFL);
    }

    public long getSrcCount(int tier) {
        if (tier < 0 || tier >= 6) return 0;
        return data.get(D_SRC_FIRST + tier) & 0xFFFFFFFFL;
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        GuildSavedData gsd   = GuildSavedData.get(server);
        boolean        shift = (clickType == ClickType.QUICK_MOVE);

        // ── Deposit (slots 0-5) ───────────────────────────────────────────────
        if (slotId >= SLOT_DEP_FIRST && slotId < SLOT_DEP_FIRST + 6) {
            int  t     = slotId - SLOT_DEP_FIRST;
            Item item  = tierItems()[t];
            long avail = countInInv(sp, item);
            if (avail <= 0) { broadcastChanges(); return; }
            long qty = shift ? avail : Math.min(avail, 64);
            long val = qty * ModItems.TIER_VALUES[t];
            takeTierFromInv(sp, item, qty);
            gsd.addToWallet(sp.getUUID(), val);
            sp.sendSystemMessage(MessageUtils.success(
                    "Deposited " + qty + "\u00d7 " + tierName(t)
                    + "  (" + CurrencyManager.format(val) + ") \u2192 wallet."));
            broadcastChanges();
            return;
        }

        // ── Withdraw (slots 6-11) ─────────────────────────────────────────────
        if (slotId >= SLOT_WD_FIRST && slotId < SLOT_WD_FIRST + 6) {
            int  t         = slotId - SLOT_WD_FIRST;
            long denom     = ModItems.TIER_VALUES[t];
            long wallet    = gsd.getWallet(sp.getUUID());
            long canAfford = wallet / denom;
            if (canAfford <= 0) { broadcastChanges(); return; }
            long qty  = shift ? Math.min(canAfford, 64) : 1;
            long cost = qty * denom;
            gsd.deductFromWallet(sp.getUUID(), cost);
            sp.getInventory().add(new ItemStack(tierItems()[t], (int) qty));
            sp.sendSystemMessage(MessageUtils.success(
                    "Withdrew " + qty + "\u00d7 " + tierName(t)
                    + "  (" + CurrencyManager.format(cost) + ") \u2192 hand."));
            broadcastChanges();
            return;
        }

        // ── Close ─────────────────────────────────────────────────────────────
        if (slotId == SLOT_CLOSE) {
            sp.closeContainer();
        }
    }

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static Item[] tierItems() {
        return new Item[]{
                ModItems.GUILD_BIT.get(),   ModItems.GUILD_CHIP.get(),
                ModItems.GUILD_TOKEN.get(), ModItems.GUILD_COIN.get(),
                ModItems.GUILD_MARK.get(),  ModItems.GUILD_SEAL.get()
        };
    }

    public static String tierName(int t) {
        return switch (t) {
            case 0 -> "Guild Bit";   case 1 -> "Guild Chip";
            case 2 -> "Guild Token"; case 3 -> "Guild Coin";
            case 4 -> "Guild Mark";  case 5 -> "Guild Seal";
            default -> "Coin";
        };
    }

    private static long countInInv(ServerPlayer player, Item item) {
        long n = 0;
        for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
            ItemStack st = player.getInventory().getItem(s);
            if (!st.isEmpty() && st.is(item)) n += st.getCount();
        }
        return n;
    }

    private static void takeTierFromInv(ServerPlayer player, Item item, long amount) {
        long rem = amount;
        for (int s = 0; s < player.getInventory().getContainerSize() && rem > 0; s++) {
            ItemStack st = player.getInventory().getItem(s);
            if (st.isEmpty() || !st.is(item)) continue;
            int take = (int) Math.min(rem, st.getCount());
            st.shrink(take);
            if (st.isEmpty()) player.getInventory().setItem(s, ItemStack.EMPTY);
            rem -= take;
        }
    }
}
