package com.botzguildz.currency;

import net.minecraft.server.level.ServerPlayer;

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

    /** Display name of the currency, for use in messages (e.g. "Guild Coins", "Spurs"). */
    String currencyName();

    /** @return true if this provider is usable (e.g. Numismatics is actually loaded). */
    boolean isAvailable();
}
