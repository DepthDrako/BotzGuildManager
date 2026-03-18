package com.botzguildz.currency;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Abstraction layer over the active currency system.
 * Two implementations: PhysicalItemProvider (default) and NumismaticsProvider (auto-detected).
 */
public interface ICurrencyProvider {

    /** @return the player's current personal balance in base currency units. */
    long getBalance(ServerPlayer player);

    /**
     * Deduct an amount from the player's balance.
     * @return true if successful, false if insufficient funds.
     */
    boolean deduct(ServerPlayer player, long amount);

    /** Credit an amount to the player's balance. */
    void give(ServerPlayer player, long amount);

    /** Format a raw amount into a human-readable string (e.g. "2 Crowns, 3 Cogs" or "150 Gold Ingots"). */
    String format(long amount);

    /**
     * Compact format for GUI display — abbreviated denomination labels.
     * Implementations override this; default falls back to {@link #format}.
     * Example (physical): "69GS  1GM  1GC  1GT  5GB"
     */
    default String formatShort(long amount) { return format(amount); }

    /** Display name of the currency, for use in messages (e.g. "Guild Coins", "Spurs"). */
    String currencyName();

    /** @return true if this provider is usable (e.g. Numismatics is actually loaded). */
    boolean isAvailable();

    /** @return an ItemStack representing the currency for display in GUIs. */
    ItemStack getDisplayItem();

    /** @return denomination values from smallest (index 0) to largest (index N). */
    long[] getDenominations();

    /** @return an ItemStack icon for tier {@code tierIndex} (0 = smallest denomination). */
    ItemStack getTierItem(int tierIndex);
}
