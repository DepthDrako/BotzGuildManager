package com.botzguildz.gui;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.MarketData;
import com.botzguildz.market.GuildShop;
import com.botzguildz.market.MarketListing;
import com.botzguildz.market.PlayerShop;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.ChestStockHelper;
import com.botzguildz.util.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One shop's listing view — 6-row chest.
 * Works for both guild shops and player shops.
 *
 * <pre>
 * Row 0 (0-8):  [BACK:0][glass][glass][glass][SHOP_INFO:4][glass][glass][glass][glass]
 * Rows 1-4 (9-44): Listings (up to 36 per page)
 * Row 5 (45-53): [Close:45][Prev:46][glass][glass][AddItem:49 if owner][glass][glass][Next:52][Close:53]
 * </pre>
 */
public class ShopViewMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    private static final int BACK_BTN   = 0;
    private static final int INFO_SLOT  = 4;
    private static final int LISTING_START = 9;
    private static final int LISTING_END   = 44;  // 36 slots
    private static final int CLOSE_L    = 45;
    private static final int PREV_SLOT  = 46;
    private static final int ADD_ITEM   = 49;
    private static final int NEXT_SLOT  = 52;
    private static final int CLOSE_R    = 53;

    /** If non-null, this is a guild shop keyed by guildId */
    private final UUID guildId;
    /** If non-null, this is a player shop (ownerUUID = shop owner) */
    private final UUID ownerUUID;
    /**
     * If non-null, show only this specific shop.
     * If null with ownerUUID set, show ALL shops for that player combined.
     */
    private final UUID shopId;
    /** True if the viewer is the shop owner (or guild officer) */
    private final boolean isOwner;
    private int page;

    // Listing IDs displayed in current page (index within LISTING_START..LISTING_END)
    private final List<UUID> displayedListingIds = new ArrayList<>();
    // Maps listingId → shopId so the purchase handler knows which chest to pull from
    private final Map<UUID, UUID> listingShopMap = new HashMap<>();

    /** No specific shopId — shows all shops for ownerUUID (or a guild shop). */
    public ShopViewMenu(int id, Inventory playerInv, UUID guildId, UUID ownerUUID,
                        boolean isOwner, int page) {
        this(id, playerInv, guildId, ownerUUID, null, isOwner, page, new SimpleContainer(CHEST_SIZE));
    }

    /** Specific shopId — shows only that one shop's listings. */
    public ShopViewMenu(int id, Inventory playerInv, UUID guildId, UUID ownerUUID,
                        UUID shopId, boolean isOwner, int page) {
        this(id, playerInv, guildId, ownerUUID, shopId, isOwner, page, new SimpleContainer(CHEST_SIZE));
    }

    private ShopViewMenu(int id, Inventory playerInv, UUID guildId, UUID ownerUUID,
                         UUID shopId, boolean isOwner, int page, SimpleContainer chest) {
        super(ModMenuTypes.SHOP_VIEW_MENU.get(), id, playerInv, chest, ROWS);
        this.guildId   = guildId;
        this.ownerUUID = ownerUUID;
        this.shopId    = shopId;
        this.isOwner   = isOwner;
        this.page      = Math.max(1, page);
        populateItems(playerInv.player);
    }

    /**
     * Wire protocol (server→client):
     * <pre>
     *   boolean  isGuild
     *   UUID     targetId   (guildId or ownerUUID)
     *   boolean  isOwner
     *   int      page
     *   boolean  hasShopId
     *   [UUID    shopId]    (only when hasShopId == true)
     * </pre>
     */
    public static ShopViewMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        boolean isGuild  = buf.readBoolean();
        UUID    targetId = buf.readUUID();
        boolean owner    = buf.readBoolean();
        int     page     = buf.readInt();
        UUID    shopId   = buf.readBoolean() ? buf.readUUID() : null;
        SimpleContainer chest = new SimpleContainer(CHEST_SIZE);
        return isGuild
                ? new ShopViewMenu(id, playerInv, targetId, null,     null,   owner, page, chest)
                : new ShopViewMenu(id, playerInv, null,     targetId, shopId, owner, page, chest);
    }

    public void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) this.getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, AuctionBrowseMenu.pane());
        displayedListingIds.clear();
        listingShopMap.clear();

        MinecraftServer server = (player instanceof ServerPlayer sp) ? sp.getServer() : null;
        ServerLevel level = (server != null) ? server.overworld() : null;
        // Use the player's actual dimension for chest access
        if (player instanceof ServerPlayer sp) level = (ServerLevel) sp.level();

        String shopName = "Shop";

        // ── Collect listings ──────────────────────────────────────────────────
        // For player shops: pair each listing with its shopId so purchases know
        // which chest to draw from.
        record Entry(MarketListing listing, UUID sId, int chestStock) {}
        List<Entry> entries = new ArrayList<>();

        if (server != null) {
            MarketData market = MarketData.get(server);

            if (guildId != null) {
                // Guild shop — stock tracked internally (unlimited)
                GuildShop gs = market.getGuildShop(guildId).orElse(null);
                if (gs != null) {
                    shopName = gs.getGuildName() + "'s Shop";
                    for (MarketListing l : gs.getListings())
                        entries.add(new Entry(l, null, -1));
                }
            } else if (ownerUUID != null) {
                // Player shops — one or all, stock read live from chest
                List<PlayerShop> shops;
                if (shopId != null) {
                    PlayerShop s = market.getShopById(shopId).orElse(null);
                    shops = s != null ? List.of(s) : List.of();
                } else {
                    shops = market.getPlayerShops(ownerUUID);
                }
                String ownerName = shops.isEmpty() ? "Unknown" : shops.get(0).getOwnerName();
                shopName = ownerName + "'s Shop" + (shops.size() > 1 ? " (" + shops.size() + " locations)" : "");

                for (PlayerShop ps : shops) {
                    for (MarketListing l : ps.getListings()) {
                        int stock = (level != null && ps.getShopBlockPos() != null)
                                ? ChestStockHelper.countStock(level, ps.getShopBlockPos(), l.getItem())
                                : 0;
                        // Only show to non-owners if there's actual stock
                        if (isOwner || stock > 0) {
                            entries.add(new Entry(l, ps.getShopId(), stock));
                            listingShopMap.put(l.getListingId(), ps.getShopId());
                        }
                    }
                }
            }
        }

        int pageSize   = LISTING_END - LISTING_START + 1; // 36
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        page = Math.min(page, totalPages);

        int startIdx = (page - 1) * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int idx = startIdx + i;
            if (idx >= entries.size()) break;
            Entry e = entries.get(idx);
            ItemStack display = makeListingItem(e.listing(), isOwner, e.chestStock());
            chest.setItem(LISTING_START + i, display);
            displayedListingIds.add(e.listing().getListingId());
        }

        // Row 0
        ItemStack back = new ItemStack(Items.ARROW);
        back.setHoverName(AuctionBrowseMenu.styledName("Back", ChatFormatting.YELLOW));
        chest.setItem(BACK_BTN, back);

        ItemStack info = new ItemStack(Items.CHEST);
        info.setHoverName(AuctionBrowseMenu.styledName(shopName, ChatFormatting.GOLD));
        AuctionBrowseMenu.appendLore(info, AuctionBrowseMenu.lore(
                entries.size() + " listing(s)", ChatFormatting.GRAY));
        chest.setItem(INFO_SLOT, info);

        // Row 5
        ItemStack close = new ItemStack(Items.BARRIER);
        close.setHoverName(AuctionBrowseMenu.styledName("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_L, close);
        chest.setItem(CLOSE_R, close.copy());

        if (page > 1) {
            chest.setItem(PREV_SLOT, makeButton(Items.ARROW,
                    "◄ Page " + (page-1) + "/" + totalPages, ChatFormatting.YELLOW));
        }
        if (page < totalPages) {
            chest.setItem(NEXT_SLOT, makeButton(Items.ARROW,
                    "Page " + (page+1) + "/" + totalPages + " ►", ChatFormatting.YELLOW));
        }

        if (isOwner) {
            ItemStack addItem = new ItemStack(Items.EMERALD);
            addItem.setHoverName(AuctionBrowseMenu.styledName("Add Item", ChatFormatting.GREEN));
            AuctionBrowseMenu.appendLore(addItem, AuctionBrowseMenu.lore(
                    "Use /shop add <price> or /guild shop add <price>", ChatFormatting.GRAY));
            chest.setItem(ADD_ITEM, addItem);
        }

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
        switch (slotId) {
            case BACK_BTN, CLOSE_L, CLOSE_R -> player.closeContainer();
            case PREV_SLOT -> { if (page > 1) { page--; populateItems(player); } }
            case NEXT_SLOT -> { page++; populateItems(player); }
            case ADD_ITEM -> {
                if (isOwner) {
                    player.sendSystemMessage(MessageUtils.info(
                            "Hold an item and use " + (guildId != null
                                    ? "/guild shop add <price>" : "/shop add <price>")
                                    + " to add it to your shop."));
                }
            }
            default -> {
                if (slotId >= LISTING_START && slotId <= LISTING_END) {
                    int idx = slotId - LISTING_START;
                    if (idx < displayedListingIds.size()) {
                        UUID listingId = displayedListingIds.get(idx);
                        if (isOwner && button == 1 /* right click */) {
                            removeListingOwner(listingId, player);
                        } else if (!isOwner || button == 0) {
                            purchaseListing(listingId, player);
                        }
                    }
                }
            }
        }
    }

    // ── Purchase logic ────────────────────────────────────────────────────────

    private void purchaseListing(UUID listingId, ServerPlayer buyer) {
        MinecraftServer server = buyer.getServer();
        if (server == null) return;

        MarketData market     = MarketData.get(server);
        GuildSavedData guildData = GuildSavedData.get(server);

        Optional<MarketListing> listingOpt = findListing(listingId, market);
        if (listingOpt.isEmpty()) {
            buyer.sendSystemMessage(MessageUtils.error("Listing not found."));
            populateItems(buyer);
            return;
        }
        MarketListing listing = listingOpt.get();

        // ── Player shop: verify chest stock ──────────────────────────────────
        PlayerShop sourceShop = null;
        if (ownerUUID != null) {
            UUID sId = listingShopMap.get(listingId);
            sourceShop = (sId != null) ? market.getShopById(sId).orElse(null) : null;
            if (sourceShop == null || sourceShop.getShopBlockPos() == null) {
                buyer.sendSystemMessage(MessageUtils.error("This shop has no physical location."));
                return;
            }
            ServerLevel level = (ServerLevel) buyer.level();
            int needed    = listing.getItem().getCount();
            int available = ChestStockHelper.countStock(level, sourceShop.getShopBlockPos(), listing.getItem());
            if (available < needed) {
                buyer.sendSystemMessage(MessageUtils.error("Out of stock — the shop chest is empty."));
                populateItems(buyer);
                return;
            }
        }

        // ── Guild shop: check static stock ────────────────────────────────────
        if (guildId != null && !listing.isUnlimited() && listing.getStock() <= 0) {
            buyer.sendSystemMessage(MessageUtils.error("This item is out of stock."));
            populateItems(buyer);
            return;
        }

        long price  = listing.getPriceEach();
        long wallet = guildData.getWallet(buyer.getUUID());
        if (wallet < price) {
            buyer.sendSystemMessage(MessageUtils.error("Insufficient funds. Need "
                    + CurrencyManager.format(price) + ", you have "
                    + CurrencyManager.format(wallet) + "."));
            return;
        }

        if (ownerUUID != null && ownerUUID.equals(buyer.getUUID())) {
            buyer.sendSystemMessage(MessageUtils.error("You can't buy from your own shop."));
            return;
        }

        // ── Deduct buyer funds ────────────────────────────────────────────────
        guildData.deductFromWallet(buyer.getUUID(), price);

        // ── Pay seller ────────────────────────────────────────────────────────
        if (guildId != null) {
            var guildObj = guildData.getGuildById(guildId);
            if (guildObj != null) guildObj.deposit(price);
            guildData.setDirty();
        } else if (ownerUUID != null) {
            guildData.addToWallet(ownerUUID, price);
        }

        // ── Pull item from chest (player shop) or give directly (guild shop) ──
        ItemStack item = listing.getItem();
        if (sourceShop != null) {
            ServerLevel level = (ServerLevel) buyer.level();
            ChestStockHelper.takeStock(level, sourceShop.getShopBlockPos(), item, item.getCount());
        } else {
            // Guild shop: use internal stock counter
            listing.decrementStock();
        }
        market.setDirty();

        if (!buyer.getInventory().add(item.copy())) buyer.drop(item.copy(), false);

        buyer.sendSystemMessage(MessageUtils.success("Purchased "
                + item.getHoverName().getString()
                + (item.getCount() > 1 ? " ×" + item.getCount() : "")
                + " for " + CurrencyManager.format(price) + "!"));

        populateItems(buyer);
    }

    // ── Owner remove listing ──────────────────────────────────────────────────

    private void removeListingOwner(UUID listingId, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        MarketData market = MarketData.get(server);
        Optional<MarketListing> listingOpt = findListing(listingId, market);
        if (listingOpt.isEmpty()) {
            player.sendSystemMessage(MessageUtils.error("Listing not found."));
            populateItems(player);
            return;
        }
        MarketListing listing = listingOpt.get();

        if (guildId != null) {
            GuildShop shop = market.getGuildShop(guildId).orElse(null);
            if (shop != null) { shop.removeListing(listingId); market.setDirty(); }
        } else if (ownerUUID != null) {
            // Find the specific shop that holds this listing
            UUID sId = listingShopMap.get(listingId);
            PlayerShop ps = (sId != null) ? market.getShopById(sId).orElse(null) : null;
            if (ps != null) { ps.removeListing(listingId); market.setDirty(); }
        }

        player.sendSystemMessage(MessageUtils.success("Removed "
                + listing.getItem().getHoverName().getString() + " listing from the shop."));

        populateItems(player);
    }

    private Optional<MarketListing> findListing(UUID listingId, MarketData market) {
        if (guildId != null) {
            GuildShop shop = market.getGuildShop(guildId).orElse(null);
            if (shop != null) return shop.getListing(listingId);
        } else if (ownerUUID != null) {
            for (PlayerShop s : market.getPlayerShops(ownerUUID))
                if (s.getListing(listingId).isPresent()) return s.getListing(listingId);
        }
        return Optional.empty();
    }

    // ── Item factories ────────────────────────────────────────────────────────

    private static ItemStack makeListingItem(MarketListing listing, boolean isOwner, int chestStock) {
        ItemStack display = listing.getItem();
        AuctionBrowseMenu.appendLore(display, AuctionBrowseMenu.lore("────────────────────", ChatFormatting.DARK_GRAY));
        AuctionBrowseMenu.appendLore(display, Component.literal("Price: ")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false))
                .append(Component.literal(CurrencyManager.format(listing.getPriceEach()) + " each")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false))));
        if (chestStock >= 0) {
            // Chest-backed player shop listing — show live stock
            AuctionBrowseMenu.appendLore(display, AuctionBrowseMenu.lore(
                    "In stock: " + chestStock, chestStock > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        } else if (!listing.isUnlimited()) {
            AuctionBrowseMenu.appendLore(display, AuctionBrowseMenu.lore(
                    "Stock: " + listing.getStock(), ChatFormatting.GRAY));
        }
        if (isOwner) {
            AuctionBrowseMenu.appendLore(display, AuctionBrowseMenu.lore(
                    "Right-click to remove listing", ChatFormatting.RED));
        } else {
            AuctionBrowseMenu.appendLore(display, AuctionBrowseMenu.lore(
                    "Left-click to buy", ChatFormatting.GREEN));
        }
        return display;
    }

    private static ItemStack makeButton(net.minecraft.world.item.Item icon, String label, ChatFormatting color) {
        ItemStack item = new ItemStack(icon);
        item.setHoverName(AuctionBrowseMenu.styledName(label, color));
        return item;
    }

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
