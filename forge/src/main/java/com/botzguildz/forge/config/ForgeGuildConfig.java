package com.botzguildz.forge.config;

import com.botzguildz.config.GuildConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/** Forge config spec - mirrors GuildConfig and calls bind() to wire delegates. */
public final class ForgeGuildConfig {

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue MAX_GUILD_NAME_LENGTH;
    private static final ForgeConfigSpec.IntValue MAX_GUILD_TAG_LENGTH;
    private static final ForgeConfigSpec.IntValue BASE_MAX_MEMBERS;
    private static final ForgeConfigSpec.IntValue MAX_GUILD_LEVEL;

    private static final ForgeConfigSpec.ConfigValue<String> CURRENCY_NAME;
    private static final ForgeConfigSpec.ConfigValue<String> CURRENCY_SYMBOL;
    private static final ForgeConfigSpec.ConfigValue<String> CURRENCY_ITEM;
    private static final ForgeConfigSpec.IntValue  CURRENCY_PER_KILL;
    private static final ForgeConfigSpec.IntValue  CURRENCY_PER_LOGIN;
    private static final ForgeConfigSpec.LongValue MAX_BANK_BALANCE;

    private static final ForgeConfigSpec.IntValue XP_PER_KILL;
    private static final ForgeConfigSpec.IntValue XP_PER_MEMBER_LOGIN;
    private static final ForgeConfigSpec.IntValue XP_PER_WAR_WIN;

    private static final ForgeConfigSpec.IntValue WAR_ACCEPT_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.IntValue WAR_COOLDOWN_MINUTES;
    private static final ForgeConfigSpec.IntValue ARENA_WAR_DURATION_SECONDS;
    private static final ForgeConfigSpec.IntValue ARENA_SIZE;
    private static final ForgeConfigSpec.IntValue WAR_WIN_CURRENCY_REWARD;
    private static final ForgeConfigSpec.IntValue WAR_WIN_XP_REWARD;

    private static final ForgeConfigSpec.IntValue DUEL_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.IntValue DUEL_RADIUS_BLOCKS;
    private static final ForgeConfigSpec.IntValue MAX_ALLIANCES;
    private static final ForgeConfigSpec.IntValue HOME_COOLDOWN_SECONDS;

