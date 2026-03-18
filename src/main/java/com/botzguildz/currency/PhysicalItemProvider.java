package com.botzguildz.currency;

import com.botzguildz.data.GuildSavedData;
import com.botzguildz.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Physical-item currency provider using the six tiered guild currency items.
 *
 * Denomination table (ascending):
 *   Guild Bit    = 1
 *   Guild Chip   = 8
 *   Guild Token  = 64
 *   Guild Coin   = 512
 *   Guild Mark   = 4 096
 *   Guild Seal   = 32 768
 *
 * Physical flow:
 *   getBalance  — inventory value + wallet balance
 *   deduct      — drain wallet first, then remove physical items (give change back)
 *   give        — fill inventory from highest denomination downward; overflow → wallet
 */
public class PhysicalItemProvider implements ICurrencyProvider {

    // ── Tier table (index 0 = lowest tier) ───────────────────────────────────

    private static Item[] tierItems() {
        return new Item[] {
                ModItems.GUILD_BIT.get(),
                ModItems.GUILD_CHIP.get(),
                ModItems.GUILD_TOKEN.get(),
                ModItems.GUILD_COIN.get(),
                ModItems.GUILD_MARK.get(),
                ModItems.GUILD_SEAL.get()
        };
    }

    private static final long[] TIER_VALUES = ModItems.TIER_VALUES;

    // ── ICurrencyProvider ─────────────────────────────────────────────────────

    @Override
    public long getBalance(ServerPlayer player) {
        GuildSavedData data = GuildSavedData.get(player.getServer());
        long walletBalance  = data.getWallet(player.getUUID());
        long inventoryValue = countInventoryValue(player);
        return walletBalance + inventoryValue;
    }

    @Override
    public boolean deduct(ServerPlayer player, long amount) {
        GuildSavedData data  = GuildSavedData.get(player.getServer());
        long wallet          = data.getWallet(player.getUUID());
        long inventoryValue  = countInventoryValue(player);
        if (wallet + inventoryValue < amount) return false;

        // Drain wallet first
        long fromWallet = Math.min(wallet, amount);
        if (fromWallet > 0) data.deductFromWallet(player.getUUID(), fromWallet);

        long fromInventory = amount - fromWallet;
        if (fromInventory > 0) removeInventoryValue(player, fromInventory);

        return true;
    }

    @Override
    public void give(ServerPlayer player, long amount) {
        Item[] items = tierItems();
        long remaining = amount;

        // Give from highest denomination down
        for (int tier = items.length - 1; tier >= 0 && remaining > 0; tier--) {
            long count = remaining / TIER_VALUES[tier];
            if (count == 0) continue;
            remaining %= TIER_VALUES[tier];

            // Fill into inventory stacks of 64
            while (count > 0) {
                int batch = (int) Math.min(count, 64);
                ItemStack give = new ItemStack(items[tier], batch);
                if (!player.getInventory().add(give)) {
                    // Inventory full — convert leftover to value and overflow to wallet
                    remaining += (long) give.getCount() * TIER_VALUES[tier];
                    break;
                }
                count -= batch;
            }
        }

        // Any un-deliverable value goes to the soft wallet
        if (remaining > 0) {
            GuildSavedData.get(player.getServer()).addToWallet(player.getUUID(), remaining);
        }
    }

    @Override
    public String format(long amount) {
        if (amount == 0) return "0 Guild Bits";
        Item[] items = tierItems();
        StringBuilder sb = new StringBuilder();
        long remaining = amount;

        for (int tier = items.length - 1; tier >= 0 && remaining > 0; tier--) {
            long count = remaining / TIER_VALUES[tier];
            if (count == 0) continue;
            remaining %= TIER_VALUES[tier];

            if (sb.length() > 0) sb.append(", ");
            sb.append(count).append(" ").append(tierName(tier));
            if (count != 1) sb.append("s");
        }

        return sb.toString();
    }

    private static final String[] TIER_ABBR = { "GB", "GCh", "GT", "GC", "GM", "GS" };

    @Override
    public String formatShort(long amount) {
        if (amount == 0) return "0 GB";
        StringBuilder sb = new StringBuilder();
        long rem = amount;
        for (int t = TIER_ABBR.length - 1; t >= 0; t--) {
            long count = rem / TIER_VALUES[t];
            if (count == 0) continue;
            rem %= TIER_VALUES[t];
            if (sb.length() > 0) sb.append("  ");
            sb.append(count).append(TIER_ABBR[t]);
        }
        return sb.toString();
    }

    @Override
    public String currencyName() {
        return "Guild Coins";
    }

    @Override
    public boolean isAvailable() { return true; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Count the total currency value of all tiered items in the player's inventory.
     */
    private long countInventoryValue(ServerPlayer player) {
        Item[] items = tierItems();
        long total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            for (int tier = 0; tier < items.length; tier++) {
                if (stack.is(items[tier])) {
                    total += (long) stack.getCount() * TIER_VALUES[tier];
                    break;
                }
            }
        }
        return total;
    }

    /**
     * Remove exactly {@code amount} value of currency from the player's inventory.
     * Uses a "collect all → give back change" strategy to ensure correctness
     * regardless of which denominations the player holds.
     */
    private void removeInventoryValue(ServerPlayer player, long amount) {
        Item[] items = tierItems();

        // Collect all currency stacks (highest first — minimise items touched)
        long collected = 0;
        for (int tier = items.length - 1; tier >= 0; tier--) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isEmpty() || !stack.is(items[tier])) continue;

                long stackValue = (long) stack.getCount() * TIER_VALUES[tier];
                collected += stackValue;
                player.getInventory().setItem(slot, ItemStack.EMPTY);

                if (collected >= amount) break;
            }
            if (collected >= amount) break;
        }

        // Give back the change in optimal denominations
        long change = collected - amount;
        if (change > 0) give(player, change);
    }

    @Override
    public ItemStack getDisplayItem() {
        return new ItemStack(ModItems.GUILD_COIN.get());
    }

    @Override
    public long[] getDenominations() {
        return ModItems.TIER_VALUES;
    }

    @Override
    public ItemStack getTierItem(int tierIndex) {
        Item[] items = tierItems();
        if (tierIndex < 0 || tierIndex >= items.length) return new ItemStack(Items.GOLD_NUGGET);
        return new ItemStack(items[tierIndex]);
    }

    /** Human-readable tier name for format(). */
    private static String tierName(int tier) {
        return switch (tier) {
            case 0 -> "Guild Bit";
            case 1 -> "Guild Chip";
            case 2 -> "Guild Token";
            case 3 -> "Guild Coin";
            case 4 -> "Guild Mark";
            case 5 -> "Guild Seal";
            default -> "Guild Coin";
        };
    }
}
