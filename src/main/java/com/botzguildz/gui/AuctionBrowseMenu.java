package com.botzguildz.gui;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.AuctionHouseData;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.market.AuctionListing;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Auction House browse GUI — 6-row chest.
 *
 * <pre>
 * Row 0 (0-8):  [All:0][Fixed:1][Timed:2][glass...][MyListings:8]
 * Row 1 (9-17): [SortPrice:9][SortTime:10][glass...][ListItem:17]
 * Rows 2-4 (18-44): Listings (up to 27 per page)
 * Row 5 (45-53): [Close:45][Prev:46][glass][glass][Info:49][glass][glass][Next:52][Close:53]
 * </pre>
 */
public class AuctionBrowseMenu extends ChestMenu {

    public enum Filter { ALL, FIXED, TIMED, MY_LISTINGS }
    public enum Sort { PRICE_ASC, PRICE_DESC, TIME_ASC }

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    // Row 0 — filter tabs
    private static final int TAB_ALL        = 0;
    private static final int TAB_FIXED      = 1;
    private static final int TAB_TIMED      = 2;
    private static final int TAB_MY_LISTINGS = 8;

    // Row 1 — sort + actions
    private static final int SORT_PRICE = 9;
    private static final int SORT_TIME  = 10;
    private static final int LIST_ITEM  = 17;

    // Rows 2-4 — listing slots
    private static final int LISTING_START = 18;
    private static final int LISTING_END   = 44;

    // Row 5 — navigation
    private static final int CLOSE_L    = 45;
    private static final int PREV_SLOT  = 46;
    private static final int INFO_SLOT  = 49;
    private static final int NEXT_SLOT  = 52;
    private static final int CLOSE_R    = 53;

    private final SimpleContainer chest;
    private Filter filter;
    private Sort sort = Sort.PRICE_ASC;
    private int page;

    // The listing UUIDs currently displayed (indexed by slot offset from LISTING_START)
    private final List<UUID> displayedListingIds = new ArrayList<>();

    private AuctionBrowseMenu(int id, Inventory playerInv, Filter filter, int page, SimpleContainer chest) {
        super(ModMenuTypes.AUCTION_BROWSE_MENU.get(), id, playerInv, chest, ROWS);
        this.chest  = chest;
        this.filter = filter;
        this.page   = Math.max(1, page);
        populateItems(playerInv.player);
    }

    public AuctionBrowseMenu(int id, Inventory playerInv, Filter filter, int page) {
        this(id, playerInv, filter, page, new SimpleContainer(CHEST_SIZE));
    }

