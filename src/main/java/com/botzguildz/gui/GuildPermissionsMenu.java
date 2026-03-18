package com.botzguildz.gui;

import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildRank;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.RankPermission;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * Guild Permissions Editor GUI — 6-row chest.
 *
 * <pre>
 * Row 0 (0-8):   [glass×4][TITLE:4][glass×4]
 * Row 1 (9-17):  [RankTab×N][glass fills remaining]   ← up to 9 rank tabs
 * Row 2 (18-26): [Perm×9]                              ← 9 permission toggle tiles
 * Row 3 (27-35): [glass×4][INFO:31][glass×4]           ← current rank info
 * Row 4 (36-44): [glass×9]
 * Row 5 (45-53): [CANCEL:45][glass×7][APPLY:53]
 * </pre>
 *
 * Permission tiles are GREEN (has permission) or RED (no permission).
 * Clicking a tile toggles it in a pending copy — NOT written to the guild
 * until the player clicks Apply. Closing via Cancel or any other means
 * discards all pending changes.
 */
public class GuildPermissionsMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    // ── Slot constants ────────────────────────────────────────────────────────

    private static final int TITLE_SLOT     = 4;
    private static final int RANK_TAB_FIRST = 9;    // slots 9-17 (up to 9 rank tabs)
    private static final int PERM_FIRST     = 18;   // slots 18-26 (9 permission tiles)
    private static final int INFO_SLOT      = 31;   // centre of row 3
    private static final int CANCEL_BTN     = 45;
    private static final int APPLY_BTN      = 53;

    // All permissions, in display order (fixed — must match 9 perm slots exactly)
    private static final RankPermission[] ALL_PERMS = RankPermission.values();

    // ── Server-side pending state ─────────────────────────────────────────────

    /** Ordered list of rank names matching the guild's rank list at open time. */
    private final List<String> rankNames = new ArrayList<>();

    /**
     * Pending copy of each rank's permission set.
     * NOT written to the guild until Apply is clicked.
     */
    private final Map<String, EnumSet<RankPermission>> pendingPerms = new LinkedHashMap<>();

    /** Index into {@link #rankNames} for the rank currently being viewed/edited. */
    private int selectedRankIndex = 1; // default: first non-Leader rank

    // ── Constructor ───────────────────────────────────────────────────────────

    public GuildPermissionsMenu(int id, Inventory playerInv) {
        this(id, playerInv, new SimpleContainer(CHEST_SIZE));
    }

    private GuildPermissionsMenu(int id, Inventory playerInv, SimpleContainer chest) {
        super(ModMenuTypes.GUILD_PERMISSIONS_MENU.get(), id, playerInv, chest, ROWS);

        // Initialise pending state from the current guild (server-side only)
        if (playerInv.player instanceof ServerPlayer sp) {
            Guild guild = GuildUtils.getGuildOf(sp);
            if (guild != null) {
                for (GuildRank rank : guild.getRanks()) {
                    rankNames.add(rank.getName());
                    Set<RankPermission> perms = rank.getPermissions();
                    pendingPerms.put(rank.getName(),
                            perms.isEmpty()
                                    ? EnumSet.noneOf(RankPermission.class)
                                    : EnumSet.copyOf(perms));
                }
            }
        }

        // Default selection: first editable rank (skip Leader at index 0)
        selectedRankIndex = rankNames.size() > 1 ? 1 : 0;
        populateItems(playerInv.player);
    }

    public static GuildPermissionsMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        return new GuildPermissionsMenu(id, playerInv, new SimpleContainer(CHEST_SIZE));
    }

    // ── Population ────────────────────────────────────────────────────────────

    public void populateItems(Player player) {
        SimpleContainer chest = (SimpleContainer) this.getContainer();
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());

        // ── Row 0 — title ─────────────────────────────────────────────────────
        ItemStack title = new ItemStack(Items.WRITABLE_BOOK);
        title.setHoverName(styled("Edit Rank Permissions", ChatFormatting.AQUA));
        appendLore(title, lore("Select a rank tab, then click permissions to toggle", ChatFormatting.GRAY));
        appendLore(title, lore("GREEN = granted   RED = not granted", ChatFormatting.DARK_GRAY));
        appendLore(title, lore("Click Apply to save.  Cancel discards all changes.", ChatFormatting.GOLD));
        chest.setItem(TITLE_SLOT, title);

        // ── Row 1 — rank tabs (slots 9-17) ───────────────────────────────────
        for (int i = 0; i < rankNames.size() && i < 9; i++) {
            chest.setItem(RANK_TAB_FIRST + i, makeRankTab(rankNames.get(i), i));
        }

        // ── Row 2 — permission toggles (slots 18-26) ─────────────────────────
        String currentRank = selectedRankIndex < rankNames.size()
                ? rankNames.get(selectedRankIndex) : null;
        boolean locked = currentRank == null || currentRank.equalsIgnoreCase("Leader");
        EnumSet<RankPermission> currentPerms = currentRank != null
                ? pendingPerms.getOrDefault(currentRank, EnumSet.noneOf(RankPermission.class))
                : EnumSet.noneOf(RankPermission.class);

        for (int i = 0; i < ALL_PERMS.length && i < 9; i++) {
            RankPermission perm = ALL_PERMS[i];
            boolean hasPerm = locked || currentPerms.contains(perm);
            chest.setItem(PERM_FIRST + i, makePermTile(perm, hasPerm, locked, currentRank));
        }

        // ── Row 3 — info tile ─────────────────────────────────────────────────
        if (currentRank != null) {
            ItemStack info = new ItemStack(Items.OAK_SIGN);
            info.setHoverName(styled("Editing: " + currentRank, ChatFormatting.WHITE));
            if (locked) {
                appendLore(info, lore("Leader always has all permissions.", ChatFormatting.GRAY));
                appendLore(info, lore("This rank cannot be edited.", ChatFormatting.DARK_GRAY));
            } else {
                int count = currentPerms.size();
                appendLore(info, lore(count + " / " + ALL_PERMS.length + " permissions granted", ChatFormatting.YELLOW));
                appendLore(info, lore("Click a permission tile above to toggle it", ChatFormatting.GRAY));
                appendLore(info, lore("Changes are pending — click Apply to save", ChatFormatting.DARK_GRAY));
            }
            chest.setItem(INFO_SLOT, info);
        }

        // ── Row 5 — Cancel / Apply ────────────────────────────────────────────
        ItemStack cancel = new ItemStack(Items.BARRIER);
        cancel.setHoverName(styled("Cancel", ChatFormatting.RED));
        appendLore(cancel, lore("Discard all pending changes and close", ChatFormatting.GRAY));
        chest.setItem(CANCEL_BTN, cancel);

        ItemStack apply = new ItemStack(Items.EMERALD);
        apply.setHoverName(styled("Apply Changes", ChatFormatting.GREEN));
        appendLore(apply, lore("Save all permission changes to the guild", ChatFormatting.GRAY));
        appendLore(apply, lore("This will take effect immediately", ChatFormatting.DARK_GRAY));
        chest.setItem(APPLY_BTN, apply);

        broadcastChanges();
    }

    // ── Tile builders ─────────────────────────────────────────────────────────

    private ItemStack makeRankTab(String rankName, int index) {
        boolean isLeader   = rankName.equalsIgnoreCase("Leader");
        boolean isSelected = index == selectedRankIndex;

        Item tabItem = isLeader   ? Items.NETHER_STAR
                     : isSelected ? Items.GOLD_INGOT
                     : Items.IRON_INGOT;

        ChatFormatting nameColor = isLeader   ? ChatFormatting.WHITE
                                 : isSelected ? ChatFormatting.GOLD
                                 : ChatFormatting.GRAY;

        ItemStack s = new ItemStack(tabItem);
        s.setHoverName(styled(rankName + (isSelected ? "  ◄" : ""), nameColor));

        if (isLeader) {
            appendLore(s, lore("All permissions — locked", ChatFormatting.DARK_GRAY));
        } else if (isSelected) {
            appendLore(s, lore("Currently editing", ChatFormatting.YELLOW));
        } else {
            appendLore(s, lore("Click to edit this rank", ChatFormatting.DARK_GRAY));
        }

        // Show pending permission count for this tab
        EnumSet<RankPermission> perms = pendingPerms.getOrDefault(rankName, EnumSet.noneOf(RankPermission.class));
        int count = isLeader ? ALL_PERMS.length : perms.size();
        appendLore(s, lore(count + " / " + ALL_PERMS.length + " permissions", ChatFormatting.DARK_GRAY));
        return s;
    }

    private static ItemStack makePermTile(RankPermission perm, boolean hasPerm,
                                          boolean locked, String rankName) {
        Item tileItem = hasPerm ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE;
        ChatFormatting nameColor = hasPerm ? ChatFormatting.GREEN : ChatFormatting.RED;
        String prefix = hasPerm ? "✔  " : "✘  ";

        ItemStack s = new ItemStack(tileItem);
        s.setHoverName(styled(prefix + friendlyName(perm), nameColor));
        appendLore(s, lore(perm.name(), ChatFormatting.DARK_GRAY));

        if (rankName != null) {
            appendLore(s, lore(hasPerm
                    ? "Granted to " + rankName
                    : "Not granted to " + rankName, ChatFormatting.GRAY));
        }

        if (locked) {
            appendLore(s, lore("Cannot be changed — Leader is always all-permissions", ChatFormatting.DARK_GRAY));
        } else {
            appendLore(s, lore("Click to " + (hasPerm ? "revoke" : "grant"), ChatFormatting.YELLOW));
        }
        return s;
    }

    private static String friendlyName(RankPermission p) {
        return switch (p) {
            case INVITE          -> "Invite Members";
            case KICK            -> "Kick Members";
            case MANAGE_BANK     -> "Manage Bank";
            case DECLARE_WAR     -> "Declare War";
            case MANAGE_RANKS    -> "Manage Ranks";
            case MANAGE_UPGRADES -> "Purchase Upgrades";
            case TOGGLE_FF       -> "Toggle Friendly Fire";
            case SET_HOME        -> "Set Guild Home";
            case MANAGE_ALLIES   -> "Manage Allies";
            case MANAGE_BOUNTIES -> "Post Bounties";
            case MANAGE_VAULT    -> "Vault Withdraw";
            case MANAGE_MISSIONS -> "Manage Missions";
        };
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

        // ── Rank tab clicks (slots 9-17) ──────────────────────────────────────
        if (slotId >= RANK_TAB_FIRST && slotId < RANK_TAB_FIRST + 9) {
            int idx = slotId - RANK_TAB_FIRST;
            if (idx < rankNames.size()) {
                selectedRankIndex = idx;
                populateItems(player);
            }
            return;
        }

        // ── Permission toggle clicks (slots 18-26) ────────────────────────────
        if (slotId >= PERM_FIRST && slotId < PERM_FIRST + ALL_PERMS.length) {
            String currentRank = selectedRankIndex < rankNames.size()
                    ? rankNames.get(selectedRankIndex) : null;
            if (currentRank == null || currentRank.equalsIgnoreCase("Leader")) return;

            int permIdx = slotId - PERM_FIRST;
            RankPermission perm = ALL_PERMS[permIdx];
            EnumSet<RankPermission> perms = pendingPerms.computeIfAbsent(
                    currentRank, k -> EnumSet.noneOf(RankPermission.class));

            if (perms.contains(perm)) perms.remove(perm);
            else perms.add(perm);

            populateItems(player);
            return;
        }

        // ── Apply ─────────────────────────────────────────────────────────────
        if (slotId == APPLY_BTN) {
            applyChanges(player);
            player.closeContainer();
            return;
        }

        // ── Cancel ────────────────────────────────────────────────────────────
        if (slotId == CANCEL_BTN) {
            player.sendSystemMessage(MessageUtils.warn("Permission changes discarded."));
            player.closeContainer();
        }
    }

    /** Write the pending permission sets back to the real guild ranks and persist. */
    private void applyChanges(ServerPlayer player) {
        Guild guild = GuildUtils.getGuildOf(player);
        if (guild == null) {
            player.sendSystemMessage(MessageUtils.error("You are no longer in a guild."));
            return;
        }
        if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_RANKS)) {
            player.sendSystemMessage(MessageUtils.error("You no longer have permission to manage ranks."));
            return;
        }

        for (GuildRank rank : guild.getRanks()) {
            if (rank.getName().equalsIgnoreCase("Leader")) continue; // Leader is immutable
            EnumSet<RankPermission> pending = pendingPerms.get(rank.getName());
            if (pending == null) continue;
            // Replace permissions in-place
            rank.getPermissions().clear();
            rank.getPermissions().addAll(pending);
        }

        GuildSavedData.get(player.getServer()).setDirty();
        guild.addLog(player.getName().getString() + " updated rank permissions.");
        player.sendSystemMessage(MessageUtils.success("Rank permissions saved successfully."));
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
