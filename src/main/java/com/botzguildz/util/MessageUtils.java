package com.botzguildz.util;

import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildMember;
import com.botzguildz.data.GuildSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Centralised chat message formatting and routing for BotzGuildz.
 */
public class MessageUtils {

    // ── Colour constants ──────────────────────────────────────────────────────

    public static final ChatFormatting GOLD   = ChatFormatting.GOLD;
    public static final ChatFormatting GREEN  = ChatFormatting.GREEN;
    public static final ChatFormatting RED    = ChatFormatting.RED;
    public static final ChatFormatting GRAY   = ChatFormatting.GRAY;
    public static final ChatFormatting YELLOW = ChatFormatting.YELLOW;
    public static final ChatFormatting AQUA   = ChatFormatting.AQUA;
    public static final ChatFormatting WHITE  = ChatFormatting.WHITE;

    // ── Prefix builders ───────────────────────────────────────────────────────

    /** "[BotzGuildz] " prefix in gold. */
    public static MutableComponent prefix() {
        return Component.literal("[BotzGuildz] ").withStyle(GOLD);
    }

    /** "[GUILD TAG] " prefix using the guild's chosen chat colour. */
    public static MutableComponent guildPrefix(Guild guild) {
        return Component.literal("[" + guild.getTag() + "] ").withStyle(guild.getChatColor());
    }

    /** "[GUILD TAG-OFC] " prefix using the guild's chosen chat colour. */
    public static MutableComponent officerPrefix(Guild guild) {
        return Component.literal("[" + guild.getTag() + "-OFC] ").withStyle(guild.getChatColor());
    }

    // ── System messages ───────────────────────────────────────────────────────

    public static MutableComponent success(String text) {
        return prefix().append(Component.literal(text).withStyle(GREEN));
    }

    public static MutableComponent error(String text) {
        return prefix().append(Component.literal(text).withStyle(RED));
    }

    public static MutableComponent info(String text) {
        return prefix().append(Component.literal(text).withStyle(GRAY));
    }

    public static MutableComponent warn(String text) {
        return prefix().append(Component.literal(text).withStyle(YELLOW));
    }

    // ── Guild-wide broadcast ──────────────────────────────────────────────────

    /** Send a message to all online guild members. */
    public static void broadcastToGuild(Guild guild, Component message, MinecraftServer server) {
        for (GuildMember member : guild.getAllMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.getPlayerUUID());
            if (player != null) player.sendSystemMessage(message);
        }
    }

    /** Send a message to all online officer+ members. */
    public static void broadcastToOfficers(Guild guild, Component message, MinecraftServer server) {
        for (GuildMember member : guild.getAllMembers()) {
            GuildUtils.RankInfo rank = GuildUtils.getRankInfo(guild, member.getPlayerUUID());
            if (rank == null || rank.priority < 300) continue; // Officer priority = 300
            ServerPlayer player = server.getPlayerList().getPlayer(member.getPlayerUUID());
            if (player != null) player.sendSystemMessage(message);
        }
    }

    // ── Guild chat ────────────────────────────────────────────────────────────

    /** Route a guild chat message. Shows rank name alongside sender. */
    public static void sendGuildChat(Guild guild, ServerPlayer sender, String message, MinecraftServer server) {
        GuildMember member = guild.getMember(sender.getUUID());
        String rankName    = member != null ? member.getRankName() : "Member";
        MutableComponent formatted = guildPrefix(guild)
                .append(Component.literal("[" + rankName + "] ").withStyle(YELLOW))
                .append(Component.literal(sender.getName().getString()).withStyle(WHITE))
                .append(Component.literal(": " + message).withStyle(GRAY));
        broadcastToGuild(guild, formatted, server);
    }

    /** Route an officer chat message. Only officers and above receive it. */
    public static void sendOfficerChat(Guild guild, ServerPlayer sender, String message, MinecraftServer server) {
        MutableComponent formatted = officerPrefix(guild)
                .append(Component.literal(sender.getName().getString()).withStyle(YELLOW))
                .append(Component.literal(": " + message).withStyle(GRAY));
        broadcastToOfficers(guild, formatted, server);
    }

    // ── War notifications ─────────────────────────────────────────────────────

    public static void broadcastWarEvent(Guild guild, String text, MinecraftServer server) {
        broadcastToGuild(guild, Component.literal("⚔ [WAR] ").withStyle(RED)
                .append(Component.literal(text).withStyle(YELLOW)), server);
    }

    // ── Duel notifications ────────────────────────────────────────────────────

    public static void sendDuelMessage(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("⚔ [DUEL] ").withStyle(AQUA)
                .append(Component.literal(text).withStyle(WHITE)));
    }

    // ── Separator / header ────────────────────────────────────────────────────

    public static MutableComponent separator() {
        return Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").withStyle(GOLD);
    }

    public static MutableComponent header(String title) {
        return Component.literal("━━ ").withStyle(GOLD)
                .append(Component.literal(title).withStyle(YELLOW))
                .append(Component.literal(" ━━").withStyle(GOLD));
    }
}
