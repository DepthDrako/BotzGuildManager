package com.botzguildz.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * A thin wrapper around {@link AnvilMenu} that hijacks the rename text-field as a
 * search input for the guild bounty item picker.
 *
 * <p>Flow:
 * <ol>
 *   <li>Player opens this menu (anvil-style UI) by clicking the Search button in
 *       {@link BountyItemPickerMenu}.</li>
 *   <li>They type a query; each keystroke calls {@link #setItemName(String)}
 *       server-side, which stores {@link #currentQuery}.</li>
 *   <li>Clicking the output slot (slot 2) "applies" the search and schedules a
 *       reopen of the picker with the filter applied.</li>
 *   <li>Pressing Escape closes the anvil; {@link #removed(Player)} reopens the
 *       picker with no filter (search cancelled).</li>
 * </ol>
 *
 * <p><b>Important:</b> No items pass through this menu.  The fake paper in slot 0
 * is cleared before super.removed() is called so it is never dropped.
 */
public class ItemSearchMenu extends AnvilMenu {

    /** Last text the player typed in the rename field. */
    private String currentQuery = "";

    /** True once the player confirms (clicks output slot); prevents removed() from reopening. */
    private boolean confirmed = false;

    // State forwarded back to the picker
    private final UUID   guildId;
    private final int    tabIndex;
    private final int    catPage;
    private final int    page;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ItemSearchMenu(int id, Inventory inv, UUID guildId, int tabIndex, int catPage, int page) {
        super(id, inv, ContainerLevelAccess.NULL);
        this.guildId  = guildId;
        this.tabIndex = tabIndex;
        this.catPage  = catPage;
        this.page     = page;

        // Seed slot 0 with a paper so the anvil text-field activates on the client
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.setHoverName(Component.literal("Type to filter items...")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false)));
        this.inputSlots.setItem(0, paper);
        this.repairItemCountCost = 0; // show no XP cost
    }

    /** Client-side reconstruction — minimal; server drives all logic. */
    public static ItemSearchMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        boolean hasGuild = buf.readBoolean();
        UUID    guildId  = hasGuild ? buf.readUUID() : null;
        int     tabIdx   = buf.readInt();
        int     catPg    = buf.readInt();
        int     pg       = buf.readInt();
        return new ItemSearchMenu(id, inv, guildId, tabIdx, catPg, pg);
    }

    // ── AnvilMenu overrides ───────────────────────────────────────────────────

    /** Store the query; reset cost to 0 so no XP is ever charged. */
    @Override
    public boolean setItemName(String name) {
        this.currentQuery        = (name == null) ? "" : name.trim();
        this.repairItemCountCost = 0;
        // Don't call super — we don't want to produce a renamed output item
        return true;
    }

    /** Clicking the output slot (2) applies the search and reopens the picker. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == 2 && player instanceof ServerPlayer sp) {
            confirmed = true;
            String query = currentQuery;
            // Clear slots so removed() won't drop items
            inputSlots.setItem(0, ItemStack.EMPTY);
            inputSlots.setItem(1, ItemStack.EMPTY);
            sp.closeContainer(); // triggers removed(); confirmed=true suppresses re-open there
            MinecraftServer server = sp.getServer();
            if (server != null) {
                UUID   gId   = guildId;
                int    tIdx  = tabIndex;
                int    catPg = catPage;
                int    pg    = page;
                server.execute(() -> NetworkHooks.openScreen(sp,
                        new net.minecraft.world.MenuProvider() {
                            @Override public Component getDisplayName() {
                                return Component.literal("Select Item");
                            }
                            @Override public net.minecraft.world.inventory.AbstractContainerMenu
                            createMenu(int cid, Inventory inv2, Player p) {
                                return new BountyItemPickerMenu(cid, inv2, gId, tIdx, catPg, pg, query);
                            }
                        },
                        buf2 -> {
                            buf2.writeBoolean(gId != null);
                            if (gId != null) buf2.writeUUID(gId);
                            buf2.writeInt(tIdx);
                            buf2.writeInt(catPg);
                            buf2.writeInt(pg);
                            buf2.writeUtf(query);
                        }
                ));
            }
            return;
        }
        // All other clicks: no-op (prevent item manipulation)
    }

    /**
     * When the player presses Escape, reopen the picker with no filter.
     * When called after a confirmed-search close, do nothing (already scheduled).
     */
    @Override
    public void removed(Player player) {
        // Prevent the fake paper from dropping into the player's inventory
        inputSlots.setItem(0, ItemStack.EMPTY);
        inputSlots.setItem(1, ItemStack.EMPTY);
        super.removed(player);

        if (!confirmed && player instanceof ServerPlayer sp) {
            UUID  gId   = guildId;
            int   tIdx  = tabIndex;
            int   catPg = catPage;
            int   pg    = page;
            MinecraftServer server = sp.getServer();
            if (server != null) {
                // Escape → reopen picker with no filter (search cancelled)
                server.execute(() -> NetworkHooks.openScreen(sp,
                        new net.minecraft.world.MenuProvider() {
                            @Override public Component getDisplayName() {
                                return Component.literal("Select Item");
                            }
                            @Override public net.minecraft.world.inventory.AbstractContainerMenu
                            createMenu(int cid, Inventory inv2, Player p) {
                                return new BountyItemPickerMenu(cid, inv2, gId, tIdx, catPg, pg, "");
                            }
                        },
                        buf2 -> {
                            buf2.writeBoolean(gId != null);
                            if (gId != null) buf2.writeUUID(gId);
                            buf2.writeInt(tIdx);
                            buf2.writeInt(catPg);
                            buf2.writeInt(pg);
                            buf2.writeUtf("");
                        }
                ));
            }
        }
    }

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
