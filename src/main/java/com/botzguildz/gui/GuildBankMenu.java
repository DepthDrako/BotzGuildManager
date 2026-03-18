package com.botzguildz.gui;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildRank;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.registry.ModItems;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkHooks;

/**
 * Guild Bank GUI — 6-row chest.
 *
 * <pre>
 * Row 0 (0-8):   [glass×4][TITLE:4][glass×4]
 * Row 1 (9-17):  [WALLET:9][glass×3][GUILD_BANK:13][glass×3][MY_RANK:17]
 * Row 2 (18-26): [DON_LABEL:18][Bit:19][Chip:20][Token:21][Coin:22][Mark:23][Seal:24][DON_ALL:25][glass:26]
 * Row 3 (27-35): [WD_LABEL:27][Bit:28][Chip:29][Token:30][Coin:31][Mark:32][Seal:33][WD_ALL:34][glass:35]
 * Row 4 (36-44): [glass×9]
 * Row 5 (45-53): [BACK:45][glass×7][CLOSE:53]
 * </pre>
 *
 * Donate row: click a tier tile to send that many from your wallet to the guild bank.
 *   Left-click = 1 item, Shift-click = 1 full stack (64 items).
 * Withdraw row: restricted by rank permission + configurable daily limit.
 *   Left-click = 1, Shift-click = up to 64 (capped by remaining daily allowance).
 */
