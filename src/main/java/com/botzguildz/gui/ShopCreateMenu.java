package com.botzguildz.gui;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.MarketData;
import com.botzguildz.market.MarketListing;
import com.botzguildz.market.PlayerShop;
import com.botzguildz.registry.ModItems;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.MessageUtils;
import com.botzguildz.util.ShopSignHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;

/**
 * Shop Create GUI — 6-row chest.
 *
 * <pre>
 * Row 0 (0-8):   [glass ×4][TITLE:4][glass ×4]
 * Row 1 (9-17):  [glass ×4][ITEM:13][glass ×4]   ← drag item here to list it
 * Row 2 (18-26): [glass ×9]
 * Row 3 (27-35): [BIT:27][CHIP:28][TOKEN:29][COIN:30][MARK:31][SEAL:32][glass ×3]
 *                  Left-click = +1 denomination  |  Right-click = -1 denomination
 *                  Item stack count mirrors how many of that denomination are in the price
 * Row 4 (36-44): [glass ×9]
 * Row 5 (45-53): [CANCEL:45][glass][CLR:47][glass][PRICE:49][glass ×3][CONFIRM:53]
 * </pre>
 */
public class ShopCreateMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    // Row 0
    private static final int TITLE_SLOT = 4;

    // Row 1 — item to list
    private static final int ITEM_SLOT = 13;

    // Row 3 — one slot per denomination (BIT → SEAL, left to right)
    private static final int DENOM_ROW = 27; // slots 27-32

    // Row 5 — controls
    private static final int CANCEL_BTN  = 45;
    private static final int CLEAR_BTN   = 47;
    private static final int PRICE_SLOT  = 49;
    private static final int CONFIRM_BTN = 53;

    // Denomination data — index 0=BIT … 5=SEAL
    private static final long[]   DENOM_VALUES = { 1L, 8L, 64L, 512L, 4_096L, 32_768L };
    private static final String[] DENOM_NAMES  = {
            "GB", "GCh", "GT",
            "GC", "GM",  "GS"
    };

    // ── Per-instance state ────────────────────────────────────────────────────

    /** How many of each denomination the player has added to the price (index 0–5). */
    private final long[] denomCounts = new long[6];

    private ItemStack listItem  = ItemStack.EMPTY;
    private boolean   confirmed = false;

    /** Null on the client (fromNetwork). Server-side only. */
    private final BlockPos shopBlockPos;

    // ── Constructors ──────────────────────────────────────────────────────────

    public ShopCreateMenu(int id, Inventory playerInv, BlockPos shopBlockPos) {
        this(id, playerInv, new SimpleContainer(CHEST_SIZE), shopBlockPos);
    }

    private ShopCreateMenu(int id, Inventory playerInv, SimpleContainer chest, BlockPos shopBlockPos) {
        super(ModMenuTypes.SHOP_CREATE_MENU.get(), id, playerInv, chest, ROWS);
        this.shopBlockPos = shopBlockPos;
        populateItems(playerInv.player);
    }

    public static ShopCreateMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new ShopCreateMenu(id, playerInv, new SimpleContainer(CHEST_SIZE), pos);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long totalPrice() {
        long total = 0;
        for (int i = 0; i < 6; i++) total += denomCounts[i] * DENOM_VALUES[i];
        return total;
    }

    private static Item[] denomItems() {
        return new Item[]{
                ModItems.GUILD_BIT.get(),
                ModItems.GUILD_CHIP.get(),
                ModItems.GUILD_TOKEN.get(),
                ModItems.GUILD_COIN.get(),
                ModItems.GUILD_MARK.get(),
                ModItems.GUILD_SEAL.get()
        };
    }

    // ── Item population ───────────────────────────────────────────────────────

    public void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) this.getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());

        // ── Row 0 — title ─────────────────────────────────────────────────────
        ItemStack title = new ItemStack(Items.WRITABLE_BOOK);
        title.setHoverName(styledName("Create Shop Listing", ChatFormatting.AQUA));
        appendLore(title, lore("1. Drag the item to sell into the centre slot", ChatFormatting.GRAY));
        appendLore(title, lore("2. Click currency icons to build the price", ChatFormatting.GRAY));
        appendLore(title, lore("   Left-click = +1  |  Right-click = -1", ChatFormatting.DARK_GRAY));
        appendLore(title, lore("3. Click Confirm to open your shop", ChatFormatting.GRAY));
        chest.setItem(TITLE_SLOT, title);

        // ── Row 1 — item slot ─────────────────────────────────────────────────
        if (listItem.isEmpty()) {
            ItemStack placeholder = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            placeholder.setHoverName(styledName("→  Place item to sell here  ←", ChatFormatting.GREEN));
            appendLore(placeholder, lore("Click while holding an item from your inventory", ChatFormatting.GRAY));
            chest.setItem(ITEM_SLOT, placeholder);
        } else {
            chest.setItem(ITEM_SLOT, listItem.copy());
        }

        // ── Row 3 — denomination icons ────────────────────────────────────────
        Item[] items = denomItems();
        for (int i = 0; i < 6; i++) {
            long count = denomCounts[i];
            ItemStack btn = new ItemStack(items[i]);

            if (count > 0) {
                // Show actual count on the item (capped at 64 for visual)
                btn.setCount((int) Math.min(count, 64));
                btn.setHoverName(styledName("×" + count + "  " + DENOM_NAMES[i], ChatFormatting.GOLD));
                appendLore(btn, lore("= " + CurrencyManager.formatShort(count * DENOM_VALUES[i]),
                        ChatFormatting.YELLOW));
            } else {
                // No denominations added yet — still show the item (count stays 1)
                btn.setHoverName(styledName(DENOM_NAMES[i], ChatFormatting.GRAY));
                appendLore(btn, lore("×0 in price", ChatFormatting.DARK_GRAY));
            }
            appendLore(btn, lore("Left-click: +1  |  Right-click: -1", ChatFormatting.DARK_GRAY));
            chest.setItem(DENOM_ROW + i, btn);
        }

        // ── Row 5 — control bar ───────────────────────────────────────────────
        ItemStack cancel = new ItemStack(Items.BARRIER);
        cancel.setHoverName(styledName("✗  Cancel", ChatFormatting.RED));
        appendLore(cancel, lore("Returns your item and closes", ChatFormatting.GRAY));
        chest.setItem(CANCEL_BTN, cancel);

        ItemStack clear = new ItemStack(Items.RED_DYE);
        clear.setHoverName(styledName("Clear Price", ChatFormatting.YELLOW));
        appendLore(clear, lore("Reset all denominations to ×0", ChatFormatting.GRAY));
        chest.setItem(CLEAR_BTN, clear);

        long price = totalPrice();
        boolean ready = price > 0 && !listItem.isEmpty();

        ItemStack priceDisplay = new ItemStack(Items.GOLD_NUGGET);
        priceDisplay.setHoverName(styledName(
                price == 0 ? "Price: Not set" : "Price: " + CurrencyManager.formatShort(price),
                price == 0 ? ChatFormatting.GRAY : ChatFormatting.GOLD));
        appendLore(priceDisplay, lore("Click currency items above to set", ChatFormatting.DARK_GRAY));
        chest.setItem(PRICE_SLOT, priceDisplay);

        ItemStack confirm = new ItemStack(ready ? Items.EMERALD : Items.GRAY_DYE);
        confirm.setHoverName(styledName(
                "✔  Create Listing", ready ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        if (ready) {
            appendLore(confirm, lore(listItem.getHoverName().getString()
                    + " ×" + listItem.getCount(), ChatFormatting.YELLOW));
            appendLore(confirm, lore("for " + CurrencyManager.formatShort(price) + " each",
                    ChatFormatting.YELLOW));
        } else {
            appendLore(confirm, lore(
                    listItem.isEmpty() ? "Place an item first" : "Set a price first",
                    ChatFormatting.RED));
        }
        chest.setItem(CONFIRM_BTN, confirm);

        broadcastChanges();
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < CHEST_SIZE) {
            if (!(player instanceof ServerPlayer sp)) return;
            handleClick(slotId, button, sp);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void handleClick(int slotId, int mouseButton, ServerPlayer player) {

        // ── Denomination slots 27–32 ──────────────────────────────────────────
        if (slotId >= DENOM_ROW && slotId < DENOM_ROW + 6) {
            int di = slotId - DENOM_ROW;
            if (mouseButton == 1) {                      // right-click = subtract
                if (denomCounts[di] > 0) denomCounts[di]--;
            } else {                                     // left-click = add
                denomCounts[di]++;
            }
            populateItems(player);
            return;
        }

        switch (slotId) {

            // ── Item slot — swap cursor ↔ listItem ────────────────────────────
            case ITEM_SLOT -> {
                ItemStack cursor = getCarried();
                if (!cursor.isEmpty()) {
                    ItemStack prev = listItem.copy();
                    listItem = cursor.copy();
                    setCarried(prev.isEmpty() ? ItemStack.EMPTY : prev);
                } else if (!listItem.isEmpty()) {
                    setCarried(listItem.copy());
                    listItem = ItemStack.EMPTY;
                }
                populateItems(player);
            }

            // ── Clear price ───────────────────────────────────────────────────
            case CLEAR_BTN -> {
                Arrays.fill(denomCounts, 0);
                populateItems(player);
            }

            // ── Cancel ────────────────────────────────────────────────────────
            case CANCEL_BTN -> {
                returnItemToPlayer(player);
                player.closeContainer();
            }

            // ── Confirm ───────────────────────────────────────────────────────
            case CONFIRM_BTN -> {
                long price = totalPrice();
                if (listItem.isEmpty()) {
                    player.sendSystemMessage(MessageUtils.error("Place an item in the centre slot first."));
                    return;
                }
                if (price <= 0) {
                    player.sendSystemMessage(MessageUtils.error("Set a price before confirming."));
                    return;
                }
                if (shopBlockPos == null) {
                    player.sendSystemMessage(MessageUtils.error("No shop block was registered."));
                    return;
                }

                String dimId = player.level().dimension().location().toString();
                MarketData market = MarketData.get(player.getServer());

                // Find the existing shop at this chest, or create a new one for it
                PlayerShop shop = market.getOrCreateShopAt(
                        player.getUUID(), player.getName().getString(), shopBlockPos, dimId);
                shop.setOpen(true); // auto-open on first listing

                int maxListings = GuildConfig.SHOP_MAX_LISTINGS_PER_PLAYER.get();
                if (shop.getListings().size() >= maxListings) {
                    player.sendSystemMessage(MessageUtils.error(
                            "This shop is full (" + maxListings + " listings max)."));
                    return;
                }

                // The listItem is a price/type template — return it to the player,
                // stock must come from the physical chest block.
                ItemStack toList = listItem.copy();
                if (!player.getInventory().add(listItem.copy())) player.drop(listItem.copy(), false);
                listItem = ItemStack.EMPTY;

                shop.addListing(new MarketListing(toList, price, toList.getCount()));
                market.setDirty();

                // Update adjacent signs
                if (player.level() instanceof ServerLevel sl) {
                    ShopSignHelper.updateAdjacentSigns(sl, shopBlockPos, shop);
                }

                final String name = toList.getHoverName().getString();
                player.sendSystemMessage(MessageUtils.success(
                        "Listed " + name + " for " + CurrencyManager.format(price) + " each."));
                player.sendSystemMessage(MessageUtils.info(
                        "Stock your chest with " + name + " — buyers draw from it directly."));

                confirmed = true;
                player.closeContainer();
            }
        }
    }

    // ── Cleanup when menu closes ──────────────────────────────────────────────

    @Override
    public void removed(Player player) {
        super.removed(player);
        returnItemToPlayer(player);
    }

    private void returnItemToPlayer(Player player) {
        if (!listItem.isEmpty() && !confirmed) {
            if (!player.getInventory().add(listItem.copy())) {
                player.drop(listItem.copy(), false);
            }
            listItem = ItemStack.EMPTY;
        }
    }

    // ── Required overrides ────────────────────────────────────────────────────

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static ItemStack pane() { return PersonalBankMenu.pane(); }

    private static Component styledName(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    static Component lore(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    static void appendLore(ItemStack stack, Component line) {
        PersonalBankMenu.appendLore(stack, line);
    }
}
