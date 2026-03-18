package com.botzguildz.gui;

import com.botzguildz.data.*;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.GuildUtils;
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

import java.util.List;
import java.util.UUID;

/**
 * Guild Missions board — shows up to 3 active missions with shared progress.
 *
 * <pre>
 * Row 0 (0-8):   [glass fill, book header at 4]
 * Row 1 (9-17):  [glass fill, Mission 1 at 13]
 * Row 2 (18-26): [glass fill, Mission 2 at 22]
 * Row 3 (27-35): [glass fill, Mission 3 at 31]
 * Row 4 (36-44): [glass fill]
 * Row 5 (45-53): [glass][Refresh:46][glass][glass][Close:49][glass][glass][glass][glass]
 * </pre>
 */
public class GuildMissionsMenu extends ChestMenu {

    private static final int ROWS       = 6;
    private static final int CHEST_SIZE = ROWS * 9;

    private static final int HEADER_SLOT  = 4;
    private static final int MISSION_1    = 13;
    private static final int MISSION_2    = 22;
    private static final int MISSION_3    = 31;
    private static final int REFRESH_BTN  = 46;
    private static final int CLOSE_BTN   = 49;

    private final UUID guildId;

    // ── Constructors ──────────────────────────────────────────────────────────

    public GuildMissionsMenu(int id, Inventory inv, UUID guildId) {
        this(id, inv, new SimpleContainer(CHEST_SIZE), guildId);
    }

    private GuildMissionsMenu(int id, Inventory inv, SimpleContainer chest, UUID guildId) {
        super(ModMenuTypes.GUILD_MISSIONS_MENU.get(), id, inv, chest, ROWS);
        this.guildId = guildId;
        if (inv.player instanceof ServerPlayer) populateItems(inv.player);
    }

    public static GuildMissionsMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        boolean hasGuild = buf.readBoolean();
        UUID    guildId  = hasGuild ? buf.readUUID() : null;
        return new GuildMissionsMenu(id, inv, new SimpleContainer(CHEST_SIZE), guildId);
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

        // ── Header ────────────────────────────────────────────────────────────
        ItemStack header = new ItemStack(Items.WRITABLE_BOOK);
        header.setHoverName(styled("Guild Missions", ChatFormatting.GOLD));
        appendLore(header, lore("Complete missions for guild XP and coins", ChatFormatting.GRAY));
        appendLore(header, lore("Missions reset every 24 hours", ChatFormatting.DARK_GRAY));
        chest.setItem(HEADER_SLOT, header);

        // ── Mission tiles ─────────────────────────────────────────────────────
        GuildMissionsData missionsData = GuildMissionsData.get(server);
        List<GuildMissionEntry> missions = missionsData.getOrGenerateMissions(guild);

        int[] slots = {MISSION_1, MISSION_2, MISSION_3};
        for (int i = 0; i < 3; i++) {
            if (i < missions.size()) {
                chest.setItem(slots[i], buildMissionTile(missions.get(i)));
            } else {
                ItemStack empty = new ItemStack(Items.GRAY_STAINED_GLASS);
                empty.setHoverName(styled("No Mission", ChatFormatting.DARK_GRAY));
                appendLore(empty, lore("Refreshing soon...", ChatFormatting.DARK_GRAY));
                chest.setItem(slots[i], empty);
            }
        }

        // ── Refresh button ─────────────────────────────────────────────────────
        boolean canRefresh = guild.hasPermission(player.getUUID(), RankPermission.MANAGE_MISSIONS);
        ItemStack refresh = new ItemStack(Items.CLOCK);
        if (canRefresh) {
            refresh.setHoverName(styled("Force Refresh Missions", ChatFormatting.YELLOW));
            appendLore(refresh, lore("Generates new missions immediately", ChatFormatting.GRAY));
            appendLore(refresh, lore("(Requires MANAGE_MISSIONS permission)", ChatFormatting.DARK_GRAY));
        } else {
            refresh.setHoverName(styled("Refresh Missions", ChatFormatting.DARK_GRAY));
            appendLore(refresh, lore("Missions auto-refresh every 24h", ChatFormatting.DARK_GRAY));
            appendLore(refresh, lore("Requires MANAGE_MISSIONS permission to force", ChatFormatting.DARK_GRAY));
        }
        chest.setItem(REFRESH_BTN, refresh);

        // ── Close button ──────────────────────────────────────────────────────
        ItemStack close = new ItemStack(Items.BARRIER);
        close.setHoverName(styled("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_BTN, close);

        broadcastChanges();
    }

    private static ItemStack buildMissionTile(GuildMissionEntry m) {
        ItemStack icon = switch (m.getType()) {
            case KILL    -> new ItemStack(Items.IRON_SWORD);
            case MINE    -> new ItemStack(Items.IRON_PICKAXE);
            case COLLECT -> new ItemStack(Items.CHEST);
        };

        String statusColor = m.isCompleted() ? "§a" : "";
        icon.setHoverName(Component.literal(statusColor + m.getType().label + ": " + m.getDisplayName())
                .withStyle(Style.EMPTY.withColor(m.isCompleted() ? ChatFormatting.GREEN : ChatFormatting.GOLD)
                        .withItalic(false)));

        appendLore(icon, lore("Progress:  " + m.progressBar(), ChatFormatting.WHITE));
        appendLore(icon, lore(" ", ChatFormatting.WHITE));
        appendLore(icon, lore("Rewards:", ChatFormatting.YELLOW));
        appendLore(icon, lore("  Guild Bank:  +" + m.getGuildReward() + " coins", ChatFormatting.GREEN));
        appendLore(icon, lore("  Per Contributor: +" + m.getPlayerReward() + " coins", ChatFormatting.GREEN));
        appendLore(icon, lore(" ", ChatFormatting.WHITE));
        if (!m.isCompleted()) {
            appendLore(icon, lore("Expires in: " + m.timeRemaining(), ChatFormatting.DARK_GRAY));
            appendLore(icon, lore("Contributors: " + m.getContributions().size(), ChatFormatting.DARK_GRAY));
        } else {
            appendLore(icon, lore("✔ COMPLETED", ChatFormatting.GREEN));
        }
        return icon;
    }

    // ── Click handling ────────────────────────────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= CHEST_SIZE) { super.clicked(slotId, button, clickType, player); return; }
        if (!(player instanceof ServerPlayer sp)) return;

        if (slotId == CLOSE_BTN) { sp.closeContainer(); return; }

        if (slotId == REFRESH_BTN) {
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            Guild guild = GuildSavedData.get(server).getGuildById(guildId);
            if (guild == null) return;
            if (!guild.hasPermission(sp.getUUID(), RankPermission.MANAGE_MISSIONS)) {
                sp.sendSystemMessage(MessageUtils.error("You don't have permission to refresh missions."));
                return;
            }
            // Force clear active missions and regenerate
            GuildMissionsData md = GuildMissionsData.get(server);
            md.generateMissionsForGuild(guild);
            sp.sendSystemMessage(MessageUtils.success("Guild missions refreshed!"));
            populateItems(sp);
            return;
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
