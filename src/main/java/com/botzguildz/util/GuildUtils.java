package com.botzguildz.util;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Static utility methods shared across the mod.
 */
public class GuildUtils {

    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = 1200;

    // ── Guild Lookup ──────────────────────────────────────────────────────────

    public static Guild getGuildOf(ServerPlayer player) {
        return GuildSavedData.get(player.getServer()).getGuildByPlayer(player.getUUID());
    }

    public static Guild getGuildOf(UUID playerUUID, MinecraftServer server) {
        return GuildSavedData.get(server).getGuildByPlayer(playerUUID);
    }

    public static boolean isInGuild(ServerPlayer player) {
        return GuildSavedData.get(player.getServer()).isInGuild(player.getUUID());
    }

    // ── Rank / Permission ─────────────────────────────────────────────────────

    public static class RankInfo {
        public final String name;
        public final int    priority;
        public RankInfo(String name, int priority) { this.name = name; this.priority = priority; }
    }

    public static RankInfo getRankInfo(Guild guild, UUID playerUUID) {
        GuildRank rank = guild.getMemberRank(playerUUID);
        if (rank == null) return null;
        return new RankInfo(rank.getName(), rank.getPriority());
    }

    /** True if the player has the given permission in their guild. */
    public static boolean hasPermission(ServerPlayer player, RankPermission perm) {
        Guild guild = getGuildOf(player);
        if (guild == null) return false;
        return guild.hasPermission(player.getUUID(), perm);
    }

    // ── Online Members ────────────────────────────────────────────────────────

    public static List<ServerPlayer> getOnlineMembers(Guild guild, MinecraftServer server) {
        List<ServerPlayer> online = new ArrayList<>();
        for (GuildMember member : guild.getAllMembers()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(member.getPlayerUUID());
            if (sp != null) online.add(sp);
        }
        return online;
    }

    public static List<UUID> getOnlineMemberUUIDs(Guild guild, MinecraftServer server) {
        List<UUID> uuids = new ArrayList<>();
        for (ServerPlayer sp : getOnlineMembers(guild, server)) uuids.add(sp.getUUID());
        return uuids;
    }

    // ── Friendly-Fire Check ───────────────────────────────────────────────────

    /**
     * Returns true if attacker should be blocked from harming target
     * due to guild friendly-fire settings or alliance protection.
     */
    public static boolean isFriendlyFireBlocked(ServerPlayer attacker, ServerPlayer target,
                                                MinecraftServer server) {
        GuildSavedData data      = GuildSavedData.get(server);
        Guild attackerGuild      = data.getGuildByPlayer(attacker.getUUID());
        Guild targetGuild        = data.getGuildByPlayer(target.getUUID());

        if (attackerGuild == null || targetGuild == null) return false;

        // Same guild
        if (attackerGuild.getGuildId().equals(targetGuild.getGuildId())) {
            return !attackerGuild.isFriendlyFire();
        }

        // Allied guilds
        if (attackerGuild.isAlliedWith(targetGuild.getGuildId())) {
            // Both guilds must have FF off for protection to apply
            return !attackerGuild.isFriendlyFire() && !targetGuild.isFriendlyFire();
        }

        return false;
    }

    // ── War Cooldown ──────────────────────────────────────────────────────────

    public static boolean isOnWarCooldown(Guild guild) {
        long cooldownMs = GuildConfig.WAR_COOLDOWN_MINUTES.get() * 60_000L;
        return (System.currentTimeMillis() - guild.getLastWarEndTime()) < cooldownMs;
    }

    public static long warCooldownRemainingSeconds(Guild guild) {
        long cooldownMs   = GuildConfig.WAR_COOLDOWN_MINUTES.get() * 60_000L;
        long elapsed      = System.currentTimeMillis() - guild.getLastWarEndTime();
        long remainingMs  = cooldownMs - elapsed;
        return Math.max(0, remainingMs / 1000);
    }

    // ── War Shield Check ──────────────────────────────────────────────────────

