package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.RankPermission;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * /guild ally — Alliance management. Requires the ALLIANCE Arcane upgrade.
 */
public class GuildAllyCommand {

    // Tracks pending alliance invitations: invitedGuildId -> invitingGuildId
    private static final java.util.Map<UUID, UUID> pendingInvites = new java.util.HashMap<>();

    // ── Suggestion providers ───────────────────────────────────────────────────

    /** Suggests all guild names on the server (excluding the player's own). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ALL_GUILDS =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Guild self = GuildUtils.getGuildOf(player);
                    GuildSavedData data = GuildSavedData.get(player.getServer());
                    return SharedSuggestionProvider.suggest(
                            data.getAllGuilds().stream()
                                    .filter(g -> self == null || !g.getGuildId().equals(self.getGuildId()))
                                    .map(Guild::getName),
                            builder);
                } catch (Exception e) {
                    return builder.buildFuture();
                }
            };

    /**
     * Suggests the names of guilds currently allied with the player's guild.
     * Used for /guild ally break.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CURRENT_ALLIES =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Guild self = GuildUtils.getGuildOf(player);
                    if (self == null) return builder.buildFuture();
                    GuildSavedData data = GuildSavedData.get(player.getServer());
                    return SharedSuggestionProvider.suggest(
                            self.getAlliedGuildIds().stream()
                                    .map(data::getGuildById)
                                    .filter(g -> g != null)
                                    .map(Guild::getName),
                            builder);
                } catch (Exception e) {
                    return builder.buildFuture();
                }
            };

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild.then(Commands.literal("ally")
                .requires(src -> src.isPlayer())

                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))

                // Changed from greedyString to word — guild names cannot have spaces
                .then(Commands.literal("invite")
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .suggests(SUGGEST_ALL_GUILDS)
                                .executes(ctx -> invite(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "guild")))))

                .then(Commands.literal("accept")
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .suggests(SUGGEST_ALL_GUILDS)
                                .executes(ctx -> accept(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "guild")))))

                .then(Commands.literal("deny")
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .suggests(SUGGEST_ALL_GUILDS)
                                .executes(ctx -> deny(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "guild")))))

                .then(Commands.literal("break")
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .suggests(SUGGEST_CURRENT_ALLIES)
                                .executes(ctx -> breakAlly(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "guild")))))
        );
    }

    // ── /guild ally list ──────────────────────────────────────────────────────

    private static int list(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            src.sendSuccess(() -> MessageUtils.header("Allies — " + guild.getName()), false);

            if (guild.getAlliedGuildIds().isEmpty()) {
                src.sendSuccess(() -> MessageUtils.info("No allies yet."), false);
            } else {
                for (UUID allyId : guild.getAlliedGuildIds()) {
                    Guild ally = data.getGuildById(allyId);
                    if (ally != null)
                        src.sendSuccess(() -> Component.literal("  • " + ally.getName()
                                + " [" + ally.getTag() + "]").withStyle(MessageUtils.GREEN), false);
                }
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild ally invite <guild> ────────────────────────────────────────────

    private static int invite(CommandSourceStack src, String targetName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasUpgrade("ALLIANCE")) {
                src.sendFailure(MessageUtils.error("Your guild needs the 'Diplomatic Relations' (Arcane) upgrade to use alliances.")); return 0;
            }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_ALLIES)) {
                src.sendFailure(MessageUtils.error("You don't have permission to manage allies.")); return 0;
            }
            if (guild.getAlliedGuildIds().size() >= GuildConfig.MAX_ALLIANCES.get()) {
                src.sendFailure(MessageUtils.error("You've reached the max alliance limit (" + GuildConfig.MAX_ALLIANCES.get() + ").")); return 0;
            }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            Guild target = data.getGuildByName(targetName);
            if (target == null) { src.sendFailure(MessageUtils.error("Guild '" + targetName + "' not found.")); return 0; }
            if (target.getGuildId().equals(guild.getGuildId())) { src.sendFailure(MessageUtils.error("You can't ally with yourself.")); return 0; }
            if (GuildUtils.areAllied(guild, target)) { src.sendFailure(MessageUtils.error("You are already allied with " + target.getName() + ".")); return 0; }

            pendingInvites.put(target.getGuildId(), guild.getGuildId());
            MinecraftServer server = player.getServer();
            MessageUtils.broadcastToGuild(target,
                    MessageUtils.warn(guild.getName() + " has sent you an alliance invitation! Type /guild ally accept " + guild.getName() + " to accept."),
                    server);
            src.sendSuccess(() -> MessageUtils.success("Alliance invitation sent to " + target.getName() + "."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild ally accept <guild> ────────────────────────────────────────────

    private static int accept(CommandSourceStack src, String inviterName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_ALLIES)) {
                src.sendFailure(MessageUtils.error("You don't have permission to manage allies.")); return 0;
            }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            Guild inviter = data.getGuildByName(inviterName);
            if (inviter == null || !pendingInvites.containsKey(guild.getGuildId())
                    || !pendingInvites.get(guild.getGuildId()).equals(inviter.getGuildId())) {
                src.sendFailure(MessageUtils.error("No pending alliance invitation from '" + inviterName + "'.")); return 0;
            }

            guild.getAlliedGuildIds().add(inviter.getGuildId());
            inviter.getAlliedGuildIds().add(guild.getGuildId());
            pendingInvites.remove(guild.getGuildId());
            data.setDirty();

            guild.addLog("Formed alliance with " + inviter.getName() + ".");
            inviter.addLog("Alliance with " + guild.getName() + " accepted.");

            MinecraftServer server = player.getServer();
            MessageUtils.broadcastToGuild(guild,   MessageUtils.success("Allied with " + inviter.getName() + "!"), server);
            MessageUtils.broadcastToGuild(inviter, MessageUtils.success("Alliance with " + guild.getName() + " accepted!"), server);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild ally deny <guild> ──────────────────────────────────────────────

    private static int deny(CommandSourceStack src, String inviterName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            Guild inviter = data.getGuildByName(inviterName);
            if (inviter == null || !pendingInvites.containsKey(guild.getGuildId())) {
                src.sendFailure(MessageUtils.error("No pending invitation from '" + inviterName + "'.")); return 0;
            }
            pendingInvites.remove(guild.getGuildId());
            src.sendSuccess(() -> MessageUtils.info("Alliance invitation from " + inviterName + " declined."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild ally break <guild> ─────────────────────────────────────────────

    private static int breakAlly(CommandSourceStack src, String targetName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_ALLIES)) {
                src.sendFailure(MessageUtils.error("You don't have permission to manage allies.")); return 0;
            }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            Guild target = data.getGuildByName(targetName);
            if (target == null || !guild.getAlliedGuildIds().contains(target.getGuildId())) {
                src.sendFailure(MessageUtils.error("You are not allied with '" + targetName + "'.")); return 0;
            }

            guild.getAlliedGuildIds().remove(target.getGuildId());
            target.getAlliedGuildIds().remove(guild.getGuildId());
            data.setDirty();

            guild.addLog("Alliance with " + target.getName() + " broken.");
            target.addLog(guild.getName() + " broke the alliance.");

            MinecraftServer server = player.getServer();
            MessageUtils.broadcastToGuild(guild,  MessageUtils.warn("Alliance with " + target.getName() + " has been dissolved."), server);
            MessageUtils.broadcastToGuild(target, MessageUtils.warn(guild.getName() + " dissolved the alliance with you."), server);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }
}
