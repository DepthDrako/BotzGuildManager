package com.botzguildz.gui;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.GuildSavedData.BankVault;
import com.botzguildz.registry.ModItems;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.ChestStockHelper;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
 * Personal Bank GUI — 6-row chest.
 *
 * <pre>
 * Row 0 (0-8):   [glass ×4][TITLE:4][glass ×4]
 * Row 1 (9-17):  [SOURCE:9][glass ×3][WALLET:13][glass ×3][GUILD_BANK:17]
 * Row 2 (18-26): [DEP_LABEL:18][Bit:19][Chip:20][Token:21][Coin:22][Mark:23][Seal:24][DEP_ALL:25][glass]
 * Row 3 (27-35): [WD_LABEL:27][Bit:28][Chip:29][Token:30][Coin:31][Mark:32][Seal:33][WD_ALL:34][glass]
 * Row 4 (36-44): [glass ×3][DONATE:39][glass][PAY_INFO:41][glass ×3]
 * Row 5 (45-53): [CLOSE:45][glass ×7][CLOSE:53]
 * </pre>
 *
 * Deposit row:  each slot shows the currency tier item with stack-size = count available.
 *               Left-click = deposit one stack (up to 64).  Shift-click = deposit all.
 * Withdraw row: each slot shows the tier item with stack-size = how many the wallet can afford.
 *               Left-click = withdraw 1.  Shift-click = withdraw a full stack (up to 64).
 */