    public static AuctionBrowseMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        int filterOrdinal = buf.readInt();
        int page = buf.readInt();
        Filter filter = filterOrdinal >= 0 && filterOrdinal < Filter.values().length
                ? Filter.values()[filterOrdinal] : Filter.ALL;
        return new AuctionBrowseMenu(id, playerInv, filter, page, new SimpleContainer(CHEST_SIZE));
    }

    public void populateItems(Player player) {
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());
        displayedListingIds.clear();

        // ── Filter tabs ──────────────────────────────────────────────────────
        chest.setItem(TAB_ALL,         makeTab("All Listings",  ChatFormatting.WHITE,  filter == Filter.ALL));
        chest.setItem(TAB_FIXED,       makeTab("Fixed Price",   ChatFormatting.YELLOW, filter == Filter.FIXED));
        chest.setItem(TAB_TIMED,       makeTab("Timed Auctions",ChatFormatting.AQUA,   filter == Filter.TIMED));
        chest.setItem(TAB_MY_LISTINGS, makeTab("My Listings",   ChatFormatting.GREEN,  filter == Filter.MY_LISTINGS));

        // ── Sort buttons ─────────────────────────────────────────────────────
        chest.setItem(SORT_PRICE, makeButton(Items.GOLD_NUGGET,
                sort == Sort.PRICE_ASC ? "Sort: Price ▲" : "Sort: Price ▼", ChatFormatting.GOLD));
        chest.setItem(SORT_TIME, makeButton(Items.CLOCK,
                "Sort: Time Left", ChatFormatting.AQUA));
        chest.setItem(LIST_ITEM, makeButton(Items.WRITABLE_BOOK,
                "Create Listing", ChatFormatting.GREEN));

        // ── Listings ─────────────────────────────────────────────────────────
        List<AuctionListing> listings = getFilteredSortedListings(player);
        int pageSize = LISTING_END - LISTING_START + 1; // 27
        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) pageSize));
        page = Math.min(page, totalPages);

        int startIdx = (page - 1) * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int idx = startIdx + i;
            if (idx >= listings.size()) break;
            AuctionListing listing = listings.get(idx);
            chest.setItem(LISTING_START + i, makeListingItem(listing));
            displayedListingIds.add(listing.getListingId());
        }

        // ── Navigation bar ────────────────────────────────────────────────────
        ItemStack close = new ItemStack(Items.BARRIER);
        close.setHoverName(styledName("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_L, close);
        chest.setItem(CLOSE_R, close.copy());

        if (page > 1) {
            chest.setItem(PREV_SLOT, makeButton(Items.ARROW,
                    "◄ Previous Page (" + (page - 1) + "/" + totalPages + ")", ChatFormatting.YELLOW));
        }
        if (page < totalPages) {
            chest.setItem(NEXT_SLOT, makeButton(Items.ARROW,
                    "Next Page ► (" + (page + 1) + "/" + totalPages + ")", ChatFormatting.YELLOW));
        }

        ItemStack info = new ItemStack(Items.PAPER);
        info.setHoverName(styledName("Auction House", ChatFormatting.GOLD));
        appendLore(info, lore("Page " + page + "/" + totalPages
                + "  |  " + listings.size() + " listing(s)", ChatFormatting.GRAY));
        chest.setItem(INFO_SLOT, info);

        broadcastChanges();
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < CHEST_SIZE) {
            if (!(player instanceof ServerPlayer sp)) return;
            handleChestClick(slotId, button, sp);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void handleChestClick(int slotId, int button, ServerPlayer player) {
        MinecraftServer server = player.getServer();

        switch (slotId) {
            case TAB_ALL        -> { filter = Filter.ALL;         page = 1; populateItems(player); }
            case TAB_FIXED      -> { filter = Filter.FIXED;       page = 1; populateItems(player); }
            case TAB_TIMED      -> { filter = Filter.TIMED;       page = 1; populateItems(player); }
            case TAB_MY_LISTINGS -> { filter = Filter.MY_LISTINGS; page = 1; populateItems(player); }
            case SORT_PRICE -> {
                sort = (sort == Sort.PRICE_ASC) ? Sort.PRICE_DESC : Sort.PRICE_ASC;
                populateItems(player);
            }
            case SORT_TIME -> { sort = Sort.TIME_ASC; populateItems(player); }
            case LIST_ITEM -> {
                player.closeContainer();
                player.sendSystemMessage(MessageUtils.info(
                        "Use /ah sell <price> or /ah auction <bid> <minutes> to create a listing."));
            }
            case CLOSE_L, CLOSE_R -> player.closeContainer();
            case PREV_SLOT -> { if (page > 1) { page--; populateItems(player); } }
            case NEXT_SLOT -> { page++; populateItems(player); }
            default -> {
                if (slotId >= LISTING_START && slotId <= LISTING_END && server != null) {
                    int idx = slotId - LISTING_START;
                    if (idx < displayedListingIds.size()) {
                        handleListingClick(displayedListingIds.get(idx), button, player, server);
                    }
                }
            }
        }
    }

    private void handleListingClick(UUID listingId, int button, ServerPlayer player, MinecraftServer server) {
        AuctionHouseData ahData = AuctionHouseData.get(server);
        AuctionListing listing = ahData.getListing(listingId).orElse(null);
        if (listing == null || !listing.isActive() || listing.isExpired()) {
            player.sendSystemMessage(MessageUtils.error("This listing is no longer available."));
            populateItems(player);
            return;
        }

        if (listing.getSellerUUID().equals(player.getUUID())) {
            player.sendSystemMessage(MessageUtils.info(
                    "This is your listing. Use /ah cancel " + listing.getListingId().toString().substring(0, 8)
                            + " to cancel it."));
            return;
        }

        if (listing.getType() == AuctionListing.Type.FIXED) {
            // Buy immediately
            long price = listing.getPrice();
            long wallet = GuildSavedData.get(server).getWallet(player.getUUID());
            if (wallet < price) {
                player.sendSystemMessage(MessageUtils.error("Insufficient funds. Need "
                        + CurrencyManager.format(price) + ", you have "
                        + CurrencyManager.format(wallet) + "."));
                return;
            }
            // Collect fee
            int feePercent = com.botzguildz.config.GuildConfig.AH_LISTING_FEE_PERCENT.get();
            long fee = Math.max(0, price * feePercent / 100);
            long sellerReceives = price - fee;

            GuildSavedData.get(server).deductFromWallet(player.getUUID(), price);
            ahData.queueDelivery(listing.getSellerUUID(), ItemStack.EMPTY, sellerReceives,
                    "Item sold: " + listing.getItem().getHoverName().getString() + " for "
                            + CurrencyManager.format(sellerReceives) + " (fee: " + CurrencyManager.format(fee) + ")");
            listing.setActive(false);
            ahData.setDirty();

            // Give item to buyer
            ItemStack item = listing.getItem();
            boolean added = player.getInventory().add(item);
            if (!added) player.drop(item, false);

            player.sendSystemMessage(MessageUtils.success("Purchased "
                    + item.getHoverName().getString() + " for "
                    + CurrencyManager.format(price) + "!"));

            // Pay seller if online
            ServerPlayer seller = server.getPlayerList().getPlayer(listing.getSellerUUID());
            if (seller != null) ahData.deliverPending(seller);

            populateItems(player);

        } else {
            // Timed — prompt to bid
            player.sendSystemMessage(MessageUtils.info(
                    "Current bid: " + CurrencyManager.format(listing.getPrice())
                            + "  Time left: " + listing.formatTimeLeft()
                            + "  — Type your bid amount in chat and use /ah bid "
                            + listing.getListingId().toString().substring(0, 8) + " <amount>"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<AuctionListing> getFilteredSortedListings(Player player) {
        List<AuctionListing> base;
        MinecraftServer server = (player instanceof ServerPlayer sp) ? sp.getServer() : null;
        if (server == null) return new ArrayList<>();

        base = AuctionHouseData.get(server).getActiveListings();

        List<AuctionListing> filtered = new ArrayList<>();
        for (AuctionListing l : base) {
            if (filter == Filter.FIXED && l.getType() != AuctionListing.Type.FIXED) continue;
            if (filter == Filter.TIMED && l.getType() != AuctionListing.Type.TIMED) continue;
            if (filter == Filter.MY_LISTINGS && !l.getSellerUUID().equals(player.getUUID())) continue;
            filtered.add(l);
        }

        filtered.sort((a, b) -> {
            if (sort == Sort.PRICE_DESC) return Long.compare(b.getPrice(), a.getPrice());
            if (sort == Sort.TIME_ASC)   return Long.compare(a.getExpiresAt(), b.getExpiresAt());
            return Long.compare(a.getPrice(), b.getPrice()); // PRICE_ASC default
        });

        return filtered;
    }

    private static ItemStack makeTab(String label, ChatFormatting color, boolean selected) {
        ItemStack item = new ItemStack(selected ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE);
        item.setHoverName(Component.literal(label)
                .withStyle(Style.EMPTY.withColor(selected ? ChatFormatting.GREEN : color)
                        .withBold(selected).withItalic(false)));
        if (selected) appendLore(item, lore("Currently viewing", ChatFormatting.YELLOW));
        return item;
    }

    private static ItemStack makeButton(net.minecraft.world.item.Item icon, String label, ChatFormatting color) {
        ItemStack item = new ItemStack(icon);
        item.setHoverName(styledName(label, color));
        return item;
    }

    private static ItemStack makeListingItem(AuctionListing listing) {
        ItemStack display = listing.getItem();
        String name = display.getHoverName().getString();

        appendLore(display, lore("────────────────────", ChatFormatting.DARK_GRAY));
        if (listing.getType() == AuctionListing.Type.FIXED) {
            appendLore(display, Component.literal("Price: ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false))
                    .append(Component.literal(CurrencyManager.formatShort(listing.getPrice()))
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false))));
            appendLore(display, lore("Click to buy", ChatFormatting.GREEN));
        } else {
            appendLore(display, Component.literal("Current Bid: ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false))
                    .append(Component.literal(CurrencyManager.formatShort(listing.getPrice()))
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false))));
            appendLore(display, Component.literal("Time Left: ")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withItalic(false))
                    .append(Component.literal(listing.formatTimeLeft())
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withItalic(false))));
            appendLore(display, lore("Click for bid instructions", ChatFormatting.YELLOW));
        }
        appendLore(display, lore("────────────────────", ChatFormatting.DARK_GRAY));
        return display;
    }

    // ── Required overrides ────────────────────────────────────────────────────

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    // ── Static helpers ────────────────────────────────────────────────────────

    static ItemStack pane() {
        ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        s.setHoverName(Component.literal(" ").withStyle(Style.EMPTY.withItalic(false)));
        return s;
    }

    static Component styledName(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    static Component lore(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    static void appendLore(ItemStack stack, Component line) {
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore;
        if (display.contains("Lore", 9)) {
            lore = display.getList("Lore", 8);
        } else {
            lore = new ListTag();
            display.put("Lore", lore);
        }
        lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
    }
}
