package com.botzguildz.gui;

import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.MarketData;
import com.botzguildz.gui.ShopViewMenu;
import com.botzguildz.market.GuildShop;
import com.botzguildz.market.PlayerShop;
import com.botzguildz.registry.ModMenuTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shop directory — lists all guild or all player shops.
 *
 * <pre>
 * Row 0 (0-8):  glass fill (title)
 * Rows 1-4 (9-44): Shop entries (up to 36 per page)
 * Row 5 (45-53): [Close:45][Prev:46][glass][glass][Info:49][glass][glass][Next:52][Close:53]
 * </pre>
 */
public class ShopListMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    private static final int SHOP_START = 9;
    private static final int SHOP_END   = 44;
    private static final int CLOSE_L    = 45;
    private static final int PREV_SLOT  = 46;
    private static final int INFO_SLOT  = 49;
    private static final int NEXT_SLOT  = 52;
    private static final int CLOSE_R    = 53;

    private final boolean isGuild;
    private int page;

    // Currently displayed shop IDs (guild UUID or player UUID)
    private final List<UUID> displayedIds = new ArrayList<>();

    private ShopListMenu(int id, Inventory playerInv, boolean isGuild, int page, SimpleContainer chest) {
        super(ModMenuTypes.SHOP_LIST_MENU.get(), id, playerInv, chest, ROWS);
        this.isGuild = isGuild;
        this.page    = Math.max(1, page);
        populateItems(playerInv.player);
    }

    public ShopListMenu(int id, Inventory playerInv, boolean isGuild, int page) {
        this(id, playerInv, isGuild, page, new SimpleContainer(CHEST_SIZE));
    }

    public static ShopListMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        boolean isGuild = buf.readBoolean();
        int page = buf.readInt();
        return new ShopListMenu(id, playerInv, isGuild, page, new SimpleContainer(CHEST_SIZE));
    }

    public void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) this.getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, AuctionBrowseMenu.pane());
        displayedIds.clear();

        MinecraftServer server = (player instanceof ServerPlayer sp) ? sp.getServer() : null;

        int pageSize = SHOP_END - SHOP_START + 1; // 36
        int totalPages = 1;

        if (server != null) {
            MarketData market = MarketData.get(server);

            if (isGuild) {
                List<GuildShop> shops = new ArrayList<>(market.getAllGuildShops());
                shops.removeIf(s -> !s.isOpen() || s.getListings().isEmpty());
                totalPages = Math.max(1, (int) Math.ceil(shops.size() / (double) pageSize));
                page = Math.min(page, totalPages);
                int startIdx = (page - 1) * pageSize;
                for (int i = 0; i < pageSize; i++) {
                    int idx = startIdx + i;
                    if (idx >= shops.size()) break;
                    GuildShop shop = shops.get(idx);
                    chest.setItem(SHOP_START + i, makeGuildShopItem(shop));
                    displayedIds.add(shop.getGuildId());
                }
            } else {
                // One entry per PLAYER (not per individual shop) — clicking opens
                // a combined view of all that player's shops.
                // Collect ownerUUID → (ownerName, totalListings, shopCount)
                record PlayerEntry(UUID ownerUUID, String ownerName, int totalListings, int shopCount) {}
                java.util.LinkedHashMap<UUID, PlayerEntry> seen = new java.util.LinkedHashMap<>();
                for (PlayerShop shop : market.getAllPlayerShops()) {
                    if (!shop.isOpen() || shop.getListings().isEmpty()) continue;
                    seen.merge(shop.getOwnerUUID(),
                            new PlayerEntry(shop.getOwnerUUID(), shop.getOwnerName(),
                                    shop.getListings().size(), 1),
                            (a, b) -> new PlayerEntry(a.ownerUUID(), a.ownerName(),
                                    a.totalListings() + b.totalListings(), a.shopCount() + 1));
                }
                List<PlayerEntry> entries = new ArrayList<>(seen.values());
                totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
                page = Math.min(page, totalPages);
                int startIdx = (page - 1) * pageSize;
                for (int i = 0; i < pageSize; i++) {
                    int idx = startIdx + i;
                    if (idx >= entries.size()) break;
                    PlayerEntry e = entries.get(idx);
                    chest.setItem(SHOP_START + i, makePlayerEntryItem(e.ownerName(),
                            e.totalListings(), e.shopCount()));
                    displayedIds.add(e.ownerUUID());
                }
            }
        }

        // Navigation bar
        ItemStack close = new ItemStack(Items.BARRIER);
        close.setHoverName(AuctionBrowseMenu.styledName("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_L, close);
        chest.setItem(CLOSE_R, close.copy());

        if (page > 1) {
            chest.setItem(PREV_SLOT, makeButton(Items.ARROW,
                    "◄ Previous Page (" + (page-1) + "/" + totalPages + ")", ChatFormatting.YELLOW));
        }
        if (page < totalPages) {
            chest.setItem(NEXT_SLOT, makeButton(Items.ARROW,
                    "Next Page ► (" + (page+1) + "/" + totalPages + ")", ChatFormatting.YELLOW));
        }

        int finalTotalPages = totalPages;
        ItemStack info = new ItemStack(Items.PAPER);
        info.setHoverName(AuctionBrowseMenu.styledName(
                isGuild ? "All Guild Shops" : "All Player Shops", ChatFormatting.GOLD));
        AuctionBrowseMenu.appendLore(info, AuctionBrowseMenu.lore(
                "Page " + page + "/" + finalTotalPages, ChatFormatting.GRAY));
        chest.setItem(INFO_SLOT, info);

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
        if (slotId == CLOSE_L || slotId == CLOSE_R) { player.closeContainer(); return; }
        if (slotId == PREV_SLOT && page > 1) { page--; populateItems(player); return; }
        if (slotId == NEXT_SLOT) { page++; populateItems(player); return; }

        if (slotId >= SHOP_START && slotId <= SHOP_END) {
            int idx = slotId - SHOP_START;
            if (idx < displayedIds.size()) {
                UUID targetId = displayedIds.get(idx);
                openShopView(player, targetId);
            }
        }
    }

    private void openShopView(ServerPlayer player, UUID targetId) {
        final boolean guild = isGuild;
        NetworkHooks.openScreen(player,
                new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.literal(guild ? "Guild Shop" : "Player Shop");
                    }
                    @Override
                    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                        return guild
                                ? new ShopViewMenu(id, inv, targetId, null, false, 1)
                                : new ShopViewMenu(id, inv, null, targetId, player.getUUID().equals(targetId), 1);
                    }
                },
                buf -> {
                    buf.writeBoolean(guild);
                    buf.writeUUID(targetId);
                    buf.writeBoolean(!guild && player.getUUID().equals(targetId));
                    buf.writeInt(1);
                    buf.writeBoolean(false);  // no specific shopId — show all for this player
                }
        );
    }

    // ── Item factories ────────────────────────────────────────────────────────

    private static ItemStack makeGuildShopItem(GuildShop shop) {
        ItemStack item = new ItemStack(Items.CHEST);
        item.setHoverName(Component.literal(shop.getGuildName())
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false)));
        AuctionBrowseMenu.appendLore(item, AuctionBrowseMenu.lore(
                shop.getListings().size() + " listing(s)", ChatFormatting.GRAY));
        AuctionBrowseMenu.appendLore(item, AuctionBrowseMenu.lore("Click to browse", ChatFormatting.GREEN));
        return item;
    }

    private static ItemStack makePlayerEntryItem(String ownerName, int totalListings, int shopCount) {
        ItemStack item = new ItemStack(Items.CHEST);
        item.setHoverName(Component.literal(ownerName + "'s Shop")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false)));
        AuctionBrowseMenu.appendLore(item, AuctionBrowseMenu.lore(
                totalListings + " listing(s) across " + shopCount + " location(s)", ChatFormatting.GRAY));
        AuctionBrowseMenu.appendLore(item, AuctionBrowseMenu.lore("Click to browse all", ChatFormatting.GREEN));
        return item;
    }

    private static ItemStack makeButton(net.minecraft.world.item.Item icon, String label, ChatFormatting color) {
        ItemStack item = new ItemStack(icon);
        item.setHoverName(AuctionBrowseMenu.styledName(label, color));
        return item;
    }

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