public class PersonalBankMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    // ── Slot constants ────────────────────────────────────────────────────────

    private static final int TITLE_SLOT      = 4;
    private static final int SOURCE_SLOT     = 9;   // vault or on-hand display
    private static final int WALLET_SLOT     = 13;
    private static final int GUILD_BANK_SLOT = 17;

    // Deposit row — slot 18 is the label, 19-24 are the six tiers, 25 = deposit all
    private static final int DEP_LABEL  = 18;
    private static final int DEP_FIRST  = 19;       // DEP_FIRST + tierIdx = slot for that tier
    private static final int DEP_ALL    = 25;

    // Withdraw row — slot 27 is the label, 28-33 are the six tiers, 34 = withdraw all
    private static final int WD_LABEL   = 27;
    private static final int WD_FIRST   = 28;       // WD_FIRST + tierIdx = slot for that tier
    private static final int WD_ALL     = 34;

    // Row 4
    private static final int DONATE_BTN = 39;
    private static final int PAY_INFO   = 41;

    // Row 5
    private static final int CLOSE_L    = 45;
    private static final int CLOSE_R    = 53;

    // ── Tier metadata ─────────────────────────────────────────────────────────

    private static final String[] TIER_NAMES = {
            "Guild Bit", "Guild Chip", "Guild Token",
            "Guild Coin", "Guild Mark", "Guild Seal"
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public PersonalBankMenu(int id, Inventory playerInv) {
        this(id, playerInv, new SimpleContainer(CHEST_SIZE));
    }

    private PersonalBankMenu(int id, Inventory playerInv, SimpleContainer chest) {
        super(ModMenuTypes.PERSONAL_BANK_MENU.get(), id, playerInv, chest, ROWS);
        populateItems(playerInv.player);
    }

    public static PersonalBankMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        return new PersonalBankMenu(id, playerInv, new SimpleContainer(CHEST_SIZE));
    }

    // ── Population ────────────────────────────────────────────────────────────

    public void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) this.getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());

        ServerPlayer sp     = (player instanceof ServerPlayer s) ? s : null;
        MinecraftServer srv = (sp != null) ? sp.getServer() : null;

        long walletBal   = srv != null ? GuildSavedData.get(srv).getWallet(player.getUUID()) : 0;
        Guild guild      = (sp != null) ? GuildUtils.getGuildOf(sp) : null;

        // Vault resolution
        BankVault vault      = srv != null ? GuildSavedData.get(srv).getBankVault(player.getUUID()) : null;
        ServerLevel vaultLvl = resolveVaultLevel(sp);
        boolean vaultReady   = vaultLvl != null;

        // Per-tier source counts (for deposit row)
        Item[] tiers       = tierItems();
        long[] tierValues  = ModItems.TIER_VALUES;
        long[] srcCounts   = new long[6]; // how many of each tier are in vault/inventory
        for (int t = 0; t < 6; t++) {
            srcCounts[t] = vaultReady
                    ? ChestStockHelper.countTierInStorage(vaultLvl, vault.pos(), tiers[t])
                    : (sp != null ? countTierInInventory(sp, tiers[t]) : 0);
        }
        long totalSourceValue = 0;
        for (int t = 0; t < 6; t++) totalSourceValue += srcCounts[t] * tierValues[t];

        // ── Row 0 — title ────────────────────────────────────────────────────
        ItemStack title = new ItemStack(Items.GOLD_INGOT);
        title.setHoverName(styledName("Personal Bank", ChatFormatting.GOLD));
        appendLore(title, lore("Manage your coins", ChatFormatting.GRAY));
        chest.setItem(TITLE_SLOT, title);

        // ── Row 1 — balance display ───────────────────────────────────────────
        // Source slot
        String sourceLabel = vaultReady ? "Vault Storage" : "On Hand";
        ItemStack sourceItem = new ItemStack(vaultReady ? Items.ENDER_CHEST : Items.GOLD_INGOT);
        sourceItem.setHoverName(styledName(sourceLabel, vaultReady ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        appendLore(sourceItem, lore(CurrencyManager.format(totalSourceValue), ChatFormatting.YELLOW));
        if (vaultReady) {
            assert vault != null;
            net.minecraft.core.BlockPos vp = vault.pos();
            appendLore(sourceItem, lore("Block at: " + vp.getX() + ", " + vp.getY() + ", " + vp.getZ(), ChatFormatting.DARK_GRAY));
            appendLore(sourceItem, lore("/bank vault clear  to unregister", ChatFormatting.DARK_GRAY));
        } else {
            appendLore(sourceItem, lore(CurrencyManager.currencyName() + " in inventory", ChatFormatting.GRAY));
            appendLore(sourceItem, lore("/bank vault set  to link a storage block", ChatFormatting.DARK_GRAY));
        }
        chest.setItem(SOURCE_SLOT, sourceItem);

        // Wallet slot
        ItemStack walletItem = new ItemStack(Items.ENDER_CHEST);
        walletItem.setHoverName(styledName("Wallet Balance", ChatFormatting.AQUA));
        appendLore(walletItem, lore(CurrencyManager.format(walletBal), ChatFormatting.YELLOW));
        appendLore(walletItem, lore("Server-side coins (safe)", ChatFormatting.GRAY));
        appendLore(walletItem, lore("Used for AH, shops, bounties", ChatFormatting.DARK_GRAY));
        chest.setItem(WALLET_SLOT, walletItem);

        // Guild bank slot
        ItemStack guildItem = new ItemStack(Items.CHEST);
        if (guild != null) {
            guildItem.setHoverName(styledName(guild.getName() + " Bank", ChatFormatting.GREEN));
            appendLore(guildItem, lore(CurrencyManager.format(guild.getAvailableBalance()), ChatFormatting.YELLOW));
            appendLore(guildItem, lore("Available guild balance", ChatFormatting.GRAY));
            appendLore(guildItem, lore("Use Donate (below) to contribute", ChatFormatting.DARK_GRAY));
        } else {
            guildItem.setHoverName(styledName("Guild Bank", ChatFormatting.GRAY));
            appendLore(guildItem, lore("Not in a guild", ChatFormatting.DARK_GRAY));
        }
        chest.setItem(GUILD_BANK_SLOT, guildItem);

        // ── Row 2 — DEPOSIT ───────────────────────────────────────────────────
        String srcWord = vaultReady ? "vault" : "inventory";
        ItemStack depLabel = new ItemStack(Items.HOPPER);
        depLabel.setHoverName(styledName("DEPOSIT  →  Wallet", ChatFormatting.GREEN));
        appendLore(depLabel, lore("Click a coin below to deposit it", ChatFormatting.GRAY));
        appendLore(depLabel, lore("Left-click: 1 stack (64)", ChatFormatting.DARK_GRAY));
        appendLore(depLabel, lore("Shift-click: deposit all of that type", ChatFormatting.DARK_GRAY));
        appendLore(depLabel, lore("Available in " + srcWord + ": " + CurrencyManager.format(totalSourceValue), ChatFormatting.YELLOW));
        chest.setItem(DEP_LABEL, depLabel);

        for (int t = 0; t < 6; t++) {
            chest.setItem(DEP_FIRST + t, makeDepositTile(tiers[t], TIER_NAMES[t], tierValues[t], srcCounts[t], srcWord));
        }

        ItemStack depAll = new ItemStack(Items.CHEST);
        depAll.setHoverName(styledName("Deposit All", ChatFormatting.GREEN));
        appendLore(depAll, lore("Sweep ALL currency from " + srcWord, ChatFormatting.GRAY));
        appendLore(depAll, lore("into your wallet", ChatFormatting.GRAY));
        appendLore(depAll, lore("Total: " + CurrencyManager.format(totalSourceValue), ChatFormatting.YELLOW));
        chest.setItem(DEP_ALL, depAll);

        // ── Row 3 — WITHDRAW ──────────────────────────────────────────────────
        String dstWord = vaultReady ? "vault" : "hand";
        ItemStack wdLabel = new ItemStack(Items.DISPENSER);
        wdLabel.setHoverName(styledName("WITHDRAW  Wallet  →", ChatFormatting.YELLOW));
        appendLore(wdLabel, lore("Click a coin below to withdraw it", ChatFormatting.GRAY));
        appendLore(wdLabel, lore("Left-click: 1 item", ChatFormatting.DARK_GRAY));
        appendLore(wdLabel, lore("Shift-click: 1 stack (64 or max)", ChatFormatting.DARK_GRAY));
        appendLore(wdLabel, lore("Wallet: " + CurrencyManager.format(walletBal), ChatFormatting.YELLOW));
        chest.setItem(WD_LABEL, wdLabel);

        for (int t = 0; t < 6; t++) {
            chest.setItem(WD_FIRST + t, makeWithdrawTile(tiers[t], TIER_NAMES[t], tierValues[t], walletBal));
        }

        ItemStack wdAll = new ItemStack(Items.DISPENSER);
        wdAll.setHoverName(styledName("Withdraw All", ChatFormatting.YELLOW));
        appendLore(wdAll, lore("Convert entire wallet balance", ChatFormatting.GRAY));
        appendLore(wdAll, lore("to physical items → " + dstWord, ChatFormatting.GRAY));
        appendLore(wdAll, lore("Wallet: " + CurrencyManager.format(walletBal), ChatFormatting.YELLOW));
        chest.setItem(WD_ALL, wdAll);

        // ── Row 4 — donate / pay ──────────────────────────────────────────────
        ItemStack donate = new ItemStack(guild != null ? Items.EMERALD : Items.GRAY_DYE);
        if (guild != null) {
            donate.setHoverName(styledName("Guild Bank", ChatFormatting.GOLD));
            appendLore(donate, lore("Open the " + guild.getName() + " bank menu", ChatFormatting.GRAY));
            appendLore(donate, lore("Donate or withdraw from the guild treasury", ChatFormatting.DARK_GRAY));
            appendLore(donate, lore("Treasury: " + CurrencyManager.format(guild.getAvailableBalance()), ChatFormatting.YELLOW));
        } else {
            donate.setHoverName(styledName("Guild Bank", ChatFormatting.DARK_GRAY));
            appendLore(donate, lore("Join a guild first", ChatFormatting.RED));
        }
        chest.setItem(DONATE_BTN, donate);

        ItemStack payInfo = new ItemStack(Items.NAME_TAG);
        payInfo.setHoverName(styledName("Pay a Player", ChatFormatting.AQUA));
        appendLore(payInfo, lore("/bank pay <player> <amount>", ChatFormatting.GRAY));
        appendLore(payInfo, lore("Transfers wallet → wallet", ChatFormatting.DARK_GRAY));
        chest.setItem(PAY_INFO, payInfo);

        // ── Row 5 — close ─────────────────────────────────────────────────────
        ItemStack close = new ItemStack(Items.BARRIER);
        close.setHoverName(styledName("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_L, close);
        chest.setItem(CLOSE_R, close.copy());

        broadcastChanges();
    }

    // ── Tile builders ─────────────────────────────────────────────────────────

    /** Deposit row tile: shows the actual tier item with count = available in source. */
    private ItemStack makeDepositTile(Item tierItem, String name, long value,
                                      long countInSrc, String srcWord) {
        if (countInSrc <= 0) {
            ItemStack s = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            s.setHoverName(styledName(name + " — None available", ChatFormatting.RED));
            appendLore(s, lore("0 " + name + " in " + srcWord, ChatFormatting.DARK_GRAY));
            return s;
        }
        int displayCount = (int) Math.min(countInSrc, 64);
        ItemStack s = new ItemStack(tierItem, displayCount);
        s.setHoverName(styledName("Deposit  " + name, ChatFormatting.GREEN));
        appendLore(s, lore(countInSrc + " in " + srcWord + "  (worth " + CurrencyManager.format(countInSrc * value) + ")", ChatFormatting.YELLOW));
        appendLore(s, lore("Left-click: deposit 1 stack (up to 64)", ChatFormatting.GRAY));
        appendLore(s, lore("Shift-click: deposit all " + countInSrc, ChatFormatting.GRAY));
        return s;
    }

    /** Withdraw row tile: shows the tier item with count = how many the wallet can afford. */
    private ItemStack makeWithdrawTile(Item tierItem, String name, long value, long walletBal) {
        long canAfford = walletBal / value;
        if (canAfford <= 0) {
            ItemStack s = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            s.setHoverName(styledName(name + " — Cannot afford", ChatFormatting.RED));
            appendLore(s, lore("Need " + CurrencyManager.format(value) + " in wallet", ChatFormatting.DARK_GRAY));
            return s;
        }
        int displayCount = (int) Math.min(canAfford, 64);
        ItemStack s = new ItemStack(tierItem, displayCount);
        s.setHoverName(styledName("Withdraw  " + name, ChatFormatting.YELLOW));
        appendLore(s, lore("Each costs " + CurrencyManager.format(value), ChatFormatting.GRAY));
        appendLore(s, lore("Can afford: " + canAfford + "  (wallet: " + CurrencyManager.format(walletBal) + ")", ChatFormatting.YELLOW));
        appendLore(s, lore("Left-click: withdraw 1", ChatFormatting.GRAY));
        appendLore(s, lore("Shift-click: withdraw up to 64", ChatFormatting.GRAY));
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

        GuildSavedData data     = GuildSavedData.get(server);
        ServerLevel    vaultLvl = resolveVaultLevel(player);
        BankVault      vault    = resolveVault(player);
        boolean        hasVault = vaultLvl != null && vault != null;

        Item[] tiers      = tierItems();
        long[] tierValues = ModItems.TIER_VALUES;

        // ── Deposit tier tiles (slots 19-24) ──────────────────────────────────
        if (slotId >= DEP_FIRST && slotId < DEP_FIRST + 6) {
            int t = slotId - DEP_FIRST;
            long available = hasVault
                    ? ChestStockHelper.countTierInStorage(vaultLvl, vault.pos(), tiers[t])
                    : countTierInInventory(player, tiers[t]);
            if (available <= 0) { populateItems(player); return; }

            long toDeposit = shiftClick ? available : Math.min(available, 64);
            long value     = toDeposit * tierValues[t];

            if (hasVault) {
                ChestStockHelper.takeTierFromStorage(vaultLvl, vault.pos(), tiers[t], toDeposit);
            } else {
                takeTierFromInventory(player, tiers[t], toDeposit);
            }
            data.addToWallet(player.getUUID(), value);
            player.sendSystemMessage(MessageUtils.success(
                    "Deposited " + toDeposit + "x " + TIER_NAMES[t]
                    + " (" + CurrencyManager.format(value) + ") → wallet."));
            populateItems(player);
            return;
        }

        // ── Withdraw tier tiles (slots 28-33) ─────────────────────────────────
        if (slotId >= WD_FIRST && slotId < WD_FIRST + 6) {
            int t = slotId - WD_FIRST;
            long walletBal = data.getWallet(player.getUUID());
            long canAfford = walletBal / tierValues[t];
            if (canAfford <= 0) { populateItems(player); return; }

            long toWithdraw = shiftClick ? Math.min(canAfford, 64) : 1;
            long cost       = toWithdraw * tierValues[t];

            data.deductFromWallet(player.getUUID(), cost);

            ItemStack give = new ItemStack(tiers[t], (int) toWithdraw);
            if (hasVault) {
                ItemStack leftover = ChestStockHelper.insertItems(vaultLvl, vault.pos(), give);
                if (!leftover.isEmpty()) {
                    // Vault full — overflow to player's hand
                    if (!player.getInventory().add(leftover)) {
                        // Hand also full — refund to wallet
                        long refund = (long) leftover.getCount() * tierValues[t];
                        data.addToWallet(player.getUUID(), refund);
                        player.sendSystemMessage(MessageUtils.error(
                                "Vault and inventory are full! Refunded "
                                + CurrencyManager.format(refund) + " to wallet."));
                    }
                }
            } else {
                player.getInventory().add(give);
            }
            String dest = hasVault ? "vault" : "hand";
            player.sendSystemMessage(MessageUtils.success(
                    "Withdrew " + toWithdraw + "x " + TIER_NAMES[t]
                    + " (" + CurrencyManager.format(cost) + ") → " + dest + "."));
            populateItems(player);
            return;
        }

        // ── Deposit All ───────────────────────────────────────────────────────
        if (slotId == DEP_ALL) {
            long collected = 0;
            if (hasVault) {
                collected = ChestStockHelper.countCurrencyValue(vaultLvl, vault.pos());
                if (collected > 0) ChestStockHelper.takeCurrencyValue(vaultLvl, vault.pos(), collected);
            } else {
                // Sweep all tiers from inventory
                for (int t = 0; t < 6; t++) {
                    long cnt = countTierInInventory(player, tiers[t]);
                    if (cnt > 0) {
                        takeTierFromInventory(player, tiers[t], cnt);
                        collected += cnt * tierValues[t];
                    }
                }
            }
            if (collected == 0) {
                player.sendSystemMessage(MessageUtils.error("No currency items to deposit."));
            } else {
                data.addToWallet(player.getUUID(), collected);
                player.sendSystemMessage(MessageUtils.success(
                        "Deposited all " + CurrencyManager.format(collected) + " → wallet."));
            }
            populateItems(player);
            return;
        }

        // ── Withdraw All ──────────────────────────────────────────────────────
        if (slotId == WD_ALL) {
            long walletBal = data.getWallet(player.getUUID());
            if (walletBal <= 0) {
                player.sendSystemMessage(MessageUtils.error("Your wallet is empty."));
            } else {
                data.deductFromWallet(player.getUUID(), walletBal);
                long leftover;
                if (hasVault) {
                    leftover = ChestStockHelper.insertCurrency(vaultLvl, vault.pos(), walletBal);
                } else {
                    CurrencyManager.give(player, walletBal);
                    leftover = 0;
                }
                if (leftover > 0) {
                    // Vault full — put remainder in hand
                    CurrencyManager.give(player, leftover);
                }
                String dest = hasVault ? "vault" : "hand";
                long deposited = walletBal - leftover;
                player.sendSystemMessage(MessageUtils.success(
                        "Withdrew " + CurrencyManager.format(deposited) + " from wallet → " + dest + "."));
                if (leftover > 0) {
                    player.sendSystemMessage(MessageUtils.success(
                            CurrencyManager.format(leftover) + " could not fit in vault → given to hand."));
                }
            }
            populateItems(player);
            return;
        }

        // ── Misc ──────────────────────────────────────────────────────────────
        if (slotId == DONATE_BTN) {
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                player.sendSystemMessage(MessageUtils.error("You are not in a guild."));
                return;
            }
            // Close personal bank and open the guild bank menu
            player.closeContainer();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal(guild.getName() + " Bank"); }
                        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new GuildBankMenu(id, inv);
                        }
                    },
                    buf -> {}
            );
            return;
        }

        switch (slotId) {
            case CLOSE_L, CLOSE_R -> player.closeContainer();
        }
    }

    // ── Vault resolution helpers ──────────────────────────────────────────────

    private ServerLevel resolveVaultLevel(ServerPlayer player) {
        if (player == null) return null;
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        BankVault vault = GuildSavedData.get(server).getBankVault(player.getUUID());
        if (vault == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation(vault.dimensionId())));
    }

    private BankVault resolveVault(ServerPlayer player) {
        if (player == null) return null;
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        return GuildSavedData.get(server).getBankVault(player.getUUID());
    }

    // ── Inventory helpers (used when no vault is registered) ─────────────────

    private static long countTierInInventory(ServerPlayer player, Item tierItem) {
        long count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(tierItem)) count += stack.getCount();
        }
        return count;
    }

    private static void takeTierFromInventory(ServerPlayer player, Item tierItem, long amount) {
        long remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(tierItem)) continue;
            int take = (int) Math.min(remaining, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
            remaining -= take;
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
