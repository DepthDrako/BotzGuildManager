package com.botzguildz.gui;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildBountyData;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.RankPermission;
import com.botzguildz.market.GuildBountyEntry;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
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
 * Guild bounty board — shows all active item-collection bounties for the player's guild.
 *
 * <pre>
 * Row 0 (0-8):   [glass×4][TITLE:4][glass×4]
 * Rows 1-4 (9-44): Bounty tiles (up to 36 per page)
 * Row 5 (45-53): [PostBounty:45][Prev:46][glass][glass][PageInfo:49][glass][glass][Next:52][Close:53]
 * </pre>
 *
 * Bounty tile lore shows: item needed, qty, reward, poster, and whether the player
 * has enough items to claim.  Clicking the tile attempts to claim instantly.
 *
 * The "Post Bounty" button is only shown to members with {@link RankPermission#MANAGE_BOUNTIES}.
 */
public class GuildBountyBoardMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    // ── Slot constants ────────────────────────────────────────────────────────

    private static final int TITLE_SLOT   = 4;
    private static final int ENTRY_FIRST  = 9;
    private static final int ENTRY_LAST   = 44;
    private static final int ENTRIES_PER_PAGE = ENTRY_LAST - ENTRY_FIRST + 1; // 36

    private static final int POST_BTN  = 45;
    private static final int PREV_BTN  = 46;
    private static final int PAGE_INFO = 49;
    private static final int NEXT_BTN  = 52;
    private static final int CLOSE_BTN = 53;

    // ── State ─────────────────────────────────────────────────────────────────

    private final UUID guildId;
    private int page; // 1-based

    // ── Constructor ───────────────────────────────────────────────────────────

    public GuildBountyBoardMenu(int id, Inventory inv, UUID guildId, int page) {
        this(id, inv, new SimpleContainer(CHEST_SIZE), guildId, page);
    }

    private GuildBountyBoardMenu(int id, Inventory inv, SimpleContainer chest,
                                  UUID guildId, int page) {
        super(ModMenuTypes.GUILD_BOUNTY_BOARD_MENU.get(), id, inv, chest, ROWS);
        this.guildId = guildId;
        this.page    = Math.max(1, page);
        if (inv.player instanceof ServerPlayer) {
            populateItems(inv.player);
        }
    }

    public static GuildBountyBoardMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        boolean hasGuild = buf.readBoolean();
        UUID    guildId  = hasGuild ? buf.readUUID() : null;
        int     pg       = buf.readInt();
        return new GuildBountyBoardMenu(id, inv, new SimpleContainer(CHEST_SIZE), guildId, pg);
    }

    // ── Population ────────────────────────────────────────────────────────────

    private void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());

        // ── Title ─────────────────────────────────────────────────────────────
        ItemStack title = new ItemStack(Items.WRITABLE_BOOK);
        title.setHoverName(styled("Guild Bounty Board", ChatFormatting.GOLD));
        appendLore(title, lore("Post item-collection bounties to reward guild members", ChatFormatting.GRAY));
        appendLore(title, lore("Click a bounty to claim it (requires items in inventory)", ChatFormatting.DARK_GRAY));
        chest.setItem(TITLE_SLOT, title);

        // ── Bounty entries ────────────────────────────────────────────────────
        List<GuildBountyEntry> entries = guildId != null && player instanceof ServerPlayer sp
                ? GuildBountyData.get(sp.getServer()).getBountiesForGuild(guildId)
                : List.of();

        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ENTRIES_PER_PAGE));
        page = Math.min(page, totalPages);

        if (entries.isEmpty()) {
            ItemStack empty = new ItemStack(Items.BARRIER);
            empty.setHoverName(styled("No Active Bounties", ChatFormatting.GRAY));
            appendLore(empty, lore("Post one with the button below (requires permission)", ChatFormatting.DARK_GRAY));
            chest.setItem(27, empty); // centre of visible rows
        } else {
            int start = (page - 1) * ENTRIES_PER_PAGE;
            for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
                int idx = start + i;
                if (idx >= entries.size()) break;
                GuildBountyEntry e = entries.get(idx);
                chest.setItem(ENTRY_FIRST + i, makeBountyTile(e, player));
            }
        }

        // ── Row 5 — navigation ────────────────────────────────────────────────

        // Post button — only shown to members with MANAGE_BOUNTIES
        boolean canPost = player instanceof ServerPlayer sp2
                && guildId != null
                && GuildUtils.getGuildOf(sp2) != null
                && GuildUtils.getGuildOf(sp2).hasPermission(sp2.getUUID(), RankPermission.MANAGE_BOUNTIES);
        if (canPost) {
            ItemStack post = new ItemStack(Items.WRITABLE_BOOK);
            post.setHoverName(styled("Post New Bounty", ChatFormatting.GREEN));
            appendLore(post, lore("Opens the item picker to select a target item", ChatFormatting.GRAY));
            chest.setItem(POST_BTN, post);
        } else {
            ItemStack lock = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            lock.setHoverName(styled("Post Bounty", ChatFormatting.DARK_GRAY));
            appendLore(lock, lore("Requires MANAGE_BOUNTIES permission", ChatFormatting.DARK_GRAY));
            chest.setItem(POST_BTN, lock);
        }

        if (page > 1) {
            ItemStack prev = new ItemStack(Items.SPECTRAL_ARROW);
            prev.setHoverName(styled("◄ Previous Page", ChatFormatting.WHITE));
            chest.setItem(PREV_BTN, prev);
        }

        ItemStack info = new ItemStack(Items.OAK_SIGN);
        info.setHoverName(styled("Page " + page + " / " + totalPages, ChatFormatting.GOLD));
        appendLore(info, lore(entries.size() + " active bounties", ChatFormatting.GRAY));
        chest.setItem(PAGE_INFO, info);

        if (page < totalPages) {
            ItemStack next = new ItemStack(Items.SPECTRAL_ARROW);
            next.setHoverName(styled("Next Page ►", ChatFormatting.WHITE));
            chest.setItem(NEXT_BTN, next);
        }

        ItemStack close = new ItemStack(Items.BARRIER);
        close.setHoverName(styled("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_BTN, close);

        broadcastChanges();
    }

    // ── Tile builder ──────────────────────────────────────────────────────────

    private ItemStack makeBountyTile(GuildBountyEntry entry, Player player) {
        ItemStack tile = entry.getTargetItem().copy();
        tile.setCount(1);
        tile.setHoverName(styled(
                entry.getQuantityRequired() + "x " + entry.getTargetItem().getHoverName().getString(),
                ChatFormatting.WHITE));

        appendLore(tile, lore("Reward: " + CurrencyManager.format(entry.getRewardAmount()), ChatFormatting.GOLD));
        appendLore(tile, lore("Posted by: " + entry.getPosterName(), ChatFormatting.GRAY));

        // Check if the player has enough items
        int found = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (entry.matchesItem(s)) found += s.getCount();
        }
        int needed = entry.getQuantityRequired();
        if (found >= needed) {
            appendLore(tile, lore("✔  You have " + found + "/" + needed + "  — Click to claim!",
                    ChatFormatting.GREEN));
        } else {
            appendLore(tile, lore("✘  You have " + found + "/" + needed + " (need "
                    + (needed - found) + " more)", ChatFormatting.RED));
        }

        // Officers with MANAGE_BOUNTIES can see a cancel hint
        if (player instanceof ServerPlayer sp) {
            Guild guild = GuildUtils.getGuildOf(sp);
            if (guild != null && guild.hasPermission(sp.getUUID(), RankPermission.MANAGE_BOUNTIES)) {
                appendLore(tile, lore("[Shift-click to cancel + refund]", ChatFormatting.DARK_GRAY));
            }
        }

        return tile;
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < CHEST_SIZE) {
            if (!(player instanceof ServerPlayer sp)) return;
            handleClick(slotId, clickType, sp);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void handleClick(int slotId, ClickType clickType, ServerPlayer player) {

        // ── Navigation buttons ────────────────────────────────────────────────
        if (slotId == CLOSE_BTN) { player.closeContainer(); return; }
        if (slotId == PREV_BTN && page > 1) { page--; populateItems(player); return; }
        if (slotId == NEXT_BTN)             { page++; populateItems(player); return; }

        // ── Post bounty button ────────────────────────────────────────────────
        if (slotId == POST_BTN) {
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null || !guild.hasPermission(player.getUUID(), RankPermission.MANAGE_BOUNTIES)) {
                player.sendSystemMessage(MessageUtils.error("You don't have permission to post bounties."));
                return;
            }
            UUID gId = guildId;
            player.closeContainer();
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.execute(() -> NetworkHooks.openScreen(player,
                        new MenuProvider() {
                            @Override public Component getDisplayName() { return Component.literal("Select Item"); }
                            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                                return new BountyItemPickerMenu(id, inv, gId, 0, 0, 1, "");
                            }
                        },
                        buf -> {
                            buf.writeBoolean(gId != null);
                            if (gId != null) buf.writeUUID(gId);
                            buf.writeInt(0); // tabIndex
                            buf.writeInt(0); // catPage
                            buf.writeInt(1); // page
                            buf.writeUtf("");
                        }
                ));
            }
            return;
        }

        // ── Bounty entry clicks (rows 1-4) ────────────────────────────────────
        if (slotId >= ENTRY_FIRST && slotId <= ENTRY_LAST) {
            MinecraftServer server = player.getServer();
            if (server == null || guildId == null) return;

            List<GuildBountyEntry> entries = GuildBountyData.get(server).getBountiesForGuild(guildId);
            int idx = (page - 1) * ENTRIES_PER_PAGE + (slotId - ENTRY_FIRST);
            if (idx >= entries.size()) return;

            GuildBountyEntry entry = entries.get(idx);
            Guild guild = GuildUtils.getGuildOf(player);

            // Shift-click = cancel (if has MANAGE_BOUNTIES permission)
            if (clickType == ClickType.QUICK_MOVE
                    && guild != null
                    && guild.hasPermission(player.getUUID(), RankPermission.MANAGE_BOUNTIES)) {
                long refunded = GuildBountyData.get(server).cancelBounty(
                        entry.getEntryId(), guildId, guild);
                if (refunded >= 0) {
                    GuildSavedData.get(server).setDirty();
                    guild.addLog(player.getName().getString() + " cancelled bounty for "
                            + entry.getQuantityRequired() + "x "
                            + entry.getTargetItem().getHoverName().getString()
                            + " (refunded " + CurrencyManager.format(refunded) + ").");
                    player.sendSystemMessage(MessageUtils.success("Bounty cancelled. "
                            + CurrencyManager.format(refunded) + " refunded to guild bank."));
                    populateItems(player);
                }
                return;
            }

            // Normal click = attempt to claim
            boolean claimed = GuildBountyData.get(server).claimBounty(
                    entry.getEntryId(), guildId, player, server);
            if (claimed) {
                player.sendSystemMessage(MessageUtils.success(
                        "Bounty claimed! +" + CurrencyManager.format(entry.getRewardAmount())
                                + " added to your wallet."));
                populateItems(player);
            } else {
                player.sendSystemMessage(MessageUtils.error(
                        "You don't have enough " + entry.getTargetItem().getHoverName().getString()
                                + ". Need " + entry.getQuantityRequired() + "."));
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