public class GuildBankMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    // ── Slot constants ────────────────────────────────────────────────────────

    private static final int TITLE_SLOT  = 4;
    private static final int WALLET_SLOT = 9;
    private static final int GUILD_SLOT  = 13;
    private static final int RANK_SLOT   = 17;

    // Donate row
    private static final int DON_LABEL  = 18;
    private static final int DON_FIRST  = 19;   // DON_FIRST + tierIdx = slot
    private static final int DON_ALL    = 25;

    // Withdraw row
    private static final int WD_LABEL   = 27;
    private static final int WD_FIRST   = 28;   // WD_FIRST + tierIdx = slot
    private static final int WD_ALL     = 34;

    // Row 5
    private static final int BACK_BTN  = 45;
    private static final int CLOSE_BTN = 53;

    // ── Tier names ────────────────────────────────────────────────────────────

    private static final String[] TIER_NAMES = {
            "Guild Bit", "Guild Chip", "Guild Token",
            "Guild Coin", "Guild Mark", "Guild Seal"
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public GuildBankMenu(int id, Inventory playerInv) {
        this(id, playerInv, new SimpleContainer(CHEST_SIZE));
    }

    private GuildBankMenu(int id, Inventory playerInv, SimpleContainer chest) {
        super(ModMenuTypes.GUILD_BANK_MENU.get(), id, playerInv, chest, ROWS);
        populateItems(playerInv.player);
    }

    public static GuildBankMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        return new GuildBankMenu(id, playerInv, new SimpleContainer(CHEST_SIZE));
    }

    // ── Population ────────────────────────────────────────────────────────────

    public void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) this.getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());

        ServerPlayer sp     = (player instanceof ServerPlayer s) ? s : null;
        MinecraftServer srv = (sp != null) ? sp.getServer() : null;
        if (sp == null || srv == null) { broadcastChanges(); return; }

        GuildSavedData data    = GuildSavedData.get(srv);
        Guild          guild   = GuildUtils.getGuildOf(sp);
        long walletBal         = data.getWallet(player.getUUID());
        long guildBal          = guild != null ? guild.getAvailableBalance() : 0L;

        Item[]  tiers      = tierItems();
        long[]  tierValues = ModItems.TIER_VALUES;

        // ── Rank & permission info ────────────────────────────────────────────
        GuildRank myRank         = guild != null ? guild.getMemberRank(player.getUUID()) : null;
        String    rankName       = myRank != null ? myRank.getName() : "Unknown";
        boolean   canWithdraw    = guild != null && guild.canWithdrawFromBank(player.getUUID());
        long      withdrawnToday = data.getDailyWithdrawn(player.getUUID());
        long      remaining      = guild != null
                ? guild.getRemainingDailyWithdraw(player.getUUID(), withdrawnToday)
                : 0L;

        // ── Row 0 — title ─────────────────────────────────────────────────────
        ItemStack title = new ItemStack(Items.EMERALD);
        title.setHoverName(styled(
                guild != null ? guild.getName() + " Bank" : "Guild Bank",
                ChatFormatting.GREEN));
        appendLore(title, lore("Donate to or withdraw from the guild treasury", ChatFormatting.GRAY));
        chest.setItem(TITLE_SLOT, title);

        // ── Row 1 — balances ──────────────────────────────────────────────────
        ItemStack walletItem = new ItemStack(Items.GOLD_INGOT);
        walletItem.setHoverName(styled("Your Wallet", ChatFormatting.GOLD));
        appendLore(walletItem, lore(CurrencyManager.format(walletBal), ChatFormatting.YELLOW));
        appendLore(walletItem, lore("Available to donate", ChatFormatting.GRAY));
        chest.setItem(WALLET_SLOT, walletItem);

        ItemStack guildItem = new ItemStack(Items.CHEST);
        if (guild != null) {
            guildItem.setHoverName(styled(guild.getName() + " Treasury", ChatFormatting.GREEN));
            appendLore(guildItem, lore(CurrencyManager.format(guildBal), ChatFormatting.YELLOW));
            appendLore(guildItem, lore("Guild available balance", ChatFormatting.GRAY));
        } else {
            guildItem.setHoverName(styled("Guild Treasury", ChatFormatting.DARK_GRAY));
            appendLore(guildItem, lore("Not in a guild", ChatFormatting.RED));
        }
        chest.setItem(GUILD_SLOT, guildItem);

        ItemStack rankItem = new ItemStack(canWithdraw ? Items.DIAMOND : Items.COAL);
        rankItem.setHoverName(styled("Your Rank: " + rankName,
                canWithdraw ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        if (guild != null) {
            long limit = guild.getRankDailyLimit(rankName);
            if (canWithdraw) {
                String limitStr = (limit == Long.MAX_VALUE)
                        ? "Unlimited"
                        : CurrencyManager.format(limit) + " / day";
                String remStr = (remaining == Long.MAX_VALUE)
                        ? "Unlimited remaining"
                        : CurrencyManager.format(remaining) + " remaining today";
                appendLore(rankItem, lore("Daily withdrawal: " + limitStr, ChatFormatting.AQUA));
                appendLore(rankItem, lore(remStr, ChatFormatting.YELLOW));
                if (withdrawnToday > 0 && limit != Long.MAX_VALUE)
                    appendLore(rankItem, lore("Withdrawn today: " + CurrencyManager.format(withdrawnToday), ChatFormatting.DARK_GRAY));
            } else {
                appendLore(rankItem, lore("No withdrawal permission", ChatFormatting.RED));
                appendLore(rankItem, lore("High-ranking officers can withdraw", ChatFormatting.DARK_GRAY));
            }
        }
        chest.setItem(RANK_SLOT, rankItem);

        // ── Row 2 — DONATE ────────────────────────────────────────────────────
        ItemStack donLabel = new ItemStack(Items.HOPPER);
        donLabel.setHoverName(styled("DONATE  Wallet  →  Guild Bank", ChatFormatting.GREEN));
        appendLore(donLabel, lore("Click a coin to donate from your wallet", ChatFormatting.GRAY));
        appendLore(donLabel, lore("Left-click: donate 1 item", ChatFormatting.DARK_GRAY));
        appendLore(donLabel, lore("Shift-click: donate 64 items", ChatFormatting.DARK_GRAY));
        appendLore(donLabel, lore("Wallet: " + CurrencyManager.format(walletBal), ChatFormatting.YELLOW));
        chest.setItem(DON_LABEL, donLabel);

        for (int t = 0; t < 6; t++)
            chest.setItem(DON_FIRST + t, makeDonateTile(tiers[t], TIER_NAMES[t], tierValues[t], walletBal));

        ItemStack donAll = new ItemStack(Items.EMERALD_BLOCK);
        donAll.setHoverName(styled("Donate All", ChatFormatting.GREEN));
        appendLore(donAll, lore("Send your entire wallet to the guild bank", ChatFormatting.GRAY));
        appendLore(donAll, lore("Total: " + CurrencyManager.format(walletBal), ChatFormatting.YELLOW));
        if (walletBal <= 0) appendLore(donAll, lore("Wallet is empty", ChatFormatting.RED));
        chest.setItem(DON_ALL, donAll);

        // ── Row 3 — WITHDRAW ──────────────────────────────────────────────────
        ItemStack wdLabel = new ItemStack(Items.DISPENSER);
        wdLabel.setHoverName(styled("WITHDRAW  Guild Bank  →  Wallet", ChatFormatting.YELLOW));
        if (canWithdraw) {
            appendLore(wdLabel, lore("Click a coin to withdraw it to your wallet", ChatFormatting.GRAY));
            appendLore(wdLabel, lore("Left-click: 1  |  Shift-click: up to 64", ChatFormatting.DARK_GRAY));
            String remStr = (remaining == Long.MAX_VALUE)
                    ? "Unlimited remaining today"
                    : CurrencyManager.format(remaining) + " remaining today";
            appendLore(wdLabel, lore(remStr, ChatFormatting.YELLOW));
        } else {
            appendLore(wdLabel, lore("Your rank cannot withdraw from the guild bank", ChatFormatting.RED));
            appendLore(wdLabel, lore("Contact a guild leader to adjust permissions", ChatFormatting.DARK_GRAY));
        }
        chest.setItem(WD_LABEL, wdLabel);

        for (int t = 0; t < 6; t++)
            chest.setItem(WD_FIRST + t,
                    makeWithdrawTile(tiers[t], TIER_NAMES[t], tierValues[t], guildBal, canWithdraw, remaining));

        ItemStack wdAll = new ItemStack(Items.GOLD_BLOCK);
        wdAll.setHoverName(styled("Withdraw All", canWithdraw ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
        if (canWithdraw) {
            long maxByLimit = (remaining == Long.MAX_VALUE) ? guildBal : Math.min(guildBal, remaining);
            appendLore(wdAll, lore("Withdraw as much as your daily limit allows", ChatFormatting.GRAY));
            appendLore(wdAll, lore("Max this action: " + CurrencyManager.format(maxByLimit), ChatFormatting.YELLOW));
            if (guildBal <= 0) appendLore(wdAll, lore("Treasury is empty", ChatFormatting.RED));
            else if (remaining <= 0 && remaining != Long.MAX_VALUE)
                appendLore(wdAll, lore("Daily limit exhausted", ChatFormatting.RED));
        } else {
            appendLore(wdAll, lore("No withdrawal permission", ChatFormatting.RED));
        }
        chest.setItem(WD_ALL, wdAll);

        // ── Row 5 — navigation ────────────────────────────────────────────────
        ItemStack back = new ItemStack(Items.ARROW);
        back.setHoverName(styled("Back to Personal Bank", ChatFormatting.WHITE));
        chest.setItem(BACK_BTN, back);

        ItemStack close = new ItemStack(Items.BARRIER);
        close.setHoverName(styled("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_BTN, close);

        broadcastChanges();
    }

    // ── Tile builders ─────────────────────────────────────────────────────────

    private static ItemStack makeDonateTile(Item tierItem, String name, long value, long walletBal) {
        long canAfford = walletBal / value;
        if (canAfford <= 0) {
            ItemStack s = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            s.setHoverName(styled(name + " — Cannot afford", ChatFormatting.RED));
            appendLore(s, lore("Need " + CurrencyManager.format(value) + " in wallet", ChatFormatting.DARK_GRAY));
            return s;
        }
        int displayCount = (int) Math.min(canAfford, 64);
        ItemStack s = new ItemStack(tierItem, displayCount);
        s.setHoverName(styled("Donate  " + name, ChatFormatting.GREEN));
        appendLore(s, lore("Each worth " + CurrencyManager.format(value), ChatFormatting.GRAY));
        appendLore(s, lore("Can donate: " + canAfford + "  (wallet: " + CurrencyManager.format(walletBal) + ")", ChatFormatting.YELLOW));
        appendLore(s, lore("Left-click: donate 1", ChatFormatting.GRAY));
        appendLore(s, lore("Shift-click: donate 64", ChatFormatting.GRAY));
        return s;
    }

    private static ItemStack makeWithdrawTile(Item tierItem, String name, long value,
                                              long guildBal, boolean canWithdraw, long remaining) {
        if (!canWithdraw) {
            ItemStack s = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            s.setHoverName(styled(name + " — No permission", ChatFormatting.RED));
            appendLore(s, lore("Your rank cannot withdraw", ChatFormatting.DARK_GRAY));
            return s;
        }
        if (remaining <= 0 && remaining != Long.MAX_VALUE) {
            ItemStack s = new ItemStack(Items.ORANGE_STAINED_GLASS_PANE);
            s.setHoverName(styled(name + " — Daily limit reached", ChatFormatting.GOLD));
            appendLore(s, lore("Resets at midnight (server time)", ChatFormatting.DARK_GRAY));
            return s;
        }
        long guildCanAfford   = guildBal / value;
        long limitCanAfford   = (remaining == Long.MAX_VALUE) ? guildCanAfford : remaining / value;
        long canWithdrawCount = Math.min(guildCanAfford, limitCanAfford);
        if (canWithdrawCount <= 0) {
            ItemStack s = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            s.setHoverName(styled(name + " — " + (guildBal < value ? "Treasury too low" : "Limit too low"),
                    ChatFormatting.RED));
            appendLore(s, lore("Guild: " + CurrencyManager.format(guildBal)
                    + "  Need: " + CurrencyManager.format(value), ChatFormatting.DARK_GRAY));
            return s;
        }
        int displayCount = (int) Math.min(canWithdrawCount, 64);
        ItemStack s = new ItemStack(tierItem, displayCount);
        s.setHoverName(styled("Withdraw  " + name, ChatFormatting.YELLOW));
        appendLore(s, lore("Each worth " + CurrencyManager.format(value), ChatFormatting.GRAY));
        appendLore(s, lore("Treasury: " + CurrencyManager.format(guildBal), ChatFormatting.YELLOW));
        String remStr = (remaining == Long.MAX_VALUE)
                ? "Unlimited"
                : CurrencyManager.format(remaining) + " remaining today";
        appendLore(s, lore("Daily remaining: " + remStr, ChatFormatting.AQUA));
        appendLore(s, lore("Left-click: withdraw 1", ChatFormatting.GRAY));
        appendLore(s, lore("Shift-click: withdraw up to 64 (or limit)", ChatFormatting.GRAY));
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
        MinecraftServer server = player.getServer();
        if (server == null) return;

        GuildSavedData data = GuildSavedData.get(server);
        Guild          guild = GuildUtils.getGuildOf(player);
        long walletBal       = data.getWallet(player.getUUID());
        Item[] tiers         = tierItems();
        long[] tierValues    = ModItems.TIER_VALUES;

        // ── Donate tiles (19-24): wallet → guild bank ─────────────────────────
        if (slotId >= DON_FIRST && slotId < DON_FIRST + 6) {
            if (guild == null) { populateItems(player); return; }
            int  t         = slotId - DON_FIRST;
            long canAfford = walletBal / tierValues[t];
            if (canAfford <= 0) { populateItems(player); return; }

            long toDonate = shiftClick ? Math.min(canAfford, 64) : 1;
            long value    = toDonate * tierValues[t];

            data.deductFromWallet(player.getUUID(), value);
            guild.deposit(value);
            data.setDirty();
            guild.addLog(player.getName().getString() + " donated " + CurrencyManager.format(value) + " to the treasury.");
            player.sendSystemMessage(MessageUtils.success(
                    "Donated " + CurrencyManager.format(value) + " to " + guild.getName() + "'s treasury."));
            populateItems(player);
            return;
        }

        // ── Donate All ────────────────────────────────────────────────────────
        if (slotId == DON_ALL) {
            if (guild == null || walletBal <= 0) { populateItems(player); return; }
            data.deductFromWallet(player.getUUID(), walletBal);
            guild.deposit(walletBal);
            data.setDirty();
            guild.addLog(player.getName().getString() + " donated " + CurrencyManager.format(walletBal) + " to the treasury.");
            player.sendSystemMessage(MessageUtils.success(
                    "Donated all " + CurrencyManager.format(walletBal) + " to " + guild.getName() + "'s treasury."));
            populateItems(player);
            return;
        }

        // ── Withdraw tiles (28-33): guild bank → wallet ───────────────────────
        if (slotId >= WD_FIRST && slotId < WD_FIRST + 6) {
            if (guild == null) { populateItems(player); return; }
            if (!guild.canWithdrawFromBank(player.getUUID())) {
                player.sendSystemMessage(MessageUtils.error("Your rank does not allow withdrawing from the guild bank."));
                populateItems(player); return;
            }
            long remaining = guild.getRemainingDailyWithdraw(player.getUUID(), data.getDailyWithdrawn(player.getUUID()));
            if (remaining <= 0 && remaining != Long.MAX_VALUE) {
                player.sendSystemMessage(MessageUtils.error("You have reached your daily withdrawal limit."));
                populateItems(player); return;
            }
            int  t              = slotId - WD_FIRST;
            long guildBal       = guild.getAvailableBalance();
            long guildCanAfford = guildBal / tierValues[t];
            long limitCanAfford = (remaining == Long.MAX_VALUE) ? guildCanAfford : remaining / tierValues[t];
            long available      = Math.min(guildCanAfford, limitCanAfford);
            if (available <= 0) { populateItems(player); return; }

            long toWithdraw = shiftClick ? Math.min(available, 64) : 1;
            long value      = toWithdraw * tierValues[t];

            if (!guild.withdraw(value)) {
                player.sendSystemMessage(MessageUtils.error("Guild treasury has insufficient funds."));
                populateItems(player); return;
            }
            data.setDirty();
            data.addDailyWithdrawn(player.getUUID(), value);
            data.addToWallet(player.getUUID(), value);
            guild.addLog(player.getName().getString() + " withdrew " + CurrencyManager.format(value) + " from the treasury.");
            player.sendSystemMessage(MessageUtils.success(
                    "Withdrew " + CurrencyManager.format(value) + " from " + guild.getName() + "'s treasury → wallet."));
            populateItems(player);
            return;
        }

        // ── Withdraw All ──────────────────────────────────────────────────────
        if (slotId == WD_ALL) {
            if (guild == null) { populateItems(player); return; }
            if (!guild.canWithdrawFromBank(player.getUUID())) {
                player.sendSystemMessage(MessageUtils.error("Your rank does not allow withdrawing from the guild bank."));
                populateItems(player); return;
            }
            long remaining = guild.getRemainingDailyWithdraw(player.getUUID(), data.getDailyWithdrawn(player.getUUID()));
            if (remaining <= 0 && remaining != Long.MAX_VALUE) {
                player.sendSystemMessage(MessageUtils.error("You have reached your daily withdrawal limit."));
                populateItems(player); return;
            }
            long guildBal = guild.getAvailableBalance();
            if (guildBal <= 0) {
                player.sendSystemMessage(MessageUtils.error("Guild treasury is empty."));
                populateItems(player); return;
            }
            long toWithdraw = (remaining == Long.MAX_VALUE) ? guildBal : Math.min(guildBal, remaining);
            if (!guild.withdraw(toWithdraw)) {
                player.sendSystemMessage(MessageUtils.error("Guild treasury has insufficient funds."));
                populateItems(player); return;
            }
            data.setDirty();
            data.addDailyWithdrawn(player.getUUID(), toWithdraw);
            data.addToWallet(player.getUUID(), toWithdraw);
            guild.addLog(player.getName().getString() + " withdrew " + CurrencyManager.format(toWithdraw) + " from the treasury.");
            player.sendSystemMessage(MessageUtils.success(
                    "Withdrew " + CurrencyManager.format(toWithdraw) + " from " + guild.getName() + "'s treasury → wallet."));
            populateItems(player);
            return;
        }

        // ── Navigation ────────────────────────────────────────────────────────
        if (slotId == BACK_BTN) {
            player.closeContainer();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal("Personal Bank"); }
                        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new PersonalBankMenu(id, inv);
                        }
                    },
                    buf -> {}
            );
            return;
        }

        if (slotId == CLOSE_BTN) {
            player.closeContainer();
        }
    }

    // ── Required overrides ────────────────────────────────────────────────────

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    // ── Tier table ────────────────────────────────────────────────────────────

    private static Item[] tierItems() {
        return new Item[]{
                ModItems.GUILD_BIT.get(),   ModItems.GUILD_CHIP.get(),
                ModItems.GUILD_TOKEN.get(), ModItems.GUILD_COIN.get(),
                ModItems.GUILD_MARK.get(),  ModItems.GUILD_SEAL.get()
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