    static {
        BUILDER.push("guild");
        MAX_GUILD_NAME_LENGTH = BUILDER.comment("Maximum characters in a guild name.")
                .defineInRange("maxGuildNameLength", 32, 3, 64);
        MAX_GUILD_TAG_LENGTH  = BUILDER.comment("Maximum characters in a guild tag (shown in chat prefix).")
                .defineInRange("maxGuildTagLength", 6, 2, 10);
        BASE_MAX_MEMBERS      = BUILDER.comment("Base maximum members before Member Slot upgrades.")
                .defineInRange("baseMaxMembers", 10, 1, 500);
        MAX_GUILD_LEVEL       = BUILDER.comment("Maximum guild level.")
                .defineInRange("maxGuildLevel", 25, 5, 100);
        BUILDER.pop();

        BUILDER.push("currency");
        CURRENCY_NAME    = BUILDER.comment("Display name for guild currency.")
                .define("currencyName", "Guild Coins");
        CURRENCY_SYMBOL  = BUILDER.comment("Short symbol for guild currency.")
                .define("currencySymbol", "GC");
        CURRENCY_ITEM    = BUILDER.comment("Item registry ID used as physical currency. Ignored if Create: Numismatics is installed.")
                .define("currencyItem", "minecraft:gold_ingot");
        CURRENCY_PER_KILL  = BUILDER.comment("Currency earned per player kill.")
                .defineInRange("currencyPerKill", 10, 0, 10000);
        CURRENCY_PER_LOGIN = BUILDER.comment("Currency earned for each member login per day.")
                .defineInRange("currencyPerLogin", 5, 0, 10000);
        MAX_BANK_BALANCE   = BUILDER.comment("Maximum guild bank balance.")
                .defineInRange("maxBankBalance", 1_000_000L, 100L, Long.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("experience");
        XP_PER_KILL         = BUILDER.comment("Guild XP earned per player kill.")
                .defineInRange("xpPerKill", 15, 0, 100000);
        XP_PER_MEMBER_LOGIN = BUILDER.comment("Guild XP earned per member login.")
                .defineInRange("xpPerMemberLogin", 5, 0, 100000);
        XP_PER_WAR_WIN      = BUILDER.comment("Guild XP earned for winning a guild war.")
                .defineInRange("xpPerWarWin", 500, 0, 100000);
        BUILDER.pop();

        BUILDER.push("war");
        WAR_ACCEPT_TIMEOUT_SECONDS = BUILDER.comment("Seconds the challenged guild has to accept a war declaration.")
                .defineInRange("warAcceptTimeoutSeconds", 120, 10, 600);
        WAR_COOLDOWN_MINUTES       = BUILDER.comment("Minutes a guild must wait between declaring wars.")
                .defineInRange("warCooldownMinutes", 60, 0, 10080);
        ARENA_WAR_DURATION_SECONDS = BUILDER.comment("Duration of an arena war in seconds.")
                .defineInRange("arenaWarDurationSeconds", 300, 30, 3600);
        ARENA_SIZE                 = BUILDER.comment("Radius of the generated arena in blocks.")
                .defineInRange("arenaSize", 40, 10, 200);
        WAR_WIN_CURRENCY_REWARD    = BUILDER.comment("Currency awarded to winning guild.")
                .defineInRange("warWinCurrencyReward", 500, 0, 100000);
        WAR_WIN_XP_REWARD          = BUILDER.comment("XP awarded to winning guild.")
                .defineInRange("warWinXpReward", 500, 0, 100000);
        BUILDER.pop();

        BUILDER.push("duel");
        DUEL_TIMEOUT_SECONDS = BUILDER.comment("Seconds before an unaccepted duel challenge expires.")
                .defineInRange("duelTimeoutSeconds", 30, 5, 300);
        DUEL_RADIUS_BLOCKS   = BUILDER.comment("Max blocks a player can move from duel start before forfeiting.")
                .defineInRange("duelRadiusBlocks", 20, 5, 100);
        BUILDER.pop();

        BUILDER.push("alliance");
        MAX_ALLIANCES = BUILDER.comment("Maximum alliances a guild can have at once.")
                .defineInRange("maxAlliances", 3, 1, 20);
        BUILDER.pop();

        BUILDER.push("home");
        HOME_COOLDOWN_SECONDS = BUILDER.comment("Cooldown in seconds between /guild home uses.")
                .defineInRange("homeCooldownSeconds", 30, 0, 3600);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ForgeGuildConfig() {}

    /** Wires live Forge config values into the common GuildConfig facade. */
    public static void bind() {
        GuildConfig.MAX_GUILD_NAME_LENGTH.bindTo(MAX_GUILD_NAME_LENGTH::get);
        GuildConfig.MAX_GUILD_TAG_LENGTH .bindTo(MAX_GUILD_TAG_LENGTH::get);
        GuildConfig.BASE_MAX_MEMBERS     .bindTo(BASE_MAX_MEMBERS::get);
        GuildConfig.MAX_GUILD_LEVEL      .bindTo(MAX_GUILD_LEVEL::get);

        GuildConfig.CURRENCY_NAME   .bindTo(CURRENCY_NAME::get);
        GuildConfig.CURRENCY_SYMBOL .bindTo(CURRENCY_SYMBOL::get);
        GuildConfig.CURRENCY_ITEM   .bindTo(CURRENCY_ITEM::get);
        GuildConfig.CURRENCY_PER_KILL  .bindTo(CURRENCY_PER_KILL::get);
        GuildConfig.CURRENCY_PER_LOGIN .bindTo(CURRENCY_PER_LOGIN::get);
        GuildConfig.MAX_BANK_BALANCE   .bindTo(MAX_BANK_BALANCE::get);

        GuildConfig.XP_PER_KILL         .bindTo(XP_PER_KILL::get);
        GuildConfig.XP_PER_MEMBER_LOGIN .bindTo(XP_PER_MEMBER_LOGIN::get);
        GuildConfig.XP_PER_WAR_WIN      .bindTo(XP_PER_WAR_WIN::get);

        GuildConfig.WAR_ACCEPT_TIMEOUT_SECONDS .bindTo(WAR_ACCEPT_TIMEOUT_SECONDS::get);
        GuildConfig.WAR_COOLDOWN_MINUTES       .bindTo(WAR_COOLDOWN_MINUTES::get);
        GuildConfig.ARENA_WAR_DURATION_SECONDS .bindTo(ARENA_WAR_DURATION_SECONDS::get);
        GuildConfig.ARENA_SIZE                 .bindTo(ARENA_SIZE::get);
        GuildConfig.WAR_WIN_CURRENCY_REWARD    .bindTo(WAR_WIN_CURRENCY_REWARD::get);
        GuildConfig.WAR_WIN_XP_REWARD          .bindTo(WAR_WIN_XP_REWARD::get);

        GuildConfig.DUEL_TIMEOUT_SECONDS .bindTo(DUEL_TIMEOUT_SECONDS::get);
        GuildConfig.DUEL_RADIUS_BLOCKS   .bindTo(DUEL_RADIUS_BLOCKS::get);

        GuildConfig.MAX_ALLIANCES .bindTo(MAX_ALLIANCES::get);

        GuildConfig.HOME_COOLDOWN_SECONDS .bindTo(HOME_COOLDOWN_SECONDS::get);
    }
}
