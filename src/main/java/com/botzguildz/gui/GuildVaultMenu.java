package com.botzguildz.gui;

import com.botzguildz.data.*;
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

import java.util.UUID;

/**
 * Guild shared item vault — 45 slots (rows 0-4) plus a navigation bar.
 *
 * <pre>
 * Rows 0-4 (0-44):  Vault item slots (glass pane when empty)
 * Row 5 (45-53):    [Deposit:45][glass][glass][glass][Info:49][glass][glass][glass][Close:53]
 * </pre>
 *
 * <p>Deposit:  click the Deposit button with an item in your main hand.
 * <p>Withdraw: left-click any occupied slot (requires {@link RankPermission#MANAGE_VAULT}).
 * <p>Take half: right-click an occupied slot (requires MANAGE_VAULT).
 */
public class GuildVaultMenu extends ChestMenu {

    private static final int ROWS        = 6;
    private static final int CHEST_SIZE  = ROWS * 9;
    private static final int VAULT_SLOTS = GuildVaultData.VAULT_SIZE; // 45

    private static final int DEPOSIT_BTN = 45;
    private static final int INFO_SLOT   = 49;
    private static final int CLOSE_BTN   = 53;

    private final UUID guildId;

    // ── Constructors ──────────────────────────────────────────────────────────

    public GuildVaultMenu(int id, Inventory inv, UUID guildId) {
        this(id, inv, new SimpleContainer(CHEST_SIZE), guildId);
    }

    private GuildVaultMenu(int id, Inventory inv, SimpleContainer chest, UUID guildId) {
        super(ModMenuTypes.GUILD_VAULT_MENU.get(), id, inv, chest, ROWS);
        this.guildId = guildId;
        if (inv.player instanceof ServerPlayer) populateItems(inv.player);
    }

