package com.botzguildz.config;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Platform-neutral config facade.
 *
 * Each value starts with a sensible default. On Forge, {@link
 * com.botzguildz.forge.config.ForgeGuildConfig#bind()} replaces every
 * supplier with a live delegate into the Forge config spec so the in-game
 * /config reload keeps things up-to-date. On Fabric the JSON config does
 * the same via {@link com.botzguildz.fabric.config.FabricGuildConfig#bind()}.
 */
public final class GuildConfig {

    private GuildConfig() {}

    // ── Wrapper types ─────────────────────────────────────────────────────────

    public static final class IntValue {
        private IntSupplier supplier;
        public IntValue(int defaultValue) { this.supplier = () -> defaultValue; }
        public void bindTo(IntSupplier s)  { this.supplier = s; }
        public int get()                   { return supplier.getAsInt(); }
    }

    public static final class LongValue {
        private LongSupplier supplier;
        public LongValue(long defaultValue) { this.supplier = () -> defaultValue; }
        public void bindTo(LongSupplier s)  { this.supplier = s; }
        public long get()                   { return supplier.getAsLong(); }
    }

    public static final class ConfigValue<T> {
        private Supplier<T> supplier;
        public ConfigValue(T defaultValue) { this.supplier = () -> defaultValue; }
        public void bindTo(Supplier<T> s)  { this.supplier = s; }
        public T get()                     { return supplier.get(); }
    }

    // ── Guild Settings ────────────────────────────────────────────────────────

    public static final IntValue MAX_GUILD_NAME_LENGTH = new IntValue(32);
    public static final IntValue MAX_GUILD_TAG_LENGTH  = new IntValue(6);
    public static final IntValue BASE_MAX_MEMBERS      = new IntValue(10);
    public static final IntValue MAX_GUILD_LEVEL       = new IntValue(25);

    // ── Currency ──────────────────────────────────────────────────────────────

    public static final ConfigValue<String> CURRENCY_NAME   = new ConfigValue<>("Guild Coins");
    public static final ConfigValue<String> CURRENCY_SYMBOL = new ConfigValue<>("GC");
    public static final ConfigValue<String> CURRENCY_ITEM   = new ConfigValue<>("minecraft:gold_ingot");
    public static final IntValue  CURRENCY_PER_KILL  = new IntValue(10);
    public static final IntValue  CURRENCY_PER_LOGIN = new IntValue(5);
    public static final LongValue MAX_BANK_BALANCE   = new LongValue(1_000_000L);

    // ── Guild XP ──────────────────────────────────────────────────────────────

    public static final IntValue XP_PER_KILL         = new IntValue(15);
    public static final IntValue XP_PER_MEMBER_LOGIN = new IntValue(5);
    public static final IntValue XP_PER_WAR_WIN      = new IntValue(500);

    // ── War Settings ──────────────────────────────────────────────────────────

    public static final IntValue WAR_ACCEPT_TIMEOUT_SECONDS = new IntValue(120);
    public static final IntValue WAR_COOLDOWN_MINUTES       = new IntValue(60);
    public static final IntValue ARENA_WAR_DURATION_SECONDS = new IntValue(300);
    public static final IntValue ARENA_SIZE                 = new IntValue(40);
    public static final IntValue WAR_WIN_CURRENCY_REWARD    = new IntValue(500);
    public static final IntValue WAR_WIN_XP_REWARD          = new IntValue(500);

    // ── Duel Settings ─────────────────────────────────────────────────────────

    public static final IntValue DUEL_TIMEOUT_SECONDS = new IntValue(30);
    public static final IntValue DUEL_RADIUS_BLOCKS   = new IntValue(20);

    // ── Alliance Settings ─────────────────────────────────────────────────────

    public static final IntValue MAX_ALLIANCES = new IntValue(3);

    // ── Home Settings ─────────────────────────────────────────────────────────

    public static final IntValue HOME_COOLDOWN_SECONDS = new IntValue(30);
}
