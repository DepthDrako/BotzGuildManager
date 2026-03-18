package com.botzguildz.gui;

import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.ItemPickerCache;
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

import java.util.List;
import java.util.UUID;

/**
 * Item picker GUI for the guild bounty board.
 *
 * <pre>
 * Row 0 (0-8):   [Tab 0][Tab 1][Tab 2][Tab 3][Tab 4][Tab 5][Tab 6][Tab 7][Search:8]
 * Row 1 (9-17):  [glass fill — spacer / "no results" notice at 13]
 * Rows 2-4 (18-44): Item tiles (up to 27 per page)
 * Row 5 (45-53): [Back:45][Prev:46][glass][glass][PageInfo:49][glass][glass][Next:52][Close:53]
 * </pre>
 *
 * Tabs show mod namespaces (Minecraft first, then by item count desc).  If there
 * are more than 8 namespaces, extra ones are unreachable via the tab row but can
 * still be found through the search filter.
 *
 * Clicking an item tile closes the picker and opens {@link GuildBountyPostMenu}
 * pre-populated with that item.
 */
public class BountyItemPickerMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    // ── Slot constants ────────────────────────────────────────────────────────

    private static final int SEARCH_BTN  = 8;
    private static final int CAT_PREV    = 9;   // row 1 left  — previous tab page
    private static final int CAT_NEXT    = 17;  // row 1 right — next tab page
    private static final int NOTICE_SLOT = 13;
    private static final int ITEM_FIRST  = 18;
    private static final int ITEM_LAST   = 44;
    private static final int ITEMS_PER_PAGE = ITEM_LAST - ITEM_FIRST + 1; // 27

    private static final int BACK_BTN    = 45;
    private static final int PREV_BTN    = 46;
    private static final int PAGE_INFO   = 49;
    private static final int NEXT_BTN    = 52;
    private static final int CLOSE_BTN  = 53;

    // ── State ─────────────────────────────────────────────────────────────────

    private final UUID   guildId;
    private int          tabIndex;    // selected namespace index (global across all catPages)
    private int          catPage;     // which page of 8 tabs to display (0-based)
    private int          page;        // item page within selected tab (1-based)
    private String       searchQuery; // empty = no filter

    // ── Constructor ───────────────────────────────────────────────────────────

    public BountyItemPickerMenu(int id, Inventory inv, UUID guildId,
                                 int tabIndex, int catPage, int page, String searchQuery) {
        this(id, inv, new SimpleContainer(CHEST_SIZE), guildId, tabIndex, catPage, page, searchQuery);
    }

    private BountyItemPickerMenu(int id, Inventory inv, SimpleContainer chest, UUID guildId,
                                  int tabIndex, int catPage, int page, String searchQuery) {
        super(ModMenuTypes.BOUNTY_ITEM_PICKER_MENU.get(), id, inv, chest, ROWS);
        this.guildId     = guildId;
        this.tabIndex    = tabIndex;
        this.catPage     = Math.max(0, catPage);
        this.page        = Math.max(1, page);
        this.searchQuery = searchQuery == null ? "" : searchQuery;
        // Only populate server-side; broadcastChanges() syncs slots to client automatically.
        // Running ItemPickerCache on the client inside a packet handler causes a silent failure.
        if (inv.player instanceof ServerPlayer) {
            populateItems(inv.player);
        }
    }

    public static BountyItemPickerMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        boolean hasGuild = buf.readBoolean();
        UUID    guildId  = hasGuild ? buf.readUUID() : null;
        int     tabIdx   = buf.readInt();
        int     catPg    = buf.readInt();
        int     pg       = buf.readInt();
        String  query    = buf.readUtf();
        return new BountyItemPickerMenu(id, inv, new SimpleContainer(CHEST_SIZE),
                guildId, tabIdx, catPg, pg, query);
    }

    // ── Population ────────────────────────────────────────────────────────────

    private void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());

        ItemPickerCache cache = ItemPickerCache.get();
        List<String> namespaces = cache.getNamespaces();

        // ── Row 0 — namespace tabs (8 per catPage) + search button ───────────
        int catStart = catPage * 8;
        for (int i = 0; i < 8 && (catStart + i) < namespaces.size(); i++) {
            String ns        = namespaces.get(catStart + i);
            boolean selected = (catStart + i) == tabIndex;
            ItemStack tab    = cache.representativeItem(ns).copy();
            tab.setCount(1);
            ChatFormatting color = selected ? ChatFormatting.GOLD : ChatFormatting.GRAY;
            tab.setHoverName(Component.literal(
                    ItemPickerCache.displayName(ns) + (selected ? "  ◄" : ""))
                    .withStyle(Style.EMPTY.withColor(color).withItalic(false)));
            appendLore(tab, lore(cache.getItemsForNamespace(ns).size() + " items", ChatFormatting.DARK_GRAY));
            if (!selected) appendLore(tab, lore("Click to browse", ChatFormatting.DARK_GRAY));
            chest.setItem(i, tab);
        }

        // Search button (slot 8)
        boolean hasQuery = !searchQuery.isBlank();
        ItemStack searchBtn = new ItemStack(Items.COMPASS);
        if (hasQuery) {
            searchBtn.setHoverName(Component.literal("🔍 " + searchQuery)
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false)));
            appendLore(searchBtn, lore("Click to clear filter", ChatFormatting.GRAY));
        } else {
            searchBtn.setHoverName(Component.literal("Search Items")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withItalic(false)));
            appendLore(searchBtn, lore("Click to type a search query", ChatFormatting.GRAY));
        }
        chest.setItem(SEARCH_BTN, searchBtn);

        // ── Row 1 — category page navigation ─────────────────────────────────
        int totalCatPages = Math.max(1, (int) Math.ceil((double) namespaces.size() / 8.0));
        if (catPage > 0) {
            ItemStack catPrevBtn = new ItemStack(Items.SPECTRAL_ARROW);
            catPrevBtn.setHoverName(styled("◄ Previous Tab Page", ChatFormatting.AQUA));
            appendLore(catPrevBtn, lore("Show earlier mod tabs", ChatFormatting.DARK_GRAY));
            chest.setItem(CAT_PREV, catPrevBtn);
        }
        if (catPage < totalCatPages - 1) {
            ItemStack catNextBtn = new ItemStack(Items.SPECTRAL_ARROW);
            catNextBtn.setHoverName(styled("Next Tab Page ►", ChatFormatting.AQUA));
            appendLore(catNextBtn, lore("Show more mod tabs", ChatFormatting.DARK_GRAY));
            chest.setItem(CAT_NEXT, catNextBtn);
        }

        // ── Resolve which items to show ───────────────────────────────────────
        List<ItemStack> items = hasQuery
                ? cache.search(searchQuery)
                : (tabIndex < namespaces.size() ? cache.getItemsForNamespace(namespaces.get(tabIndex))
                                                 : List.of());

        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));
        page = Math.min(page, totalPages);

        // ── Row 1 spacer / notice ─────────────────────────────────────────────
        if (items.isEmpty()) {
            ItemStack notice = new ItemStack(Items.BARRIER);
            notice.setHoverName(Component.literal("No items found")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withItalic(false)));
            if (hasQuery) appendLore(notice, lore("Try a different search term", ChatFormatting.GRAY));
            chest.setItem(NOTICE_SLOT, notice);
        } else if (hasQuery) {
            ItemStack notice = new ItemStack(Items.PAPER);
            notice.setHoverName(Component.literal("Search: \"" + searchQuery + "\"")
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false)));
            appendLore(notice, lore(items.size() + " result(s)  |  page " + page + "/" + totalPages,
                    ChatFormatting.GRAY));
            chest.setItem(NOTICE_SLOT, notice);
        }

        // ── Rows 2-4 — item tiles ─────────────────────────────────────────────
        int start = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= items.size()) break;
            ItemStack tile = items.get(idx).copy();
            tile.setCount(1);
            // Preserve original hover name; add instruction lore
            appendLore(tile, lore("Click to select for bounty", ChatFormatting.YELLOW));
            chest.setItem(ITEM_FIRST + i, tile);
        }

        // ── Row 5 — navigation ────────────────────────────────────────────────
        // Back
        ItemStack back = new ItemStack(Items.ARROW);
        back.setHoverName(styled("Back", ChatFormatting.WHITE));
        appendLore(back, lore("Return to the bounty board", ChatFormatting.GRAY));
        chest.setItem(BACK_BTN, back);

        // Prev page
        if (page > 1) {
            ItemStack prev = new ItemStack(Items.SPECTRAL_ARROW);
            prev.setHoverName(styled("◄ Previous Page", ChatFormatting.WHITE));
            chest.setItem(PREV_BTN, prev);
        }

        // Page info
        ItemStack info = new ItemStack(Items.OAK_SIGN);
        info.setHoverName(styled("Page " + page + " / " + totalPages, ChatFormatting.GOLD));
        appendLore(info, lore(items.size() + " items total", ChatFormatting.GRAY));
        chest.setItem(PAGE_INFO, info);

        // Next page
        if (page < totalPages) {
            ItemStack next = new ItemStack(Items.SPECTRAL_ARROW);
            next.setHoverName(styled("Next Page ►", ChatFormatting.WHITE));
            chest.setItem(NEXT_BTN, next);
        }

        // Close
        ItemStack close = new ItemStack(Items.BARRIER);
        close.setHoverName(styled("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_BTN, close);

        broadcastChanges();
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= CHEST_SIZE) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (!(player instanceof ServerPlayer sp)) return;
        handleClick(slotId, sp);
    }

    private void handleClick(int slotId, ServerPlayer player) {
        ItemPickerCache cache = ItemPickerCache.get();
        List<String> namespaces = cache.getNamespaces();

        // ── Tab clicks (slots 0-7) ────────────────────────────────────────────
        if (slotId < 8) {
            int nsIndex = catPage * 8 + slotId;
            if (nsIndex < namespaces.size()) {
                tabIndex    = nsIndex;
                page        = 1;
                searchQuery = "";
                populateItems(player);
            }
            return;
        }

        // ── Category page navigation (row 1) ─────────────────────────────────
        if (slotId == CAT_PREV && catPage > 0) { catPage--; populateItems(player); return; }
        if (slotId == CAT_NEXT) {
            int totalCatPages = (int) Math.ceil((double) namespaces.size() / 8.0);
            if (catPage < totalCatPages - 1) { catPage++; populateItems(player); }
            return;
        }

        // ── Search button (slot 8) ────────────────────────────────────────────
        if (slotId == SEARCH_BTN) {
            if (!searchQuery.isBlank()) {
                // Clear filter
                searchQuery = "";
                populateItems(player);
            } else {
                // Open search input (AnvilMenu subclass)
                UUID  gId   = guildId;
                int   tIdx  = tabIndex;
                int   catPg = catPage;
                int   pg    = page;
                player.closeContainer();
                MinecraftServer server = player.getServer();
                if (server != null) {
                    server.execute(() -> NetworkHooks.openScreen(player,
                            new MenuProvider() {
                                @Override public Component getDisplayName() {
                                    return Component.literal("Search Items");
                                }
                                @Override public AbstractContainerMenu
                                createMenu(int id, Inventory inv, Player p) {
                                    return new ItemSearchMenu(id, inv, gId, tIdx, catPg, pg);
                                }
                            },
                            buf -> {
                                buf.writeBoolean(gId != null);
                                if (gId != null) buf.writeUUID(gId);
                                buf.writeInt(tIdx);
                                buf.writeInt(catPg);
                                buf.writeInt(pg);
                            }
                    ));
                }
            }
            return;
        }

        // ── Item tiles (slots 18-44) ──────────────────────────────────────────
        if (slotId >= ITEM_FIRST && slotId <= ITEM_LAST) {
            List<ItemStack> items = searchQuery.isBlank()
                    ? (tabIndex < namespaces.size()
                            ? cache.getItemsForNamespace(namespaces.get(tabIndex)) : List.of())
                    : cache.search(searchQuery);

            int idx = (page - 1) * ITEMS_PER_PAGE + (slotId - ITEM_FIRST);
            if (idx >= items.size()) return;

            ItemStack selected = items.get(idx).copy();
            selected.setCount(1);
            UUID  gId = guildId;

            player.closeContainer();
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.execute(() -> NetworkHooks.openScreen(player,
                        new MenuProvider() {
                            @Override public Component getDisplayName() {
                                return Component.literal("Post Bounty");
                            }
                            @Override public AbstractContainerMenu
                            createMenu(int id, Inventory inv, Player p) {
                                return new GuildBountyPostMenu(id, inv, gId, selected);
                            }
                        },
                        buf -> {
                            buf.writeBoolean(gId != null);
                            if (gId != null) buf.writeUUID(gId);
                            buf.writeItem(selected);
                        }
                ));
            }
            return;
        }

        // ── Navigation ────────────────────────────────────────────────────────
        if (slotId == PREV_BTN && page > 1)    { page--; populateItems(player); return; }
        if (slotId == NEXT_BTN)                { page++; populateItems(player); return; }
        if (slotId == CLOSE_BTN)               { player.closeContainer(); return; }

        if (slotId == BACK_BTN) {
            UUID gId = guildId;
            player.closeContainer();
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.execute(() -> NetworkHooks.openScreen(player,
                        new MenuProvider() {
                            @Override public Component getDisplayName() {
                                return Component.literal("Guild Bounty Board");
                            }
                            @Override public AbstractContainerMenu
                            createMenu(int id, Inventory inv, Player p) {
                                return new GuildBountyBoardMenu(id, inv, gId, 1);
                            }
                        },
                        buf -> {
                            buf.writeBoolean(gId != null);
                            if (gId != null) buf.writeUUID(gId);
                            buf.writeInt(1);
                        }
                ));
            }
        }
    }

    // ── Required overrides ────────────────────────────────────────────────────

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    // ── GUI helpers ───────────────────────────────────────────────────────────

    private static ItemStack pane() {
        ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        s.setHoverName(Component.literal(" ").withStyle(Style.EMPTY.withItalic(false)));
        return s;
    }

    private static Component styled(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    private static Component lore(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    private static void appendLore(ItemStack stack, Component line) {
        net.minecraft.nbt.CompoundTag display = stack.getOrCreateTagElement("display");
        net.minecraft.nbt.ListTag lore;
        if (display.contains("Lore", 9)) {
            lore = display.getList("Lore", 8);
        } else {
            lore = new net.minecraft.nbt.ListTag();
            display.put("Lore", lore);
        }
        lore.add(net.minecraft.nbt.StringTag.valueOf(
                net.minecraft.network.chat.Component.Serializer.toJson(line)));
    }
}
