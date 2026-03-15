package com.botzguildz.gui;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.RankPermission;
import com.botzguildz.ftb.FTBBridge;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.upgrade.GuildUpgradeDef;
import com.botzguildz.upgrade.UpgradeCategoryDef;
import com.botzguildz.upgrade.UpgradeEffect;
import com.botzguildz.upgrade.UpgradeRegistry;
import com.botzguildz.util.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Guild Upgrades GUI — extends vanilla ChestMenu (6 rows × 9 = 54 chest slots).
 *
 * <pre>
 * Layout (chest area, slots 0–53):
 *   Row 0  [ category tabs at their datapack-defined slots (0–8) ]
 *   Row 1  [ glass ........... Category Header (book at slot 13) ........... glass ]
 *   Rows 2–4  Upgrade items for the active category / page (slots 18–44, up to 27)
 *   Row 5  [ X ][ ◄ ][glass][glass][ Balance ][glass][glass][ ► ][ X ]
 *            45   46  47    48      49         50    51      52   53
 * </pre>
 *
 * The network protocol for opening this screen:
 * <pre>
 *   Server writes: buf.writeUtf(categoryId);  buf.writeInt(page);
 *   Client reads:  String catId = buf.readUtf(); int page = buf.readInt();
 * </pre>
 */
public class UpgradesMenu extends ChestMenu {

    // ── Layout constants ───────────────────────────────────────────────────────

    private static final int ROWS        = 6;
    private static final int CHEST_SIZE  = ROWS * 9; // 54

    private static final int HEADER_SLOT   = 13;
    private static final int UPGRADE_START = 18;
    private static final int UPGRADE_END   = 44;
    private static final int CLOSE_L       = 45;
    private static final int PREV_SLOT     = 46;
    private static final int BALANCE_SLOT  = 49;
    private static final int NEXT_SLOT     = 52;
    private static final int CLOSE_R       = 53;

    // ── State ─────────────────────────────────────────────────────────────────

    private final SimpleContainer chest;

    /** ID of the currently displayed category (matches a UpgradeCategoryDef ID). */
    private String currentCategoryId;

    /** Current page within the category (1-based). */
    private int currentPage;

    // ── Constructors ──────────────────────────────────────────────────────────

    private UpgradesMenu(int id, Inventory playerInv,
                         String categoryId, int page,
                         SimpleContainer chest) {
        super(ModMenuTypes.UPGRADES_MENU.get(), id, playerInv, chest, ROWS);
        this.chest             = chest;
        this.currentCategoryId = categoryId != null ? categoryId : "";
        this.currentPage       = Math.max(1, page);
        populateItems(playerInv.player);
    }

    /** Server-side constructor — called by the MenuProvider in GuildUpgradeCommand. */
    public UpgradesMenu(int id, Inventory playerInv, String categoryId, int page) {
        this(id, playerInv, categoryId, page, new SimpleContainer(CHEST_SIZE));
    }

