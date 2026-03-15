package com.botzguildz.currency;

import com.botzguildz.BotzGuildz;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Central access point for all currency operations.
 * Automatically selects Numismatics or physical-item mode at startup.
 */
public class CurrencyManager {

    private static ICurrencyProvider provider = null;

    /** Called once during FMLCommonSetupEvent to detect and set the provider. */
    public static void init() {
        if (ModList.get().isLoaded("numismatics")) {
            try {
                NumismaticsProvider np = new NumismaticsProvider();
                if (np.isAvailable()) {
                    provider = np;
                    BotzGuildz.LOGGER.info("[BotzGuildz] Currency: Using Create: Numismatics.");
                    return;
                }
            } catch (Exception e) {
                BotzGuildz.LOGGER.warn("[BotzGuildz] Numismatics detected but failed to load provider: {}", e.getMessage());
            }
        }
        provider = new PhysicalItemProvider();
        BotzGuildz.LOGGER.info("[BotzGuildz] Currency: Using physical item mode.");
    }

    public static ICurrencyProvider get() {
        if (provider == null) provider = new PhysicalItemProvider(); // Safety fallback
        return provider;
    }

    // ── Convenience delegates ─────────────────────────────────────────────────

    public static long getBalance(ServerPlayer player)              { return get().getBalance(player); }
    public static boolean deduct(ServerPlayer player, long amount)  { return get().deduct(player, amount); }
    public static void give(ServerPlayer player, long amount)       { get().give(player, amount); }
    public static String format(long amount)                        { return get().format(amount); }
    public static String currencyName()                             { return get().currencyName(); }

    public static boolean isNumismatics() { return provider instanceof NumismaticsProvider; }
}
