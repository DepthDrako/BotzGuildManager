package com.botzguildz.gui;

import com.botzguildz.currency.CurrencyManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import java.util.UUID;

/**
 * Confirm-and-post screen for a guild item-collection bounty.
 *
 * <pre>
 * Row 0 (0-8):   [glass×4][ITEM_DISPLAY:4][glass×4]
 * Row 1 (9-17):  [ADD_LABEL:9][Bit+:10][Chip+:11][Token+:12][Coin+:13][Mark+:14][Seal+:15][glass:16][glass:17]
 * Row 2 (18-26): [SUB_LABEL:18][Bit-:19][Chip-:20][Token-:21][Coin-:22][Mark-:23][Seal-:24][glass:25][glass:26]
 * Row 3 (27-35): [glass×4][REWARD_DISPLAY:31][glass×4]
 * Row 4 (36-44): [BANK_INFO:36][glass:37][QTY-10:38][QTY-1:39][QTY_DISPLAY:40][QTY+1:41][QTY+10:42][glass×2]
 * Row 5 (45-53): [BACK:45][glass×7][CONFIRM:53]
 * </pre>
 *
 * ADD row:    left-click = +1 of that denomination; shift-click = +64.
 *             Tile shows the coin item with stack count = how many are staged.
 * REMOVE row: left-click = −1; shift-click = −64 (clamped to 0).
 *             Tile turns to red glass pane when count is 0.
 * Reward display updates immediately after every click, showing the full
 * denomination breakdown (e.g. "1 Seal, 2 Coins, 1 Bit").
 */