    /**
     * Client-side factory — registered in ModMenuTypes via IForgeMenuType.create().
     * Called when the client receives the OpenScreen packet.
     */
    public static UpgradesMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        String catId = buf.readUtf();
        int    page  = buf.readInt();
        return new UpgradesMenu(id, playerInv, catId, page, new SimpleContainer(CHEST_SIZE));
    }

    // ── Item population ───────────────────────────────────────────────────────

    /**
     * Rebuilds all 54 chest display slots.
     * Called at construction, on tab/page navigation, and after a successful purchase.
     */
    public void populateItems(Player player) {
        // Step 1 — blank slate
        for (int i = 0; i < CHEST_SIZE; i++) chest.setItem(i, pane());

        UpgradeRegistry reg = UpgradeRegistry.INSTANCE;

        // Step 2 — category tabs in row 0 (each at its defined slot)
        for (UpgradeCategoryDef cat : reg.getCategories()) {
            chest.setItem(cat.getSlot(), makeCategoryTab(cat));
        }

        // Step 3 — header in row 1 (slot 13)
        UpgradeCategoryDef currentCat = reg.getCategoryById(currentCategoryId);
        String catDisplayName = (currentCat != null) ? currentCat.getName() : currentCategoryId;
        ItemStack header = new ItemStack(Items.BOOK);
        header.setHoverName(styledName(catDisplayName + " Upgrades", ChatFormatting.GOLD));
        chest.setItem(HEADER_SLOT, header);

        // Step 4 — upgrade items in rows 2–4
        Guild guild = resolveGuild(player);
        List<GuildUpgradeDef> upgrades = reg.getUpgradesForCategory(currentCategoryId, currentPage);
        for (int i = 0; i < upgrades.size() && (UPGRADE_START + i) <= UPGRADE_END; i++) {
            chest.setItem(UPGRADE_START + i, makeUpgradeItem(upgrades.get(i), guild));
        }

        // Step 5 — navigation bar in row 5
        ItemStack closeBtn = new ItemStack(Items.BARRIER);
        closeBtn.setHoverName(styledName("Close", ChatFormatting.RED));
        chest.setItem(CLOSE_L, closeBtn);
        chest.setItem(CLOSE_R, closeBtn.copy());

        int maxPage = reg.getPageCount(currentCategoryId);
        if (currentPage > 1) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.setHoverName(styledName(
                    "◄ Previous Page (" + (currentPage - 1) + "/" + maxPage + ")",
                    ChatFormatting.YELLOW));
            appendLore(prev, lore("Page " + (currentPage - 1), ChatFormatting.GRAY));
            chest.setItem(PREV_SLOT, prev);
        }
        if (currentPage < maxPage) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.setHoverName(styledName(
                    "Next Page ► (" + (currentPage + 1) + "/" + maxPage + ")",
                    ChatFormatting.YELLOW));
            appendLore(next, lore("Page " + (currentPage + 1), ChatFormatting.GRAY));
            chest.setItem(NEXT_SLOT, next);
        }

        ItemStack bal = new ItemStack(Items.GOLD_INGOT);
        if (guild != null) {
            bal.setHoverName(styledName(
                    "Guild Bank: " + CurrencyManager.format(guild.getAvailableBalance()),
                    ChatFormatting.GOLD));
            appendLore(bal, lore("Click an upgrade to purchase it", ChatFormatting.GRAY));
        } else {
            bal.setHoverName(styledName("Guild Bank", ChatFormatting.GOLD));
        }
        chest.setItem(BALANCE_SLOT, bal);

        // Step 6 — sync to client
        broadcastChanges();
    }

    // ── Item factories ────────────────────────────────────────────────────────

    private ItemStack makeCategoryTab(UpgradeCategoryDef cat) {
        boolean selected = cat.getId().equals(currentCategoryId);
        ItemStack item = new ItemStack(cat.getItem());
        if (selected) {
            item.setHoverName(Component.literal("» " + cat.getName())
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.GREEN)
                            .withBold(true)
                            .withItalic(false)));
            appendLore(item, lore("Currently viewing", ChatFormatting.YELLOW));
        } else {
            item.setHoverName(styledName(cat.getName(), cat.getColor()));
            appendLore(item, lore("Click to browse", ChatFormatting.DARK_GRAY));
        }
        return item;
    }

    private static ItemStack makeUpgradeItem(GuildUpgradeDef upgrade, Guild guild) {
        boolean owned     = guild != null && guild.hasUpgrade(upgrade.getId());
        boolean available = guild != null && upgrade.isAvailableTo(guild);
        boolean canAfford = guild != null && guild.canAfford(upgrade.getCost());

        net.minecraft.world.item.Item icon;
        ChatFormatting nameColor;
        Component statusLine;

        if (owned) {
            icon       = Items.EMERALD;
            nameColor  = ChatFormatting.GREEN;
            statusLine = lore("✔ Already owned", ChatFormatting.GREEN);
        } else if (available && canAfford) {
            icon       = upgrade.getIcon();
            nameColor  = ChatFormatting.YELLOW;
            statusLine = lore("Left-click to purchase", ChatFormatting.YELLOW);
        } else if (available) {
            icon       = Items.ORANGE_DYE;
            nameColor  = ChatFormatting.GOLD;
            statusLine = lore("Insufficient guild bank funds", ChatFormatting.RED);
        } else {
            icon      = Items.GRAY_DYE;
            nameColor = ChatFormatting.DARK_GRAY;
            if (guild != null && guild.getLevel() < upgrade.getRequiredLevel()) {
                statusLine = lore("Needs Guild Lv." + upgrade.getRequiredLevel()
                        + "  (you are Lv." + guild.getLevel() + ")", ChatFormatting.RED);
            } else if (upgrade.getPrerequisiteId() != null) {
                statusLine = lore("Requires: " + upgrade.getPrerequisiteId(), ChatFormatting.RED);
            } else {
                statusLine = lore("Locked", ChatFormatting.RED);
            }
        }

        ItemStack item = new ItemStack(icon);
        item.setHoverName(Component.literal(upgrade.getDisplayName())
                .withStyle(Style.EMPTY.withColor(nameColor).withBold(true).withItalic(false)));

        appendLore(item, lore(upgrade.getDescription(), ChatFormatting.GRAY));
        appendLore(item, lore("────────────────────", ChatFormatting.DARK_GRAY));
        appendLore(item, Component.empty()
                .append(Component.literal("Cost: ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)))
                .append(Component.literal(CurrencyManager.format(upgrade.getCost()))
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false))));
        appendLore(item, Component.empty()
                .append(Component.literal("Required Level: ")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)))
                .append(Component.literal("Guild Lv." + upgrade.getRequiredLevel())
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false))));
        if (upgrade.getPrerequisiteId() != null) {
            appendLore(item, Component.empty()
                    .append(Component.literal("Requires: ")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)))
                    .append(Component.literal(upgrade.getPrerequisiteId())
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE).withItalic(false))));
        }
        appendLore(item, lore("────────────────────", ChatFormatting.DARK_GRAY));
        appendLore(item, statusLine);
        return item;
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
        // ── Category tabs ──────────────────────────────────────────────────────
        for (UpgradeCategoryDef cat : UpgradeRegistry.INSTANCE.getCategories()) {
            if (slotId == cat.getSlot()) {
                currentCategoryId = cat.getId();
                currentPage = 1;
                populateItems(player);
                return;
            }
        }

        // ── Close ──────────────────────────────────────────────────────────────
        if (slotId == CLOSE_L || slotId == CLOSE_R) {
            player.closeContainer();
            return;
        }

        // ── Prev page ──────────────────────────────────────────────────────────
        if (slotId == PREV_SLOT && currentPage > 1) {
            currentPage--;
            populateItems(player);
            return;
        }

        // ── Next page ──────────────────────────────────────────────────────────
        if (slotId == NEXT_SLOT
                && currentPage < UpgradeRegistry.INSTANCE.getPageCount(currentCategoryId)) {
            currentPage++;
            populateItems(player);
            return;
        }

        // ── Upgrade purchase area ──────────────────────────────────────────────
        if (slotId >= UPGRADE_START && slotId <= UPGRADE_END) {
            handlePurchase(player, slotId - UPGRADE_START);
        }
    }

    // ── Purchase logic ────────────────────────────────────────────────────────

    private void handlePurchase(ServerPlayer player, int upgradeIndex) {
        List<GuildUpgradeDef> upgrades = UpgradeRegistry.INSTANCE
                .getUpgradesForCategory(currentCategoryId, currentPage);
        if (upgradeIndex >= upgrades.size()) return; // empty glass pane slot

        GuildUpgradeDef upgrade = upgrades.get(upgradeIndex);
        Guild guild = GuildSavedData.get(player.getServer()).getGuildByPlayer(player.getUUID());

        if (guild == null) {
            player.sendSystemMessage(lore("You are not in a guild.", ChatFormatting.RED));
            return;
        }
        if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_UPGRADES)) {
            player.sendSystemMessage(lore("You don't have permission to purchase upgrades.", ChatFormatting.RED));
            return;
        }
        if (guild.hasUpgrade(upgrade.getId())) {
            player.sendSystemMessage(Component.literal("Your guild already owns: ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(upgrade.getDisplayName()).withStyle(ChatFormatting.YELLOW)));
            return;
        }
        if (!upgrade.isAvailableTo(guild)) {
            String reason = (guild.getLevel() < upgrade.getRequiredLevel())
                    ? "Your guild must be level " + upgrade.getRequiredLevel()
                      + " (currently " + guild.getLevel() + ")."
                    : "You need the '" + upgrade.getPrerequisiteId() + "' upgrade first.";
            player.sendSystemMessage(lore(reason, ChatFormatting.RED));
            return;
        }
        if (!guild.canAfford(upgrade.getCost())) {
            player.sendSystemMessage(Component.literal("Insufficient funds. Need ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(CurrencyManager.format(upgrade.getCost()))
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(", available: ").withStyle(ChatFormatting.RED))
                    .append(Component.literal(CurrencyManager.format(guild.getAvailableBalance()))
                            .withStyle(ChatFormatting.YELLOW)));
            return;
        }

        // All checks passed — complete the purchase
        guild.withdraw(upgrade.getCost());
        guild.addUpgrade(upgrade.getId());
        GuildSavedData.get(player.getServer()).setDirty();

        // FTB Chunks integration — re-apply quotas when a chunk upgrade is purchased via GUI
        String uid = upgrade.getId();
        if (uid.equals("CHUNK_CLAIM_I") || uid.equals("CHUNK_CLAIM_II")
                || uid.equals("CHUNK_FORCE_LOAD")) {
            FTBBridge.applyChunkClaimBonus(guild, player.getServer());
        }

        // Apply one-time effects (GIVE_ITEM, COMMAND)
        applyPurchaseEffects(upgrade, player, guild);

        guild.addLog(player.getName().getString()
                + " purchased upgrade: " + upgrade.getDisplayName() + " (GUI).");
        MessageUtils.broadcastToGuild(guild,
                MessageUtils.success("Guild upgrade purchased: " + upgrade.getDisplayName() + "!"),
                player.getServer());

        populateItems(player);
    }

    /**
     * Handles GIVE_ITEM and COMMAND effects at purchase time.
     * MODIFIER and POTION_EFFECT are passive — they're applied at runtime / on login.
     */
    public static void applyPurchaseEffects(GuildUpgradeDef upgrade,
                                            ServerPlayer buyer, Guild guild) {
        for (UpgradeEffect eff : upgrade.getEffects()) {
            switch (eff.getType()) {
                case GIVE_ITEM -> {
                    ResourceLocation rl = ResourceLocation.tryParse(eff.getItemId());
                    if (rl != null) {
                        net.minecraft.world.item.Item it = ForgeRegistries.ITEMS.getValue(rl);
                        if (it != null) buyer.addItem(new ItemStack(it, eff.getCount()));
                    }
                }
                case COMMAND -> {
                    net.minecraft.commands.CommandSourceStack src =
                            buyer.getServer().createCommandSourceStack();
                    String cmd = eff.getCommand()
                            .replace("%player%", buyer.getName().getString())
                            .replace("%guild%", guild.getName());
                    buyer.getServer().getCommands().performPrefixedCommand(src, cmd);
                }
                default -> { /* MODIFIER / POTION_EFFECT: runtime only */ }
            }
        }
    }

    // ── Required overrides ────────────────────────────────────────────────────

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemStack pane() {
        ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        s.setHoverName(Component.literal(" ").withStyle(Style.EMPTY.withItalic(false)));
        return s;
    }

    private static Component styledName(String text, ChatFormatting color) {
        return Component.literal(text)
                .withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    private static Component lore(String text, ChatFormatting color) {
        return Component.literal(text)
                .withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    private static void appendLore(ItemStack stack, Component line) {
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore;
        if (display.contains("Lore", 9 /* TAG_LIST */)) {
            lore = display.getList("Lore", 8 /* TAG_STRING */);
        } else {
            lore = new ListTag();
            display.put("Lore", lore);
        }
        lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
    }

    /**
     * Server-side guild lookup. Returns null on the client (GuildSavedData is server-only).
     * The server immediately overwrites client-side items via sendAllDataToRemote() on open.
     */
    private static Guild resolveGuild(Player player) {
        if (player instanceof ServerPlayer sp && sp.getServer() != null) {
            return GuildSavedData.get(sp.getServer()).getGuildByPlayer(sp.getUUID());
        }
        return null;
    }

    public String getCurrentCategoryId() { return currentCategoryId; }
    public int    getCurrentPage()       { return currentPage; }
}
