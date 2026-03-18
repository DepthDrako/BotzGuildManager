package com.botzguildz.util;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Utilities for reading and withdrawing items from any container block at a
 * given world position.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Vanilla double-chest — merged via {@link ChestBlock#getContainer}, wrapped as
 *       {@link IItemHandler} so both halves are visible at once.
 *   <li>{@link ForgeCapabilities#ITEM_HANDLER} capability on the block entity — covers
 *       nearly all modded storage (Iron Chests, Sophisticated Storage, Storage Drawers,
 *       Quark variants, Mekanism bins, etc.).
 *   <li>{@link Container} instance fallback — barrels, hoppers, and any vanilla
 *       container that does not expose the capability directly.
 * </ol>
 */
public final class ChestStockHelper {

    private ChestStockHelper() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Count how many items matching {@code template} are available in the
     * storage block at {@code pos}.  Matching uses exact item type + NBT
     * ({@link ItemStack#isSameItemSameTags}).
     *
     * @return total matching item count, or 0 if no storage is found
     */
    public static int countStock(ServerLevel level, BlockPos pos, ItemStack template) {
        IItemHandler handler = getItemHandler(level, pos);
        if (handler == null) return 0;
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slot = handler.getStackInSlot(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, template))
                total += slot.getCount();
        }
        return total;
    }

    /**
     * Remove up to {@code amount} items matching {@code template} from the
     * storage block at {@code pos}.
     *
     * @return the number of items actually removed
     */
    public static int takeStock(ServerLevel level, BlockPos pos, ItemStack template, int amount) {
        IItemHandler handler = getItemHandler(level, pos);
        if (handler == null) return 0;
        int remaining = amount;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack slot = handler.getStackInSlot(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, template)) {
                ItemStack extracted = handler.extractItem(i, remaining, false);
                remaining -= extracted.getCount();
            }
        }
        return amount - remaining;
    }

    /**
     * Returns {@code true} if the block at {@code pos} is a usable storage block:
     * not on the blacklist AND exposes a valid {@link IItemHandler}.
     * Used by {@code /bank vault set} and {@code /shop setblock} validation.
     */
    public static boolean isValidStorage(ServerLevel level, BlockPos pos) {
        if (pos == null) return false;
        BlockState state = level.getBlockState(pos);
        if (isBlacklisted(state)) return false;
        return getItemHandler(level, pos) != null;
    }

    // ── Currency helpers (guild-tier items) ───────────────────────────────────

    /**
     * Count the total guild-currency value of all tier items present in the
     * storage block at {@code pos}.
     */
    public static long countCurrencyValue(ServerLevel level, BlockPos pos) {
        IItemHandler handler = getItemHandler(level, pos);
        if (handler == null) return 0;
        Item[] items = tierItems();
        long[] values = ModItems.TIER_VALUES;
        long total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            for (int tier = 0; tier < items.length; tier++) {
                if (stack.is(items[tier])) {
                    total += (long) stack.getCount() * values[tier];
                    break;
                }
            }
        }
        return total;
    }

    /**
     * Remove exactly {@code amount} worth of guild-currency items from the
     * storage block at {@code pos}.  Collects from the highest denomination
     * down, then inserts change back.
     *
     * <p>Precondition: caller must have verified {@link #countCurrencyValue} >= amount.
     *
     * @return the amount actually removed (equals {@code amount} if vault held enough)
     */
    public static long takeCurrencyValue(ServerLevel level, BlockPos pos, long amount) {
        IItemHandler handler = getItemHandler(level, pos);
        if (handler == null) return 0;
        Item[] items = tierItems();
        long[] values = ModItems.TIER_VALUES;

        // Collect from highest denomination down
        long collected = 0;
        for (int tier = items.length - 1; tier >= 0 && collected < amount; tier--) {
            for (int slot = 0; slot < handler.getSlots() && collected < amount; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !stack.is(items[tier])) continue;
                ItemStack extracted = handler.extractItem(slot, stack.getCount(), false);
                collected += (long) extracted.getCount() * values[tier];
            }
        }

        // Give back change (slots just freed, so insertion will always succeed)
        long change = collected - amount;
        if (change > 0) insertCurrency(level, pos, change);

        return amount;
    }

    /**
     * Insert guild-currency items worth {@code amount} into the storage block
     * at {@code pos}, using optimal denominations (highest first).
     *
     * @return the portion of {@code amount} that could NOT be inserted
     *         (storage full), which the caller should give to the player instead
     */
    public static long insertCurrency(ServerLevel level, BlockPos pos, long amount) {
        IItemHandler handler = getItemHandler(level, pos);
        if (handler == null) return amount;
        Item[] items = tierItems();
        long[] values = ModItems.TIER_VALUES;

        long leftover = amount;
        for (int tier = items.length - 1; tier >= 0 && leftover > 0; tier--) {
            long count = leftover / values[tier];
            if (count == 0) continue;

            long inserted = 0;
            while (inserted < count) {
                int batch = (int) Math.min(count - inserted, 64);
                ItemStack toInsert = new ItemStack(items[tier], batch);
                for (int slot = 0; slot < handler.getSlots() && !toInsert.isEmpty(); slot++) {
                    toInsert = handler.insertItem(slot, toInsert, false);
                }
                long batchInserted = batch - toInsert.getCount();
                inserted += batchInserted;
                if (!toInsert.isEmpty()) break; // vault full at this tier
            }

            leftover -= inserted * values[tier];
        }
        return leftover;
    }

    // ── Per-tier storage helpers ──────────────────────────────────────────────

    /**
     * Count how many items of exactly {@code tierItem} are in the storage block.
     */
    public static long countTierInStorage(ServerLevel level, BlockPos pos, Item tierItem) {
        IItemHandler handler = getItemHandler(level, pos);
        if (handler == null) return 0;
        long count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(tierItem)) count += stack.getCount();
        }
        return count;
    }

    /**
     * Extract up to {@code amount} items of {@code tierItem} from the storage block.
     * @return how many items were actually taken
     */
    public static long takeTierFromStorage(ServerLevel level, BlockPos pos, Item tierItem, long amount) {
        IItemHandler handler = getItemHandler(level, pos);
        if (handler == null) return 0;
        long remaining = amount;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !stack.is(tierItem)) continue;
            int take = (int) Math.min(remaining, stack.getCount());
            handler.extractItem(slot, take, false);
            remaining -= take;
        }
        return amount - remaining;
    }

    /**
     * Insert an {@link ItemStack} into the storage block at {@code pos}.
     * @return whatever could not be inserted (empty stack if everything fit)
     */
    public static ItemStack insertItems(ServerLevel level, BlockPos pos, ItemStack stack) {
        IItemHandler handler = getItemHandler(level, pos);
        if (handler == null) return stack;
        for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++) {
            stack = handler.insertItem(slot, stack, false);
        }
        return stack;
    }

    // ── Tier table shorthand ──────────────────────────────────────────────────

    private static Item[] tierItems() {
        return new Item[]{
                ModItems.GUILD_BIT.get(),   ModItems.GUILD_CHIP.get(),
                ModItems.GUILD_TOKEN.get(), ModItems.GUILD_COIN.get(),
                ModItems.GUILD_MARK.get(),  ModItems.GUILD_SEAL.get()
        };
    }

    // ── Internal resolution ───────────────────────────────────────────────────

    /**
     * Resolve the best available {@link IItemHandler} for the block at {@code pos}.
     * Returns {@code null} if the block is on the configured storage blacklist.
     *
     * <ul>
     *   <li>Vanilla double-chest → merged {@link Container} wrapped in {@link InvWrapper}
     *   <li>Modded storage → {@link ForgeCapabilities#ITEM_HANDLER} from the block entity
     *   <li>Vanilla single container → {@link Container} wrapped in {@link InvWrapper}
     * </ul>
     */
    private static IItemHandler getItemHandler(ServerLevel level, BlockPos pos) {
        if (pos == null) return null;
        BlockState state = level.getBlockState(pos);

        // ── Blacklist check ───────────────────────────────────────────────────
        if (isBlacklisted(state)) return null;

        // ── 1. Vanilla double-chest ───────────────────────────────────────────
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            Container merged = ChestBlock.getContainer(chestBlock, state, level, pos, true);
            if (merged != null) return new InvWrapper(merged);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        // ── 2. IItemHandler capability (most modded storage blocks) ───────────
        IItemHandler cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (cap != null) return cap;

        // ── 3. Vanilla Container fallback (barrel, hopper, etc.) ─────────────
        if (be instanceof Container c) return new InvWrapper(c);

        return null;
    }

    /**
     * Returns {@code true} if the block is on the configured
     * {@code economy.shopStorageBlacklist}.
     *
     * <p>Each entry is either an exact registry ID ({@code "minecraft:furnace"}) or a
     * mod-prefix wildcard ({@code "somemod:*"}) that blocks every block from that mod.
     */
    private static boolean isBlacklisted(BlockState state) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) return false;
        String blockStr = blockId.toString();       // e.g. "minecraft:furnace"
        String namespace = blockId.getNamespace();  // e.g. "minecraft"

        for (String entry : GuildConfig.SHOP_STORAGE_BLACKLIST.get()) {
            if (entry == null || entry.isBlank()) continue;
            if (entry.endsWith(":*")) {
                // Wildcard: block every block whose namespace matches
                String ns = entry.substring(0, entry.length() - 2);
                if (namespace.equals(ns)) return true;
            } else {
                if (blockStr.equals(entry)) return true;
            }
        }
        return false;
    }
}
