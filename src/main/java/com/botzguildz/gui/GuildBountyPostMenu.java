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

import java.util.UUID;

/**
 * Confirm-and-post screen for a guild item-collection bounty.
 *
 * <pre>
 * Row 0 (0-8):   [glass×4][ITEM_DISPLAY:4][glass×4]
 * Row 1 (9-17):  [-10:9][-1:10][glass][glass][QTY_DISPLAY:13][glass][glass][+1:15][+10:16]
 * Row 2 (18-26): [-1000:18][-100:19][-10:20][-1:21][REWARD_DISPLAY:22][+1:23][+10:24][+100:25][+1000:26]
 * Row 3 (27-35): [glass×4][BANK_INFO:31][glass×4]
 * Row 4 (36-44): [glass×9]
 * Row 5 (45-53): [BACK:45][glass×7][CONFIRM:53]
 * </pre>
 */
public class GuildBountyPostMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    // ── Slot constants ────────────────────────────────────────────────────────

    private static final int ITEM_DISPLAY  = 4;
    // Quantity row (row 1)
    private static final int QTY_MINUS10   = 9;
    private static final int QTY_MINUS1    = 10;
    private static final int QTY_DISPLAY   = 13;
    private static final int QTY_PLUS1     = 15;
    private static final int QTY_PLUS10    = 16;
    // Reward row (row 2)
    private static final int REW_MINUS1000 = 18;
    private static final int REW_MINUS100  = 19;
    private static final int REW_MINUS10   = 20;
    private static final int REW_MINUS1    = 21;
    private static final int REW_DISPLAY   = 22;
    private static final int REW_PLUS1     = 23;
    private static final int REW_PLUS10    = 24;
    private static final int REW_PLUS100   = 25;
    private static final int REW_PLUS1000  = 26;
    // Info / action
    private static final int BANK_INFO     = 31;
    private static final int BACK_BTN      = 45;
    private static final int CONFIRM_BTN   = 53;

    private static final int QTY_MIN = 1;
    private static final int QTY_MAX = 1000;
    private static final long REW_MIN = 0L;

    // ── State ─────────────────────────────────────────────────────────────────

    private final UUID      guildId;
    private final ItemStack selectedItem;
    private int             quantity   = 1;
    private long            rewardAmount = 0L;

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

    // ── Population ────────────────────────────────────────────────────────────

    private void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());

        // ── Row 0 — selected item display ─────────────────────────────────────
        ItemStack display = selectedItem.copy();
        appendLore(display, lore("Item to be collected for this bounty", ChatFormatting.GRAY));
        chest.setItem(ITEM_DISPLAY, display);

        // ── Row 1 — quantity controls ─────────────────────────────────────────
        chest.setItem(QTY_MINUS10, adjustBtn("-10",  ChatFormatting.RED,       Items.RED_STAINED_GLASS_PANE));
        chest.setItem(QTY_MINUS1,  adjustBtn("-1",   ChatFormatting.RED,       Items.RED_STAINED_GLASS_PANE));
        chest.setItem(QTY_DISPLAY, quantityDisplay());
        chest.setItem(QTY_PLUS1,   adjustBtn("+1",   ChatFormatting.GREEN,     Items.LIME_STAINED_GLASS_PANE));
        chest.setItem(QTY_PLUS10,  adjustBtn("+10",  ChatFormatting.GREEN,     Items.LIME_STAINED_GLASS_PANE));

        // ── Row 2 — reward controls ───────────────────────────────────────────
        chest.setItem(REW_MINUS1000, adjustBtn("-1000", ChatFormatting.DARK_RED,   Items.RED_STAINED_GLASS_PANE));
        chest.setItem(REW_MINUS100,  adjustBtn("-100",  ChatFormatting.RED,        Items.RED_STAINED_GLASS_PANE));
        chest.setItem(REW_MINUS10,   adjustBtn("-10",   ChatFormatting.RED,        Items.RED_STAINED_GLASS_PANE));
        chest.setItem(REW_MINUS1,    adjustBtn("-1",    ChatFormatting.RED,        Items.RED_STAINED_GLASS_PANE));
        chest.setItem(REW_DISPLAY,   rewardDisplay());
        chest.setItem(REW_PLUS1,     adjustBtn("+1",    ChatFormatting.GREEN,      Items.LIME_STAINED_GLASS_PANE));
        chest.setItem(REW_PLUS10,    adjustBtn("+10",   ChatFormatting.GREEN,      Items.LIME_STAINED_GLASS_PANE));
        chest.setItem(REW_PLUS100,   adjustBtn("+100",  ChatFormatting.GREEN,      Items.LIME_STAINED_GLASS_PANE));
        chest.setItem(REW_PLUS1000,  adjustBtn("+1000", ChatFormatting.DARK_GREEN, Items.LIME_STAINED_GLASS_PANE));

        // ── Row 3 — bank info ─────────────────────────────────────────────────
        if (player instanceof ServerPlayer sp) {
            Guild guild = GuildUtils.getGuildOf(sp);
            long available = guild != null ? guild.getAvailableBalance() : 0;
            boolean canAfford = available >= rewardAmount && rewardAmount > 0;
            ItemStack info = new ItemStack(canAfford ? Items.GOLD_INGOT : Items.GOLD_NUGGET);
            info.setHoverName(styled("Guild Bank", ChatFormatting.GOLD));
            appendLore(info, lore("Available: " + CurrencyManager.format(available), ChatFormatting.YELLOW));
            appendLore(info, lore("Reward cost: " + CurrencyManager.format(rewardAmount),
                    rewardAmount == 0 ? ChatFormatting.GRAY
                            : canAfford ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (!canAfford && rewardAmount > 0)
                appendLore(info, lore("Insufficient funds!", ChatFormatting.RED));
            chest.setItem(BANK_INFO, info);
        }

        // ── Row 5 — Back / Confirm ────────────────────────────────────────────
        ItemStack back = new ItemStack(Items.ARROW);
        back.setHoverName(styled("Back", ChatFormatting.WHITE));
        appendLore(back, lore("Return to item picker", ChatFormatting.GRAY));
        chest.setItem(BACK_BTN, back);

        boolean valid = rewardAmount > 0 && quantity >= 1;
        ItemStack confirm = new ItemStack(valid ? Items.EMERALD : Items.BARRIER);
        confirm.setHoverName(styled(valid ? "Post Bounty" : "Cannot Post", valid ? ChatFormatting.GREEN : ChatFormatting.RED));
        appendLore(confirm, lore("Collect: " + quantity + "x " + selectedItem.getHoverName().getString(),
                ChatFormatting.WHITE));
        appendLore(confirm, lore("Reward: " + CurrencyManager.format(rewardAmount), ChatFormatting.GOLD));
        if (!valid) appendLore(confirm, lore("Set a reward amount > 0 to post", ChatFormatting.GRAY));
        chest.setItem(CONFIRM_BTN, confirm);

        broadcastChanges();
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < CHEST_SIZE) {
            if (!(player instanceof ServerPlayer sp)) return;
            handleClick(slotId, sp);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void handleClick(int slotId, ServerPlayer player) {
        switch (slotId) {
            case QTY_MINUS10 -> { quantity = Math.max(QTY_MIN, quantity - 10);  populateItems(player); }
            case QTY_MINUS1  -> { quantity = Math.max(QTY_MIN, quantity - 1);   populateItems(player); }
            case QTY_PLUS1   -> { quantity = Math.min(QTY_MAX, quantity + 1);   populateItems(player); }
            case QTY_PLUS10  -> { quantity = Math.min(QTY_MAX, quantity + 10);  populateItems(player); }
            case REW_MINUS1000 -> { rewardAmount = Math.max(REW_MIN, rewardAmount - 1000); populateItems(player); }
            case REW_MINUS100  -> { rewardAmount = Math.max(REW_MIN, rewardAmount - 100);  populateItems(player); }
            case REW_MINUS10   -> { rewardAmount = Math.max(REW_MIN, rewardAmount - 10);   populateItems(player); }
            case REW_MINUS1    -> { rewardAmount = Math.max(REW_MIN, rewardAmount - 1);    populateItems(player); }
            case REW_PLUS1     -> { rewardAmount++; populateItems(player); }
            case REW_PLUS10    -> { rewardAmount += 10;   populateItems(player); }
            case REW_PLUS100   -> { rewardAmount += 100;  populateItems(player); }
            case REW_PLUS1000  -> { rewardAmount += 1000; populateItems(player); }
            case BACK_BTN      -> goBackToPicker(player);
            case CONFIRM_BTN   -> confirmPost(player);
        }
    }

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
        if (rewardAmount <= 0 || quantity < 1) {
            player.sendSystemMessage(MessageUtils.error("Set a reward amount > 0 before posting."));
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

    // ── Tile builders ─────────────────────────────────────────────────────────

    private ItemStack quantityDisplay() {
        ItemStack s = new ItemStack(Items.PAPER);
        s.setHoverName(styled("Quantity: " + quantity, ChatFormatting.WHITE));
        appendLore(s, lore("Items required for one claim", ChatFormatting.GRAY));
        appendLore(s, lore("Range: " + QTY_MIN + " – " + QTY_MAX, ChatFormatting.DARK_GRAY));
        return s;
    }

    private ItemStack rewardDisplay() {
        boolean valid = rewardAmount > 0;
        ItemStack s = new ItemStack(valid ? Items.GOLD_INGOT : Items.GOLD_NUGGET);
        s.setHoverName(styled("Reward: " + CurrencyManager.format(rewardAmount),
                valid ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        appendLore(s, lore("Paid from guild bank to claimer", ChatFormatting.GRAY));
        if (!valid) appendLore(s, lore("Must be > 0 to post", ChatFormatting.RED));
        return s;
    }

    private static ItemStack adjustBtn(String label, ChatFormatting color,
                                        net.minecraft.world.item.Item paneItem) {
        ItemStack s = new ItemStack(paneItem);
        s.setHoverName(Component.literal(label)
                .withStyle(Style.EMPTY.withColor(color).withItalic(false)));
        return s;
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
