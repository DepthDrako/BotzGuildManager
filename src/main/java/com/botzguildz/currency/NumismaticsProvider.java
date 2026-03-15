package com.botzguildz.currency;

import com.botzguildz.BotzGuildz;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Create: Numismatics currency provider — compiled WITHOUT a direct dependency on Numismatics.
 *
 * All Numismatics API calls are made via reflection so the mod compiles and runs even when
 * Numismatics is not installed. CurrencyManager.init() is responsible for only instantiating
 * this class after confirming Numismatics is loaded (via ModList).
 *
 * Balances are in SPURS (the base unit).
 * Coin values: SPUR=1, BEVEL=8, SPROCKET=16, COG=64, CROWN=512, SUN=4096.
 */
public class NumismaticsProvider implements ICurrencyProvider {

    private static final String NUM_CLASS = "dev.ithundxr.createnumismatics.Numismatics";
    private static final String ACC_CLASS = "dev.ithundxr.createnumismatics.content.backend.BankAccount";

    // Resolved once, lazily, on first use
    private static Object  bankManager = null;
    private static Method  mGetAccount = null;
    private static Method  mGetBalance = null;
    private static Method  mDeduct     = null;
    private static Method  mDeposit    = null;
    private static boolean apiResolved = false;
    private static boolean usePlayerArg = true; // true = getAccount(ServerPlayer), false = getAccount(UUID)

    /** Resolve all reflection handles once and cache them. */
    private static void resolveApi() {
        if (apiResolved) return;
        apiResolved = true;
        try {
            Class<?> numClass   = Class.forName(NUM_CLASS);
            Field    bankField  = numClass.getField("BANK");
            bankManager = bankField.get(null);
            Class<?> managerClass = bankManager.getClass();

            // Numismatics may accept either ServerPlayer or UUID — try both
            try {
                mGetAccount  = managerClass.getMethod("getAccount", ServerPlayer.class);
                usePlayerArg = true;
            } catch (NoSuchMethodException ignored) {
                mGetAccount  = managerClass.getMethod("getAccount", java.util.UUID.class);
                usePlayerArg = false;
            }

            Class<?> accClass = Class.forName(ACC_CLASS);
            mGetBalance = accClass.getMethod("getBalance");
            mDeduct     = accClass.getMethod("deduct", int.class, boolean.class);
            mDeposit    = accClass.getMethod("deposit", int.class);

            BotzGuildz.LOGGER.info("[BotzGuildz] Numismatics API resolved via reflection.");
        } catch (Exception e) {
            BotzGuildz.LOGGER.error("[BotzGuildz] Failed to resolve Numismatics API: {}", e.getMessage());
        }
    }

    /** Retrieve the BankAccount for a player, or null on failure. */
    private static Object getAccount(ServerPlayer player) {
        resolveApi();
        if (bankManager == null || mGetAccount == null) return null;
        try {
            Object arg = usePlayerArg ? player : player.getUUID();
            return mGetAccount.invoke(bankManager, arg);
        } catch (Exception e) {
            return null;
        }
    }

    // ── ICurrencyProvider ─────────────────────────────────────────────────────

    @Override
    public long getBalance(ServerPlayer player) {
        resolveApi();
        if (mGetBalance == null) return 0;
        Object account = getAccount(player);
        if (account == null) return 0;
        try {
            return ((Number) mGetBalance.invoke(account)).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean deduct(ServerPlayer player, long amount) {
        resolveApi();
        if (mDeduct == null || mGetBalance == null) return false;
        Object account = getAccount(player);
        if (account == null) return false;
        try {
            long balance = ((Number) mGetBalance.invoke(account)).longValue();
            if (balance < amount) return false;
            mDeduct.invoke(account, (int) Math.min(amount, Integer.MAX_VALUE), false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void give(ServerPlayer player, long amount) {
        resolveApi();
        if (mDeposit == null) return;
        Object account = getAccount(player);
        if (account == null) return;
        try {
            mDeposit.invoke(account, (int) Math.min(amount, Integer.MAX_VALUE));
        } catch (Exception ignored) {}
    }

    @Override
    public String format(long spurs) {
        if (spurs <= 0) return "0 Spurs";

        // Coin breakdown: SUN > CROWN > COG > SPROCKET > BEVEL > SPUR
        long[]   values = {4096, 512, 64, 16, 8, 1};
        String[] names  = {"Sun", "Crown", "Cog", "Sprocket", "Bevel", "Spur"};

        StringBuilder sb        = new StringBuilder();
        long          remaining = spurs;
        for (int i = 0; i < values.length; i++) {
            long count = remaining / values[i];
            if (count > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(count).append(" ").append(names[i]).append(count != 1 ? "s" : "");
                remaining -= count * values[i];
            }
        }
        return sb.length() > 0 ? sb.toString() : "0 Spurs";
    }

    @Override
    public String currencyName() {
        return "Spurs";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName(NUM_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
