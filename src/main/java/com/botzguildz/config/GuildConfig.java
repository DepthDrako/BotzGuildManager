package com.botzguildz.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class GuildConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // --- Guild Settings ---
    public static final ForgeConfigSpec.IntValue MAX_GUILD_NAME_LENGTH;
    public static final ForgeConfigSpec.IntValue MAX_GUILD_TAG_LENGTH;
    public static final ForgeConfigSpec.IntValue BASE_MAX_MEMBERS;
    public static final ForgeConfigSpec.IntValue MAX_GUILD_LEVEL;

    // --- Currency ---
    public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_NAME;
    public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_SYMBOL;
    public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_ITEM;
    public static final ForgeConfigSpec.IntValue CURRENCY_PER_KILL;
    public static final ForgeConfigSpec.IntValue CURRENCY_PER_LOGIN;
    public static final ForgeConfigSpec.LongValue MAX_BANK_BALANCE;

    // --- Guild XP ---
    public static final ForgeConfigSpec.IntValue XP_PER_KILL;
    public static final ForgeConfigSpec.IntValue XP_PER_MEMBER_LOGIN;
    public static final ForgeConfigSpec.IntValue XP_PER_WAR_WIN;

    // --- War Settings ---
    public static final ForgeConfigSpec.IntValue WAR_ACCEPT_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue WAR_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue ARENA_WAR_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue ARENA_SIZE;
    public static final ForgeConfigSpec.IntValue WAR_WIN_CURRENCY_REWARD;
    public static final ForgeConfigSpec.IntValue WAR_WIN_XP_REWARD;

    // --- Duel Settings ---
    public static final ForgeConfigSpec.IntValue DUEL_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue DUEL_RADIUS_BLOCKS;

    // --- Alliance Settings ---
    public static final ForgeConfigSpec.IntValue MAX_ALLIANCES;
    public static final ForgeConfigSpec.IntValue ALLY_TP_COOLDOWN_SECONDS;

    // --- Home Settings ---
    public static final ForgeConfigSpec.IntValue HOME_COOLDOWN_SECONDS;

    // --- Economy Settings ---
    public static final ForgeConfigSpec.IntValue AH_LISTING_FEE_PERCENT;
    public static final ForgeConfigSpec.IntValue AH_MAX_LISTINGS_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue AH_MAX_DURATION_MINUTES;
    public static final ForgeConfigSpec.LongValue BOUNTY_MIN;
    public static final ForgeConfigSpec.LongValue BOUNTY_MAX;
    public static final ForgeConfigSpec.IntValue SHOP_MAX_LISTINGS_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue SHOP_MAX_LISTINGS_PER_GUILD;

    // --- Shop Storage Blacklist ---
    /**
     * Registry IDs of blocks that must never be accepted as a shop storage block.
     * Checked against the block's full registry name, e.g. "minecraft:furnace".
     * Supports wildcards via prefix matching: "mod:*" matches all blocks from that mod.
     */
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> SHOP_STORAGE_BLACKLIST;

    static {
        BUILDER.push("guild");
        MAX_GUILD_NAME_LENGTH  = BUILDER.comment("Maximum characters in a guild name.")
                .defineInRange("maxGuildNameLength", 32, 3, 64);
        MAX_GUILD_TAG_LENGTH   = BUILDER.comment("Maximum characters in a guild tag (shown in chat prefix).")
                .defineInRange("maxGuildTagLength", 6, 2, 10);
        BASE_MAX_MEMBERS       = BUILDER.comment("Base maximum members before Member Slot upgrades.")
                .defineInRange("baseMaxMembers", 10, 1, 500);
        MAX_GUILD_LEVEL        = BUILDER.comment("Maximum guild level.")
                .defineInRange("maxGuildLevel", 25, 5, 100);
        BUILDER.pop();

        BUILDER.push("currency");
        CURRENCY_NAME    = BUILDER.comment("Display name for guild currency (used in physical item mode).")
                .define("currencyName", "Guild Coins");
        CURRENCY_SYMBOL  = BUILDER.comment("Short symbol for guild currency.")
                .define("currencySymbol", "GC");
        CURRENCY_ITEM    = BUILDER.comment("Item registry ID used as physical currency (e.g. minecraft:gold_ingot). Ignored if Create: Numismatics is installed.")
                .define("currencyItem", "minecraft:gold_ingot");
        CURRENCY_PER_KILL  = BUILDER.comment("Currency earned per player kill (for both killer's guild bank and personal).")
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
        WAR_WIN_XP_REWARD          = BUILDER.comment("XP awarded to winning guild (see experience.xpPerWarWin).")
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
        ALLY_TP_COOLDOWN_SECONDS = BUILDER.comment("Cooldown in seconds between /guild ally tp uses per player.")
                .defineInRange("allyTpCooldownSeconds", 300, 0, 7200);
        BUILDER.pop();

        BUILDER.push("home");
        HOME_COOLDOWN_SECONDS = BUILDER.comment("Cooldown in seconds between /guild home uses.")
                .defineInRange("homeCooldownSeconds", 30, 0, 3600);
        BUILDER.pop();

        BUILDER.push("economy");
        AH_LISTING_FEE_PERCENT     = BUILDER.comment("Percentage of sale price taken as an auction house listing fee.")
                .defineInRange("ahListingFeePercent", 5, 0, 50);
        AH_MAX_LISTINGS_PER_PLAYER = BUILDER.comment("Maximum active auction listings per player at once.")
                .defineInRange("ahMaxListingsPerPlayer", 10, 1, 500);
        AH_MAX_DURATION_MINUTES    = BUILDER.comment("Maximum timed auction duration in minutes (default 10080 = 1 week).")
                .defineInRange("ahMaxDurationMinutes", 10080, 1, 43200);
        BOUNTY_MIN                 = BUILDER.comment("Minimum currency amount for a single bounty placement.")
                .defineInRange("bountyMin", 10L, 1L, Long.MAX_VALUE);
        BOUNTY_MAX                 = BUILDER.comment("Maximum currency amount for a single bounty placement.")
                .defineInRange("bountyMax", 1_000_000L, 1L, Long.MAX_VALUE);
        SHOP_MAX_LISTINGS_PER_PLAYER = BUILDER.comment("Maximum shop listings a single player can have.")
                .defineInRange("shopMaxListingsPerPlayer", 50, 1, 1000);
        SHOP_MAX_LISTINGS_PER_GUILD  = BUILDER.comment("Maximum shop listings a single guild can have.")
                .defineInRange("shopMaxListingsPerGuild", 100, 1, 1000);
        SHOP_STORAGE_BLACKLIST = BUILDER.comment(
                "Block registry IDs that are NOT allowed as shop storage containers.",
                "Supports mod-prefix wildcards: \"examplemod:*\" blocks every block from that mod.",
                "Format: [\"namespace:path\", ...]")
                .defineList("shopStorageBlacklist",
                        java.util.List.of(
                                // Vanilla processing blocks (have IItemHandler but aren't storage)
                                "minecraft:furnace",
                                "minecraft:blast_furnace",
                                "minecraft:smoker",
                                "minecraft:brewing_stand",
                                "minecraft:composter",
                                "minecraft:dispenser",
                                "minecraft:dropper",
                                "minecraft:hopper",
                                "minecraft:lectern",
                                "minecraft:enchanting_table",
                                "minecraft:grindstone",
                                "minecraft:stonecutter",
                                "minecraft:loom",
                                "minecraft:cartography_table",
                                "minecraft:smithing_table",
                                "minecraft:crafting_table",
                                // Vanilla inventory-adjacent blocks
                                "minecraft:jukebox",
                                "minecraft:chiseled_bookshelf"
                        ),
                        e -> e instanceof String s && !s.isBlank());
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