    /** Returns true if the guild has the War Shield upgrade and lost within the last 48 hours. */
    public static boolean hasWarShieldActive(Guild guild) {
        if (!guild.hasUpgrade("WAR_SHIELD")) return false;
        long elapsed = System.currentTimeMillis() - guild.getLastWarEndTime();
        return elapsed < 48 * 3_600_000L; // 48 hours in ms
    }

    // ── Home Cooldown ─────────────────────────────────────────────────────────

    public static boolean isOnHomeCooldown(Guild guild, UUID playerUUID) {
        Long lastUse = guild.getHomeCooldowns().get(playerUUID);
        if (lastUse == null) return false;
        long cooldownMs = getEffectiveHomeCooldownMs(guild);
        return (System.currentTimeMillis() - lastUse) < cooldownMs;
    }

    public static long homeCooldownRemainingSeconds(Guild guild, UUID playerUUID) {
        Long lastUse = guild.getHomeCooldowns().get(playerUUID);
        if (lastUse == null) return 0;
        long cooldownMs  = getEffectiveHomeCooldownMs(guild);
        long elapsed     = System.currentTimeMillis() - lastUse;
        return Math.max(0, (cooldownMs - elapsed) / 1000);
    }

    private static long getEffectiveHomeCooldownMs(Guild guild) {
        long baseCooldown = GuildConfig.HOME_COOLDOWN_SECONDS.get() * 1000L;
        if (guild.hasUpgrade("HOME_COOLDOWN_II")) return (long)(baseCooldown * 0.25);
        if (guild.hasUpgrade("HOME_COOLDOWN_I"))  return (long)(baseCooldown * 0.50);
        return baseCooldown;
    }

    // ── Teleportation ─────────────────────────────────────────────────────────

    /** Teleport a player to a position in a specific dimension (by registry key string). */
    public static void teleportPlayer(ServerPlayer player, BlockPos pos, String dimensionId) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ResourceLocation rl = ResourceLocation.tryParse(dimensionId);
        if (rl == null) return;

        ResourceKey<Level> dimKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, rl);
        ServerLevel targetLevel   = server.getLevel(dimKey);
        if (targetLevel == null) return;

        player.teleportTo(targetLevel,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    // ── Upgrade Effects ───────────────────────────────────────────────────────

    /**
     * Apply combat potion effects granted by upgrades to a player on login.
     * Duration: 2 minutes (refreshed on next login).
     */
    public static void applyUpgradeEffects(ServerPlayer player, Guild guild) {
        int duration = 2400; // 2 minutes in ticks

        if (guild.hasUpgrade("COMBAT_ARMOR_II")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 1, false, false, true));
        } else if (guild.hasUpgrade("COMBAT_ARMOR_I")) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0, false, false, true));
        }

        if (guild.hasUpgrade("COMBAT_REGEN")) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 0, false, false, true));
        }
    }

    /**
     * Get the damage multiplier for a player based on their guild's Combat upgrades.
     * Returns 1.0 if none. Applied in GuildEventHandler.
     */
    public static double getDamageMultiplier(Guild guild) {
        double mult = 1.0;
        if (guild.hasUpgrade("COMBAT_DAMAGE_I"))  mult += 0.05;
        if (guild.hasUpgrade("COMBAT_DAMAGE_II")) mult += 0.05;
        return mult;
    }

    // ── Currency Earn Rate ────────────────────────────────────────────────────

    public static long applyEarnRate(Guild guild, long base) {
        double rate = 1.0;
        if (guild.hasUpgrade("EARN_RATE_I"))  rate += 0.25;
        if (guild.hasUpgrade("EARN_RATE_II")) rate += 0.25;
        return (long)(base * rate);
    }

    // ── Alliance Lookup ───────────────────────────────────────────────────────

    public static boolean areAllied(Guild a, Guild b) {
        return a.isAlliedWith(b.getGuildId()) && b.isAlliedWith(a.getGuildId());
    }

    // ── String Helpers ────────────────────────────────────────────────────────

    public static String formatSeconds(long seconds) {
        if (seconds < 60) return seconds + "s";
        long m = seconds / 60;
        long s = seconds % 60;
        if (m < 60) return m + "m " + s + "s";
        long h = m / 60;
        m = m % 60;
        return h + "h " + m + "m";
    }
}
