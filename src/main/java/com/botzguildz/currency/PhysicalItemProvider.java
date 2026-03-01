package com.botzguildz.currency;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.data.GuildSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Physical-item currency provider.
 *
 * Currency is a configurable Minecraft item (default: minecraft:gold_ingot).
 * Players must physically hold the item in their inventory to deposit.
 * Balances are stored in GuildSavedData player wallets so they persist
 * even when the item isn't in the inventory (e.g. after earning from kills).
 *
 * Flow:
 *   Earn from kill/login → added to wallet.
 *   Deposit to guild bank → deduct from wallet (or take from inventory if wallet empty).
 *   Withdraw from guild bank → add to inventory (and wallet if no room).
 */
public class PhysicalItemProvider implements ICurrencyProvider {

    @Override
    public long getBalance(ServerPlayer player) {
        // Wallet balance (soft currency, earned from kills/logins) +
        // item count in inventory (physical items the player is carrying)
        GuildSavedData data = GuildSavedData.get(player.getServer());
        long walletBalance  = data.getWallet(player.getUUID());
        long inventoryItems = countItemsInInventory(player);
        return walletBalance + inventoryItems;
    }

    @Override
    public boolean deduct(ServerPlayer player, long amount) {
        GuildSavedData data   = GuildSavedData.get(player.getServer());
        long walletBalance    = data.getWallet(player.getUUID());
        long inventoryItems   = countItemsInInventory(player);
        long total            = walletBalance + inventoryItems;
        if (total < amount) return false;

        // Deduct from wallet first, then from inventory
        long fromWallet = Math.min(walletBalance, amount);
        data.deductFromWallet(player.getUUID(), fromWallet);
        long fromInventory = amount - fromWallet;
        if (fromInventory > 0) removeItemsFromInventory(player, fromInventory);
        return true;
    }

    @Override
    public void give(ServerPlayer player, long amount) {
        // Try to give physical items; overflow goes to wallet
        Item item = getCurrencyItem();
        long remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                int give = (int) Math.min(remaining, item.getMaxStackSize());
                player.getInventory().setItem(i, new ItemStack(item, give));
                remaining -= give;
            } else if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                int space = stack.getMaxStackSize() - stack.getCount();
                int give  = (int) Math.min(remaining, space);
                stack.grow(give);
                remaining -= give;
            }
        }
        // Any overflow goes to the soft wallet
        if (remaining > 0) {
            GuildSavedData.get(player.getServer()).addToWallet(player.getUUID(), remaining);
        }
    }

    @Override
    public String format(long amount) {
        Item item = getCurrencyItem();
        String itemName = ForgeRegistries.ITEMS.getKey(item) != null
                ? ForgeRegistries.ITEMS.getKey(item).getPath().replace("_", " ")
                : "item";
        return amount + " " + capitalize(itemName) + (amount == 1 ? "" : "s");
    }

    @Override
    public String currencyName() {
        return GuildConfig.CURRENCY_NAME.get();
    }

    @Override
    public boolean isAvailable() { return true; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Item getCurrencyItem() {
        String id = GuildConfig.CURRENCY_ITEM.get();
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return net.minecraft.world.item.Items.GOLD_INGOT;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item != null ? item : net.minecraft.world.item.Items.GOLD_INGOT;
    }

    private long countItemsInInventory(ServerPlayer player) {
        Item item = getCurrencyItem();
        return player.getInventory().items.stream()
                .filter(s -> s.is(item))
                .mapToLong(ItemStack::getCount)
                .sum();
    }

    private void removeItemsFromInventory(ServerPlayer player, long amount) {
        Item item = getCurrencyItem();
        long remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int take = (int) Math.min(stack.getCount(), remaining);
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1));
            sb.append(" ");
        }
        return sb.toString().trim();
    }
}