    public static GuildVaultMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        boolean hasGuild = buf.readBoolean();
        UUID    guildId  = hasGuild ? buf.readUUID() : null;
        return new GuildVaultMenu(id, inv, new SimpleContainer(CHEST_SIZE), guildId);
    }

    // ── Population ────────────────────────────────────────────────────────────

    private void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());

        MinecraftServer server = player.getServer();
        if (server == null) return;

        GuildSavedData data  = GuildSavedData.get(server);
        Guild          guild = data.getGuildById(guildId);
        if (guild == null) return;

        boolean canWithdraw = guild.hasPermission(player.getUUID(), RankPermission.MANAGE_VAULT);
        GuildVaultData vaultData = GuildVaultData.get(server);

        // ── Vault item slots (0-44) ───────────────────────────────────────────
        for (int i = 0; i < VAULT_SLOTS; i++) {
            ItemStack vaultItem = vaultData.getSlot(guildId, i);
            if (!vaultItem.isEmpty()) {
                ItemStack display = vaultItem.copy();
                if (canWithdraw) {
                    appendLore(display, lore("Left-click to take stack", ChatFormatting.YELLOW));
                    appendLore(display, lore("Right-click to take half", ChatFormatting.YELLOW));
                } else {
                    appendLore(display, lore("No withdraw permission", ChatFormatting.DARK_RED));
                }
                chest.setItem(i, display);
            } else {
                // Empty slot — show deposit hint
                ItemStack empty = pane();
                appendLore(empty, lore("Empty — deposit an item here", ChatFormatting.DARK_GRAY));
                chest.setItem(i, empty);
            }
        }

        // ── Deposit button ────────────────────────────────────────────────────
        ItemStack depositBtn = new ItemStack(Items.HOPPER);
        depositBtn.setHoverName(styled("Deposit Item", ChatFormatting.GREEN));
        appendLore(depositBtn, lore("Hold an item in your main hand", ChatFormatting.GRAY));
        appendLore(depositBtn, lore("then click this button to deposit it.", ChatFormatting.GRAY));
        chest.setItem(DEPOSIT_BTN, depositBtn);

        // ── Info ──────────────────────────────────────────────────────────────
        int used = vaultData.usedSlots(guildId);
        ItemStack info = new ItemStack(Items.WRITABLE_BOOK);
        info.setHoverName(styled(guild.getName() + " Vault", ChatFormatting.GOLD));
        appendLore(info, lore("Slots: " + used + " / " + VAULT_SLOTS, ChatFormatting.GRAY));
        appendLore(info, lore("All members can deposit.", ChatFormatting.DARK_GRAY));
        appendLore(info, lore("MANAGE_VAULT permission to withdraw.", ChatFormatting.DARK_GRAY));
        chest.setItem(INFO_SLOT, info);

        // ── Close ─────────────────────────────────────────────────────────────
        ItemStack close = new ItemStack(Items.BARRIER);
        close.setHoverName(styled("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_BTN, close);

        broadcastChanges();
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= CHEST_SIZE) { super.clicked(slotId, button, clickType, player); return; }
        if (!(player instanceof ServerPlayer sp)) return;

        MinecraftServer server = sp.getServer();
        if (server == null) return;

        GuildSavedData data  = GuildSavedData.get(server);
        Guild          guild = data.getGuildById(guildId);
        if (guild == null) return;

        // ── Close ─────────────────────────────────────────────────────────────
        if (slotId == CLOSE_BTN) { sp.closeContainer(); return; }

        // ── Deposit button ────────────────────────────────────────────────────
        if (slotId == DEPOSIT_BTN) {
            ItemStack hand = sp.getMainHandItem();
            if (hand.isEmpty()) {
                sp.sendSystemMessage(MessageUtils.error("Hold an item in your main hand to deposit it."));
                return;
            }
            GuildVaultData vaultData = GuildVaultData.get(server);
            ItemStack remainder = vaultData.addItem(guildId, hand.copy());
            int deposited = hand.getCount() - remainder.getCount();
            if (deposited > 0) {
                hand.shrink(deposited);
                sp.sendSystemMessage(MessageUtils.success(
                        "Deposited " + deposited + "x " + hand.getHoverName().getString() + " into the guild vault."));
                // Award a small contribution point per item deposited
                guild.addContribution(sp.getUUID(), deposited);
                data.setDirty();
                populateItems(sp);
            } else {
                sp.sendSystemMessage(MessageUtils.error("The guild vault is full!"));
            }
            return;
        }

        // ── Vault slots (0-44) ────────────────────────────────────────────────
        if (slotId < VAULT_SLOTS) {
            GuildVaultData vaultData = GuildVaultData.get(server);
            ItemStack vaultItem = vaultData.getSlot(guildId, slotId);
            if (vaultItem.isEmpty()) {
                // Click on empty slot — deposit mainhand item into this specific slot
                ItemStack hand = sp.getMainHandItem();
                if (!hand.isEmpty()) {
                    ItemStack toDeposit = hand.copy();
                    vaultData.setSlot(guildId, slotId, toDeposit);
                    sp.sendSystemMessage(MessageUtils.success(
                            "Deposited " + hand.getHoverName().getString() + " into vault slot " + (slotId + 1) + "."));
                    hand.setCount(0);
                    guild.addContribution(sp.getUUID(), toDeposit.getCount());
                    data.setDirty();
                    populateItems(sp);
                }
                return;
            }

            // Non-empty slot — withdrawal requires permission
            if (!guild.hasPermission(sp.getUUID(), RankPermission.MANAGE_VAULT)) {
                sp.sendSystemMessage(MessageUtils.error("You don't have permission to withdraw from the guild vault."));
                return;
            }

            // Left-click = take full stack, right-click = take half
            ItemStack toGive;
            if (button == 1 && clickType == ClickType.PICKUP) {
                // Right-click: take half
                int half = Math.max(1, vaultItem.getCount() / 2);
                toGive = vaultItem.copy();
                toGive.setCount(half);
                vaultItem.shrink(half);
                vaultData.setSlot(guildId, slotId, vaultItem.isEmpty() ? ItemStack.EMPTY : vaultItem);
            } else {
                // Left-click: take all
                toGive = vaultItem.copy();
                vaultData.setSlot(guildId, slotId, ItemStack.EMPTY);
            }

            // Add to player inventory
            if (!sp.getInventory().add(toGive)) {
                // Inventory full — drop at feet
                sp.drop(toGive, false);
            }
            sp.sendSystemMessage(MessageUtils.info(
                    "Withdrew " + toGive.getCount() + "x " + toGive.getHoverName().getString() + " from the guild vault."));
            populateItems(sp);
        }
    }

    // ── Boilerplate ───────────────────────────────────────────────────────────

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

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
