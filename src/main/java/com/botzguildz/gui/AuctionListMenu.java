package com.botzguildz.gui;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.AuctionHouseData;
import com.botzguildz.market.AuctionListing;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.MessageUtils;
import net.minecraft.ChatFormatting;
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

/**
 * Create-listing screen.
 *
 * <pre>
 * Row 0 (0-8):  glass ... [ITEM_DISPLAY:4] ... glass
 * Row 1 (9-17): glass ... [TYPE_TOGGLE:13] ... glass
 * Row 2 (18-26): [-1000][-100][-10][-1][PRICE:22][+1][+10][+100][+1000]
 * Row 3 (27-35): glass [DUR-:30][DUR_DISPLAY:31][DUR+:32] glass   ← timed only
 * Row 4 (36-44): glass [CANCEL:38] glass glass glass [CONFIRM:42] glass
 * Row 5 (45-53): glass fill
 * </pre>
 */
public class AuctionListMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    private static final int ITEM_DISPLAY  = 4;
    private static final int TYPE_TOGGLE   = 13;

    // Price row (row 2)
    private static final int BTN_MINUS_1000 = 18;
    private static final int BTN_MINUS_100  = 19;
    private static final int BTN_MINUS_10   = 20;
    private static final int BTN_MINUS_1    = 21;
    private static final int PRICE_DISPLAY  = 22;
    private static final int BTN_PLUS_1     = 23;
    private static final int BTN_PLUS_10    = 24;
    private static final int BTN_PLUS_100   = 25;
    private static final int BTN_PLUS_1000  = 26;

    // Duration row (row 3, timed only)
    private static final int DUR_MINUS   = 30;
    private static final int DUR_DISPLAY = 31;
    private static final int DUR_PLUS    = 32;

    // Action row (row 4)
    private static final int CANCEL_BTN  = 38;
    private static final int CONFIRM_BTN = 42;

    private long price;
    private AuctionListing.Type listingType;
    private long durationMinutes;
    private ItemStack itemToList;

    private AuctionListMenu(int id, Inventory playerInv, long price,
                             AuctionListing.Type type, long durationMinutes, SimpleContainer chest) {
        super(ModMenuTypes.AUCTION_LIST_MENU.get(), id, playerInv, chest, ROWS);
        this.price           = Math.max(1, price);
        this.listingType     = type;
        this.durationMinutes = Math.max(1, durationMinutes == 0 ? 60 : durationMinutes);
        // Capture item from hand (server side); client gets null — OK because server re-sends
        if (playerInv.player instanceof ServerPlayer) {
            this.itemToList = playerInv.player.getMainHandItem().copy();
        } else {
            this.itemToList = ItemStack.EMPTY;
        }
        populateItems(playerInv.player);
    }

    public AuctionListMenu(int id, Inventory playerInv, long price,
                           AuctionListing.Type type, long durationMinutes) {
        this(id, playerInv, price, type, durationMinutes, new SimpleContainer(CHEST_SIZE));
    }

    public static AuctionListMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        long price     = buf.readLong();
        boolean timed  = buf.readBoolean();
        long duration  = buf.readLong();
        return new AuctionListMenu(id, playerInv, price,
                timed ? AuctionListing.Type.TIMED : AuctionListing.Type.FIXED,
                duration, new SimpleContainer(CHEST_SIZE));
    }

    public void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) this.getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, AuctionBrowseMenu.pane());

        // Item display
        ItemStack displayItem = itemToList.isEmpty() ? new ItemStack(Items.PAPER) : itemToList.copy();
        if (itemToList.isEmpty()) displayItem.setHoverName(AuctionBrowseMenu.styledName("Hold item and reopen", ChatFormatting.RED));
        chest.setItem(ITEM_DISPLAY, displayItem);

        // Type toggle
        ItemStack toggle = new ItemStack(listingType == AuctionListing.Type.FIXED ? Items.GOLD_INGOT : Items.CLOCK);
        toggle.setHoverName(AuctionBrowseMenu.styledName(
                "Type: " + (listingType == AuctionListing.Type.FIXED ? "Fixed Price" : "Timed Auction"),
                ChatFormatting.AQUA));
        AuctionBrowseMenu.appendLore(toggle, AuctionBrowseMenu.lore("Click to toggle", ChatFormatting.GRAY));
        chest.setItem(TYPE_TOGGLE, toggle);

        // Price row
        chest.setItem(BTN_MINUS_1000, priceBtn("-1000", ChatFormatting.RED));
        chest.setItem(BTN_MINUS_100,  priceBtn("-100",  ChatFormatting.RED));
        chest.setItem(BTN_MINUS_10,   priceBtn("-10",   ChatFormatting.RED));
        chest.setItem(BTN_MINUS_1,    priceBtn("-1",    ChatFormatting.RED));

        ItemStack priceDisplay = new ItemStack(Items.GOLD_NUGGET);
        priceDisplay.setHoverName(AuctionBrowseMenu.styledName(
                (listingType == AuctionListing.Type.FIXED ? "Price: " : "Starting Bid: ")
                        + CurrencyManager.formatShort(price), ChatFormatting.YELLOW));
        chest.setItem(PRICE_DISPLAY, priceDisplay);

        chest.setItem(BTN_PLUS_1,    priceBtn("+1",    ChatFormatting.GREEN));
        chest.setItem(BTN_PLUS_10,   priceBtn("+10",   ChatFormatting.GREEN));
        chest.setItem(BTN_PLUS_100,  priceBtn("+100",  ChatFormatting.GREEN));
        chest.setItem(BTN_PLUS_1000, priceBtn("+1000", ChatFormatting.GREEN));

        // Duration row (timed only)
        if (listingType == AuctionListing.Type.TIMED) {
            chest.setItem(DUR_MINUS, priceBtn("-60m", ChatFormatting.RED));
            ItemStack durDisplay = new ItemStack(Items.CLOCK);
            durDisplay.setHoverName(AuctionBrowseMenu.styledName("Duration: " + formatDuration(durationMinutes), ChatFormatting.AQUA));
            chest.setItem(DUR_DISPLAY, durDisplay);
            chest.setItem(DUR_PLUS, priceBtn("+60m", ChatFormatting.GREEN));
        }

        // Action row
        ItemStack cancel = new ItemStack(Items.BARRIER);
        cancel.setHoverName(AuctionBrowseMenu.styledName("Cancel", ChatFormatting.RED));
        chest.setItem(CANCEL_BTN, cancel);

        ItemStack confirm = new ItemStack(Items.EMERALD);
        confirm.setHoverName(AuctionBrowseMenu.styledName("Confirm Listing", ChatFormatting.GREEN));
        AuctionBrowseMenu.appendLore(confirm, AuctionBrowseMenu.lore(
                "Item will be taken from your hand", ChatFormatting.GRAY));
        chest.setItem(CONFIRM_BTN, confirm);

        broadcastChanges();
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < CHEST_SIZE) {
            if (!(player instanceof ServerPlayer sp)) return;
            handleChestClick(slotId, sp);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void handleChestClick(int slotId, ServerPlayer player) {
        switch (slotId) {
            case BTN_MINUS_1000 -> { price = Math.max(1, price - 1000); populateItems(player); }
            case BTN_MINUS_100  -> { price = Math.max(1, price - 100);  populateItems(player); }
            case BTN_MINUS_10   -> { price = Math.max(1, price - 10);   populateItems(player); }
            case BTN_MINUS_1    -> { price = Math.max(1, price - 1);    populateItems(player); }
            case BTN_PLUS_1     -> { price++;    populateItems(player); }
            case BTN_PLUS_10    -> { price += 10;    populateItems(player); }
            case BTN_PLUS_100   -> { price += 100;   populateItems(player); }
            case BTN_PLUS_1000  -> { price += 1000;  populateItems(player); }
            case TYPE_TOGGLE -> {
                listingType = (listingType == AuctionListing.Type.FIXED)
                        ? AuctionListing.Type.TIMED : AuctionListing.Type.FIXED;
                populateItems(player);
            }
            case DUR_MINUS -> {
                durationMinutes = Math.max(1, durationMinutes - 60);
                populateItems(player);
            }
            case DUR_PLUS -> {
                long maxMins = GuildConfig.AH_MAX_DURATION_MINUTES.get();
                durationMinutes = Math.min(maxMins, durationMinutes + 60);
                populateItems(player);
            }
            case CANCEL_BTN -> player.closeContainer();
            case CONFIRM_BTN -> confirmListing(player);
        }
    }

    private void confirmListing(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(MessageUtils.error("You must be holding an item to list."));
            return;
        }

        // Check max listings
        AuctionHouseData ahData = AuctionHouseData.get(server);
        int maxListings = GuildConfig.AH_MAX_LISTINGS_PER_PLAYER.get();
        if (ahData.getListingsBySeller(player.getUUID()).size() >= maxListings) {
            player.sendSystemMessage(MessageUtils.error("You have reached the maximum of " + maxListings + " listings."));
            return;
        }

        // Validate timed duration
        if (listingType == AuctionListing.Type.TIMED) {
            int maxMins = GuildConfig.AH_MAX_DURATION_MINUTES.get();
            if (durationMinutes < 1 || durationMinutes > maxMins) {
                player.sendSystemMessage(MessageUtils.error("Duration must be 1–" + maxMins + " minutes."));
                return;
            }
        }

        ItemStack toList = held.copy();
        player.getMainHandItem().setCount(0);

        AuctionListing listing;
        if (listingType == AuctionListing.Type.FIXED) {
            listing = new AuctionListing(player.getUUID(), toList, price);
        } else {
            listing = new AuctionListing(player.getUUID(), toList, price, durationMinutes * 60_000L);
        }
        ahData.addListing(listing);

        player.sendSystemMessage(MessageUtils.success(
                (listingType == AuctionListing.Type.FIXED ? "Fixed-price listing" : "Timed auction")
                        + " created for " + toList.getHoverName().getString()
                        + " at " + CurrencyManager.format(price) + "!"));
        player.closeContainer();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemStack priceBtn(String label, ChatFormatting color) {
        ItemStack item = new ItemStack(color == ChatFormatting.RED ? Items.RED_DYE : Items.LIME_DYE);
        item.setHoverName(Component.literal(label)
                .withStyle(Style.EMPTY.withColor(color).withBold(true).withItalic(false)));
        return item;
    }

    private static String formatDuration(long minutes) {
        if (minutes < 60) return minutes + "m";
        long h = minutes / 60; long m = minutes % 60;
        if (m == 0) return h + "h";
        return h + "h " + m + "m";
    }

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