public class GuildBountyPostMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    // ── Slot constants ────────────────────────────────────────────────────────

    private static final int ITEM_DISPLAY  = 4;

    // Row 1 — add denomination (label + 6 tier tiles)
    private static final int ADD_LABEL = 9;
    private static final int ADD_FIRST = 10;   // ADD_FIRST + tierIdx (0-5)

    // Row 2 — remove denomination (label + 6 tier tiles)
    private static final int SUB_LABEL = 18;
    private static final int SUB_FIRST = 19;   // SUB_FIRST + tierIdx (0-5)

    // Row 3 — reward display
    private static final int REWARD_DISPLAY = 31;

    // Row 4 — bank info + quantity controls
    private static final int BANK_INFO   = 36;
    private static final int QTY_MINUS10 = 38;
    private static final int QTY_MINUS1  = 39;
    private static final int QTY_DISPLAY = 40;
    private static final int QTY_PLUS1   = 41;
    private static final int QTY_PLUS10  = 42;

    // Row 5 — action buttons
    private static final int BACK_BTN    = 45;
    private static final int CONFIRM_BTN = 53;

    private static final int  QTY_MIN = 1;
    private static final int  QTY_MAX = 1000;

    // ── State ─────────────────────────────────────────────────────────────────

    private final UUID      guildId;
    private final ItemStack selectedItem;
    private int             quantity   = 1;

    /**
     * How many of each denomination tier have been staged into the reward.
     * Index 0 = smallest tier, index 5 = largest.
     * rewardAmount = sum(tierCounts[i] * denoms[i]).
     */
    private final long[] tierCounts = new long[6];

    // ── Constructor ───────────────────────────────────────────────────────────

    public GuildBountyPostMenu(int id, Inventory inv, UUID guildId, ItemStack selectedItem) {
        this(id, inv, new SimpleContainer(CHEST_SIZE), guildId, selectedItem);
    }

    private GuildBountyPostMenu(int id, Inventory inv, SimpleContainer chest,
                                 UUID guildId, ItemStack selectedItem) {
        super(ModMenuTypes.GUILD_BOUNTY_POST_MENU.get(), id, inv, chest, ROWS);
        this.guildId      = guildId;
        this.selectedItem = selectedItem.copy();
        this.selectedItem.setCount(1);
        if (inv.player instanceof ServerPlayer) {
            populateItems(inv.player);
        }
    }

    public static GuildBountyPostMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        boolean hasGuild = buf.readBoolean();
        UUID    guildId  = hasGuild ? buf.readUUID() : null;
        ItemStack item   = buf.readItem();
        return new GuildBountyPostMenu(id, inv, new SimpleContainer(CHEST_SIZE), guildId, item);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Total reward value computed from the staged tier counts. */
    private long computeReward() {
        long[] denoms = CurrencyManager.getDenominations();
        long total = 0;
        for (int i = 0; i < Math.min(tierCounts.length, denoms.length); i++) {
            total += tierCounts[i] * denoms[i];
        }
        return total;
    }

    // ── Population ────────────────────────────────────────────────────────────

    private void populateItems(Player player) {
        SimpleContainer chest  = (SimpleContainer) getContainer();
        long[]          denoms = CurrencyManager.getDenominations();
        long            reward = computeReward();

        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());

        // ── Row 0 — selected item display ─────────────────────────────────────
        ItemStack display = selectedItem.copy();
        appendLore(display, lore("Item to be collected for this bounty", ChatFormatting.GRAY));
        chest.setItem(ITEM_DISPLAY, display);

        // ── Row 1 — ADD denomination row ──────────────────────────────────────
        ItemStack addLabel = new ItemStack(Items.LIME_DYE);
        addLabel.setHoverName(styled("ADD to Reward  →", ChatFormatting.GREEN));
        appendLore(addLabel, lore("Left-click: +1 of that coin", ChatFormatting.GRAY));
        appendLore(addLabel, lore("Shift-click: +64", ChatFormatting.DARK_GRAY));
        chest.setItem(ADD_LABEL, addLabel);

        for (int t = 0; t < 6 && t < denoms.length; t++) {
            chest.setItem(ADD_FIRST + t, makeAddTile(t, denoms[t]));
        }

        // ── Row 2 — REMOVE denomination row ───────────────────────────────────
        ItemStack subLabel = new ItemStack(Items.RED_DYE);
        subLabel.setHoverName(styled("←  REMOVE from Reward", ChatFormatting.RED));
        appendLore(subLabel, lore("Left-click: -1 of that coin", ChatFormatting.GRAY));
        appendLore(subLabel, lore("Shift-click: -64", ChatFormatting.DARK_GRAY));
        chest.setItem(SUB_LABEL, subLabel);

        for (int t = 0; t < 6 && t < denoms.length; t++) {
            chest.setItem(SUB_FIRST + t, makeSubTile(t, denoms[t]));
        }

        // ── Row 3 — reward display ────────────────────────────────────────────
        chest.setItem(REWARD_DISPLAY, rewardDisplay(reward, denoms));

        // ── Row 4 — bank info + quantity controls ─────────────────────────────
        if (player instanceof ServerPlayer sp) {
            Guild guild    = GuildUtils.getGuildOf(sp);
            long available = guild != null ? guild.getAvailableBalance() : 0;
            boolean canAfford = available >= reward && reward > 0;

            ItemStack info = CurrencyManager.getDisplayItem();
            info.setHoverName(styled("Guild Bank", ChatFormatting.GOLD));
            appendLore(info, lore("Available: " + CurrencyManager.formatShort(available), ChatFormatting.YELLOW));
            appendLore(info, lore("Reward cost: " + (reward > 0 ? CurrencyManager.formatShort(reward) : "none"),
                    reward == 0 ? ChatFormatting.GRAY : canAfford ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (!canAfford && reward > 0)
                appendLore(info, lore("Insufficient funds!", ChatFormatting.RED));
            chest.setItem(BANK_INFO, info);
        }

        chest.setItem(QTY_MINUS10, qtyBtn("-10", ChatFormatting.RED));
        chest.setItem(QTY_MINUS1,  qtyBtn("-1",  ChatFormatting.RED));
        chest.setItem(QTY_DISPLAY, quantityDisplay());
        chest.setItem(QTY_PLUS1,   qtyBtn("+1",  ChatFormatting.GREEN));
        chest.setItem(QTY_PLUS10,  qtyBtn("+10", ChatFormatting.GREEN));

        // ── Row 5 — Back / Confirm ────────────────────────────────────────────
        ItemStack back = new ItemStack(Items.ARROW);
        back.setHoverName(styled("Back", ChatFormatting.WHITE));
        appendLore(back, lore("Return to item picker", ChatFormatting.GRAY));
        chest.setItem(BACK_BTN, back);

        boolean valid = reward > 0 && quantity >= 1;
        ItemStack confirm = new ItemStack(valid ? Items.EMERALD : Items.BARRIER);
        confirm.setHoverName(styled(valid ? "Post Bounty" : "Cannot Post",
                valid ? ChatFormatting.GREEN : ChatFormatting.RED));
        appendLore(confirm, lore("Collect: " + quantity + "x " + selectedItem.getHoverName().getString(),
                ChatFormatting.WHITE));
        appendLore(confirm, lore("Reward: " + (reward > 0 ? CurrencyManager.formatShort(reward) : "not set"),
                ChatFormatting.GOLD));
        if (!valid) appendLore(confirm, lore("Add a reward amount above to post", ChatFormatting.GRAY));
        chest.setItem(CONFIRM_BTN, confirm);

        broadcastChanges();
    }

    // ── Tile builders ─────────────────────────────────────────────────────────

    /**
     * ADD tile: shows the actual coin item.
     * Stack count = how many are already staged (capped at 64, min 1 so item is visible).
     */
    private ItemStack makeAddTile(int tier, long denomValue) {
        ItemStack s = CurrencyManager.getTierItem(tier).copy();
        long staged = tierCounts[tier];
        s.setCount((int) Math.min(64, Math.max(1, staged)));
        s.setHoverName(styled("+" + CurrencyManager.formatShort(denomValue), ChatFormatting.GREEN));
        appendLore(s, lore("Staged: " + staged, ChatFormatting.YELLOW));
        appendLore(s, lore("Left-click: +1  |  Shift: +64", ChatFormatting.GRAY));
        return s;
    }

    /**
     * REMOVE tile: shows the coin item when staged count > 0 (red name),
     * or a red glass pane when none have been staged.
     */
    private ItemStack makeSubTile(int tier, long denomValue) {
        long staged = tierCounts[tier];
        if (staged <= 0) {
            ItemStack s = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            s.setHoverName(styled("-" + CurrencyManager.formatShort(denomValue), ChatFormatting.DARK_RED));
            appendLore(s, lore("None staged", ChatFormatting.DARK_GRAY));
            return s;
        }
        ItemStack s = CurrencyManager.getTierItem(tier).copy();
        s.setCount((int) Math.min(64, staged));
        s.setHoverName(styled("-" + CurrencyManager.formatShort(denomValue), ChatFormatting.RED));
        appendLore(s, lore("Staged: " + staged, ChatFormatting.YELLOW));
        appendLore(s, lore("Left-click: -1  |  Shift: -64", ChatFormatting.GRAY));
        return s;
    }

    /**
     * Reward display tile: shows the denomination breakdown so the player can see
     * exactly what they've built up, one line per active tier.
     */
    private ItemStack rewardDisplay(long reward, long[] denoms) {
        boolean valid = reward > 0;
        ItemStack s = CurrencyManager.getDisplayItem();
        s.setHoverName(styled(valid ? "Reward: " + CurrencyManager.formatShort(reward) : "No Reward Set",
                valid ? ChatFormatting.GOLD : ChatFormatting.GRAY));

        // Breakdown: one lore line per tier that has a staged count
        boolean any = false;
        for (int t = denoms.length - 1; t >= 0; t--) {
            if (t < tierCounts.length && tierCounts[t] > 0) {
                long val = tierCounts[t] * denoms[t];
                appendLore(s, lore("  " + tierCounts[t] + "x " + tierName(t)
                        + "  =  " + CurrencyManager.formatShort(val), ChatFormatting.YELLOW));
                any = true;
            }
        }

        if (!any) {
            appendLore(s, lore("Click coin tiles above to build a reward", ChatFormatting.GRAY));
        }
        appendLore(s, lore("Paid from guild bank on claim", ChatFormatting.DARK_GRAY));
        return s;
    }

    private ItemStack quantityDisplay() {
        ItemStack s = new ItemStack(Items.BOOK);
        s.setHoverName(styled("Quantity: " + quantity, ChatFormatting.WHITE));
        appendLore(s, lore("Items required for one claim", ChatFormatting.GRAY));
        appendLore(s, lore("Range: " + QTY_MIN + " – " + QTY_MAX, ChatFormatting.DARK_GRAY));
        return s;
    }

    private static ItemStack qtyBtn(String label, ChatFormatting color) {
        ItemStack s = new ItemStack(Items.PAPER);
        s.setHoverName(Component.literal(label)
                .withStyle(Style.EMPTY.withColor(color).withItalic(false)));
        appendLore(s, lore("Adjust required quantity", ChatFormatting.GRAY));
        return s;
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < CHEST_SIZE) {
            if (!(player instanceof ServerPlayer sp)) return;
            handleClick(slotId, sp, clickType == ClickType.QUICK_MOVE);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void handleClick(int slotId, ServerPlayer player, boolean shiftClick) {
        long amount = shiftClick ? 64 : 1;

        // ── ADD tier tiles (slots 10-15) ──────────────────────────────────────
        if (slotId >= ADD_FIRST && slotId < ADD_FIRST + 6) {
            int tier = slotId - ADD_FIRST;
            tierCounts[tier] += amount;
            playClick(player);
            populateItems(player);
            return;
        }

        // ── REMOVE tier tiles (slots 19-24) ───────────────────────────────────
        if (slotId >= SUB_FIRST && slotId < SUB_FIRST + 6) {
            int tier = slotId - SUB_FIRST;
            tierCounts[tier] = Math.max(0, tierCounts[tier] - amount);
            playClick(player);
            populateItems(player);
            return;
        }

        // ── Quantity controls ─────────────────────────────────────────────────
        switch (slotId) {
            case QTY_MINUS10 -> { quantity = Math.max(QTY_MIN, quantity - 10); playClick(player); populateItems(player); }
            case QTY_MINUS1  -> { quantity = Math.max(QTY_MIN, quantity - 1);  playClick(player); populateItems(player); }
            case QTY_PLUS1   -> { quantity = Math.min(QTY_MAX, quantity + 1);  playClick(player); populateItems(player); }
            case QTY_PLUS10  -> { quantity = Math.min(QTY_MAX, quantity + 10); playClick(player); populateItems(player); }
            case BACK_BTN    -> goBackToPicker(player);
            case CONFIRM_BTN -> confirmPost(player);
        }
    }

    /** Play a UI click sound directly to this player. */
    private static void playClick(ServerPlayer player) {
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.0f);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void goBackToPicker(ServerPlayer player) {
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
    }

    private void confirmPost(ServerPlayer player) {
        long rewardAmount = computeReward();

        if (rewardAmount <= 0 || quantity < 1) {
            player.sendSystemMessage(MessageUtils.error("Add a reward amount before posting."));
            return;
        }

        Guild guild = GuildUtils.getGuildOf(player);
        if (guild == null) {
            player.sendSystemMessage(MessageUtils.error("You are not in a guild."));
            return;
        }
        if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_BOUNTIES)) {
            player.sendSystemMessage(MessageUtils.error("You don't have permission to post bounties."));
            return;
        }
        if (guild.getAvailableBalance() < rewardAmount) {
            player.sendSystemMessage(MessageUtils.error("The guild bank doesn't have enough funds. Available: "
                    + CurrencyManager.format(guild.getAvailableBalance())));
            return;
        }

        // Deduct reward from guild bank (into escrow inside the entry)
        boolean withdrew = guild.withdraw(rewardAmount);
        if (!withdrew) {
            player.sendSystemMessage(MessageUtils.error("Failed to deduct reward from guild bank."));
            return;
        }
        GuildSavedData.get(player.getServer()).setDirty();

        // Create and store the bounty entry
        GuildBountyEntry entry = new GuildBountyEntry(
                UUID.randomUUID(), guild.getGuildId(),
                player.getUUID(), player.getName().getString(),
                selectedItem, quantity, rewardAmount
        );
        GuildBountyData.get(player.getServer()).addBounty(entry);

        guild.addLog(player.getName().getString() + " posted bounty: " + quantity + "x "
                + selectedItem.getHoverName().getString()
                + " → " + CurrencyManager.format(rewardAmount) + ".");

        player.sendSystemMessage(MessageUtils.success(
                "Bounty posted! Members can now claim it from the bounty board."));

        // Open bounty board
        UUID gId = guild.getGuildId();
        player.closeContainer();
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.execute(() -> NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal("Guild Bounty Board"); }
                        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new GuildBountyBoardMenu(id, inv, gId, 1);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(true);
                        buf.writeUUID(gId);
                        buf.writeInt(1);
                    }
            ));
        }
    }

    // ── Required overrides ────────────────────────────────────────────────────

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    // ── Tier name helper ──────────────────────────────────────────────────────

    private String tierName(int tier) {
        return switch (tier) {
            case 0 -> "GB";  case 1 -> "GCh";
            case 2 -> "GT";  case 3 -> "GC";
            case 4 -> "GM";  case 5 -> "GS";
            default -> "?";
        };
    }

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
