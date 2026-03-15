package com.botzguildz.fabric.config;

import com.botzguildz.BotzGuildz;
import com.botzguildz.config.GuildConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON-based config for the Fabric platform.
 * Reads and writes {@code config/botzguildz.json}.
 * Call {@link #load()} then {@link #bind()} during mod init.
 */
public final class FabricGuildConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static JsonObject cfg = new JsonObject();

    private FabricGuildConfig() {}

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("botzguildz.json");
        if (Files.exists(file)) {
            try (Reader r = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                cfg = JsonParser.parseReader(r).getAsJsonObject();
            } catch (Exception e) {
                BotzGuildz.LOGGER.warn("[BotzGuildz] Failed to read config: {}. Using defaults.", e.getMessage());
                cfg = new JsonObject();
            }
        }
        saveDefaults(file);
    }

    private static void saveDefaults(Path file) {
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(cfg, w);
        } catch (Exception e) {
            BotzGuildz.LOGGER.warn("[BotzGuildz] Failed to save config defaults: {}", e.getMessage());
        }
    }

    private static int getInt(String key, int def) {
        if (cfg.has(key) && cfg.get(key).isJsonPrimitive()) return cfg.get(key).getAsInt();
        cfg.addProperty(key, def);
        return def;
    }

    private static long getLong(String key, long def) {
        if (cfg.has(key) && cfg.get(key).isJsonPrimitive()) return cfg.get(key).getAsLong();
        cfg.addProperty(key, def);
        return def;
    }

    private static String getString(String key, String def) {
        if (cfg.has(key) && cfg.get(key).isJsonPrimitive()) return cfg.get(key).getAsString();
        cfg.addProperty(key, def);
        return def;
    }

    /** Wires loaded values into the common GuildConfig facade. */
    public static void bind() {
        GuildConfig.MAX_GUILD_NAME_LENGTH.bindTo(() -> getInt("maxGuildNameLength", 32));
        GuildConfig.MAX_GUILD_TAG_LENGTH .bindTo(() -> getInt("maxGuildTagLength",  6));
        GuildConfig.BASE_MAX_MEMBERS     .bindTo(() -> getInt("baseMaxMembers",     10));
        GuildConfig.MAX_GUILD_LEVEL      .bindTo(() -> getInt("maxGuildLevel",      25));

        GuildConfig.CURRENCY_NAME   .bindTo(() -> getString("currencyName",   "Guild Coins"));
        GuildConfig.CURRENCY_SYMBOL .bindTo(() -> getString("currencySymbol", "GC"));
        GuildConfig.CURRENCY_ITEM   .bindTo(() -> getString("currencyItem",   "minecraft:gold_ingot"));
        GuildConfig.CURRENCY_PER_KILL  .bindTo(() -> getInt("currencyPerKill",  10));
        GuildConfig.CURRENCY_PER_LOGIN .bindTo(() -> getInt("currencyPerLogin",  5));
        GuildConfig.MAX_BANK_BALANCE   .bindTo(() -> getLong("maxBankBalance", 1_000_000L));

        GuildConfig.XP_PER_KILL         .bindTo(() -> getInt("xpPerKill",         15));
        GuildConfig.XP_PER_MEMBER_LOGIN .bindTo(() -> getInt("xpPerMemberLogin",   5));
        GuildConfig.XP_PER_WAR_WIN      .bindTo(() -> getInt("xpPerWarWin",       500));

        GuildConfig.WAR_ACCEPT_TIMEOUT_SECONDS .bindTo(() -> getInt("warAcceptTimeoutSeconds", 120));
        GuildConfig.WAR_COOLDOWN_MINUTES       .bindTo(() -> getInt("warCooldownMinutes",       60));
        GuildConfig.ARENA_WAR_DURATION_SECONDS .bindTo(() -> getInt("arenaWarDurationSeconds", 300));
        GuildConfig.ARENA_SIZE                 .bindTo(() -> getInt("arenaSize",                40));
        GuildConfig.WAR_WIN_CURRENCY_REWARD    .bindTo(() -> getInt("warWinCurrencyReward",    500));
        GuildConfig.WAR_WIN_XP_REWARD          .bindTo(() -> getInt("warWinXpReward",         500));

        GuildConfig.DUEL_TIMEOUT_SECONDS .bindTo(() -> getInt("duelTimeoutSeconds", 30));
        GuildConfig.DUEL_RADIUS_BLOCKS   .bindTo(() -> getInt("duelRadiusBlocks",   20));

        GuildConfig.MAX_ALLIANCES .bindTo(() -> getInt("maxAlliances", 3));

        GuildConfig.HOME_COOLDOWN_SECONDS .bindTo(() -> getInt("homeCooldownSeconds", 30));
    }
}
