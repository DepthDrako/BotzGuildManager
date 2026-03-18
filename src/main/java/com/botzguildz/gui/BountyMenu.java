package com.botzguildz.gui;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.BountyData;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.market.BountyEntry;
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

import java.util.*;

/**
 * Bounty Browse GUI — 6-row chest.
 *
 * <pre>
 * Row 0 (0-8):  glass fill (title info)
 * Rows 1-4 (9-44): Bounty entries (skull of target, total + placers in lore)
 * Row 5 (45-53): [PlaceBounty:45][Prev:46][glass][glass][Info:49][glass][glass][Next:52][Close:53]
 * </pre>
 */
public class BountyMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    private static final int BOUNTY_START  = 9;
    private static final int BOUNTY_END    = 44;   // 36 slots
    private static final int PLACE_BTN    = 45;
    private static final int PREV_SLOT    = 46;
    private static final int INFO_SLOT    = 49;
    private static final int NEXT_SLOT    = 52;
    private static final int CLOSE_BTN   = 53;

    private final UUID filterTarget; // null = show all
    private int page;

    private BountyMenu(int id, Inventory playerInv, UUID filterTarget, int page, SimpleContainer chest) {
        super(ModMenuTypes.BOUNTY_MENU.get(), id, playerInv, chest, ROWS);
        this.filterTarget = filterTarget;
        this.page         = Math.max(1, page);
        populateItems(playerInv.player);
    }

    public BountyMenu(int id, Inventory playerInv, UUID filterTarget, int page) {
        this(id, playerInv, filterTarget, page, new SimpleContainer(CHEST_SIZE));
    }

    public static BountyMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        boolean hasFilter = buf.readBoolean();
        UUID filter = hasFilter ? buf.readUUID() : null;
        int page = buf.readInt();
        return new BountyMenu(id, playerInv, filter, page, new SimpleContainer(CHEST_SIZE));
    }

    public void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) this.getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, AuctionBrowseMenu.pane());

        MinecraftServer server = (player instanceof ServerPlayer sp) ? sp.getServer() : null;

        List<Map.Entry<UUID, Long>> entries = new ArrayList<>();
        if (server != null) {
            BountyData data = BountyData.get(server);
            if (filterTarget != null) {
                long total = data.getTotalBounty(filterTarget);
                if (total > 0) entries.add(Map.entry(filterTarget, total));
            } else {
                entries = data.getTopBounties(1000);
            }
        }

        int pageSize = BOUNTY_END - BOUNTY_START + 1; // 36
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        page = Math.min(page, totalPages);

        int startIdx = (page - 1) * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int idx = startIdx + i;
            if (idx >= entries.size()) break;
            Map.Entry<UUID, Long> entry = entries.get(idx);
            chest.setItem(BOUNTY_START + i, makeBountyItem(entry.getKey(), entry.getValue(), server));
        }

        // Navigation
        ItemStack place = new ItemStack(Items.GOLD_INGOT);
        place.setHoverName(AuctionBrowseMenu.styledName("Place Bounty", ChatFormatting.YELLOW));
        AuctionBrowseMenu.appendLore(place, AuctionBrowseMenu.lore("/bounty place <player> <amount>", ChatFormatting.GRAY));
        chest.setItem(PLACE_BTN, place);

        if (page > 1) {
            chest.setItem(PREV_SLOT, makeButton(Items.ARROW,
                    "◄ Previous Page (" + (page - 1) + "/" + totalPages + ")", ChatFormatting.YELLOW));
        }
        if (page < totalPages) {
            chest.setItem(NEXT_SLOT, makeButton(Items.ARROW,
                    "Next Page ► (" + (page + 1) + "/" + totalPages + ")", ChatFormatting.YELLOW));
        }

        ItemStack info = new ItemStack(Items.PAPER);
        info.setHoverName(AuctionBrowseMenu.styledName("Active Bounties", ChatFormatting.GOLD));
        AuctionBrowseMenu.appendLore(info, AuctionBrowseMenu.lore(
                entries.size() + " target(s) | Page " + page + "/" + totalPages, ChatFormatting.GRAY));
        chest.setItem(INFO_SLOT, info);

        ItemStack close = new ItemStack(Items.BARRIER);
        close.setHoverName(AuctionBrowseMenu.styledName("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_BTN, close);

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
            case PLACE_BTN -> {
                player.closeContainer();
                player.sendSystemMessage(MessageUtils.info(
                        "Use /bounty place <player> <amount> to place a bounty."));
            }
            case PREV_SLOT -> { if (page > 1) { page--; populateItems(player); } }
            case NEXT_SLOT -> { page++; populateItems(player); }
            case CLOSE_BTN -> player.closeContainer();
        }
    }

    // ── Item factories ────────────────────────────────────────────────────────

    private static ItemStack makeBountyItem(UUID targetUUID, long total, MinecraftServer server) {
        ItemStack skull = new ItemStack(Items.PLAYER_HEAD);

        // Look up the target player name
        String targetName = targetUUID.toString().substring(0, 8);
        if (server != null) {
            var profile = server.getProfileCache() != null
                    ? server.getProfileCache().get(targetUUID).orElse(null) : null;
            if (profile != null) targetName = profile.getName();
        }

        skull.setHoverName(Component.literal(targetName)
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true).withItalic(false)));

        AuctionBrowseMenu.appendLore(skull, Component.literal("Total Bounty: ")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false))
                .append(Component.literal(CurrencyManager.formatShort(total))
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false))));

        if (server != null) {
            List<BountyEntry> entries = BountyData.get(server).getBountiesOn(targetUUID);
            AuctionBrowseMenu.appendLore(skull, AuctionBrowseMenu.lore("────────────────────", ChatFormatting.DARK_GRAY));
            AuctionBrowseMenu.appendLore(skull, AuctionBrowseMenu.lore("Placed by:", ChatFormatting.GRAY));
            for (BountyEntry e : entries) {
                AuctionBrowseMenu.appendLore(skull, Component.literal("  " + e.getPlacerName() + ": ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withItalic(false))
                        .append(Component.literal(CurrencyManager.formatShort(e.getAmount()))
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false))));
            }
        }

        return skull;
    }

    private static ItemStack makeButton(net.minecraft.world.item.Item icon, String label, ChatFormatting color) {
        ItemStack item = new ItemStack(icon);
        item.setHoverName(AuctionBrowseMenu.styledName(label, color));
        return item;
    }

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
