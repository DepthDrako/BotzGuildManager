package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.*;
import com.botzguildz.dimension.ArenaManager;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public class GuildWarCommand {

    // ── Suggestion providers ───────────────────────────────────────────────────

    /** Suggests all guild names — used for the war declare target argument. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GUILDS =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    GuildSavedData data = GuildSavedData.get(player.getServer());
                    // Exclude the player's own guild from suggestions
                    Guild self = GuildUtils.getGuildOf(player);
                    return SharedSuggestionProvider.suggest(
                            data.getAllGuilds().stream()
                                    .filter(g -> self == null || !g.getGuildId().equals(self.getGuildId()))
                                    .map(Guild::getName),
                            builder);
                } catch (Exception e) {
                    return builder.buildFuture();
                }
            };

    /** Suggests war modes: openworld (default) and arena (requires upgrade). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_WAR_MODES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    List.of("openworld", "arena"), builder);

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild.then(Commands.literal("war")
                .requires(src -> src.isPlayer())

                // /guild war declare <guildName> <wager>
                .then(Commands.literal("declare")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests(SUGGEST_GUILDS)
                                .then(Commands.argument("wager", LongArgumentType.longArg(1))
                                        .executes(ctx -> declare(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "target"),
                                                LongArgumentType.getLong(ctx, "wager"))))))

                // /guild war accept <lives> [mode]
                .then(Commands.literal("accept")
                        .then(Commands.argument("lives", IntegerArgumentType.integer(1, 999))
                                .executes(ctx -> accept(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "lives"), "openworld"))
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests(SUGGEST_WAR_MODES)
                                        .executes(ctx -> accept(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "lives"),
                                                StringArgumentType.getString(ctx, "mode"))))))

                // /guild war deny
                .then(Commands.literal("deny")
                        .executes(ctx -> deny(ctx.getSource())))

                // /guild war status
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))

                // /guild war forfeit
                .then(Commands.literal("forfeit")
                        .executes(ctx -> forfeit(ctx.getSource())))
        );
    }

    // ── /guild war declare <target> <wager> ───────────────────────────────────

    private static int declare(CommandSourceStack src, String targetName, long wager) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.DECLARE_WAR)) {
                src.sendFailure(MessageUtils.error("You don't have permission to declare wars.")); return 0;
            }
            if (guild.getCurrentWarId() != null) {
                src.sendFailure(MessageUtils.error("Your guild is already in a war.")); return 0;
            }
            if (GuildUtils.isOnWarCooldown(guild)) {
                src.sendFailure(MessageUtils.error("Your guild is on a war cooldown. Time remaining: "
                        + GuildUtils.formatSeconds(GuildUtils.warCooldownRemainingSeconds(guild)))); return 0;
            }
            if (GuildUtils.hasWarShieldActive(guild)) {
                src.sendFailure(MessageUtils.error("Your guild is protected by a War Shield. Wait until it expires.")); return 0;
            }
            if (!guild.canAfford(wager)) {
                src.sendFailure(MessageUtils.error("Your guild bank can't cover a " + CurrencyManager.format(wager) + " wager. Available: "
                        + CurrencyManager.format(guild.getAvailableBalance()))); return 0;
            }

            MinecraftServer server = player.getServer();
            GuildSavedData data = GuildSavedData.get(server);
            Guild target = data.getGuildByName(targetName);
            if (target == null) { src.sendFailure(MessageUtils.error("Guild '" + targetName + "' not found.")); return 0; }
            if (target.getGuildId().equals(guild.getGuildId())) { src.sendFailure(MessageUtils.error("You can't declare war on yourself.")); return 0; }
            if (target.getCurrentWarId() != null) { src.sendFailure(MessageUtils.error(target.getName() + " is already in a war.")); return 0; }
            if (GuildUtils.hasWarShieldActive(target)) {
                src.sendFailure(MessageUtils.error(target.getName() + " is protected by a War Shield and cannot be challenged right now.")); return 0;
            }

            GuildWar war = data.declareWar(guild, target, wager);
            if (war == null) { src.sendFailure(MessageUtils.error("Failed to declare war. Check bank funds.")); return 0; }

            int timeoutSecs = GuildConfig.WAR_ACCEPT_TIMEOUT_SECONDS.get();
            MessageUtils.broadcastToGuild(guild,
                    MessageUtils.warn("War declared on " + target.getName() + "! Wager: " + CurrencyManager.format(wager) + ". Waiting for response (" + timeoutSecs + "s)."),
                    server);
            MessageUtils.broadcastToGuild(target,
                    MessageUtils.warn("⚔ " + guild.getName() + " has declared war on you! Wager: " + CurrencyManager.format(wager)
                            + ". Use /guild war accept <lives> to accept, or /guild war deny to refuse. You have " + timeoutSecs + " seconds."),
                    server);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); }
        return 1;
    }

    // ── /guild war accept <lives> [mode] ─────────────────────────────────────

    private static int accept(CommandSourceStack src, int lives, String modeStr) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.DECLARE_WAR)) {
                src.sendFailure(MessageUtils.error("You don't have permission to accept wars.")); return 0;
            }

            MinecraftServer server = player.getServer();
            GuildSavedData data = GuildSavedData.get(server);
            GuildWar war = data.getPendingWarTargetingGuild(guild.getGuildId());
            if (war == null) { src.sendFailure(MessageUtils.error("No pending war declaration targeting your guild.")); return 0; }

            // Parse war mode
            WarMode mode = WarMode.OPEN_WORLD;
            if (modeStr.equalsIgnoreCase("arena") || modeStr.equalsIgnoreCase("ARENA")) {
                Guild declaring = data.getGuildById(war.getDeclaringGuildId());
                if (declaring != null && !declaring.hasUpgrade("ARENA_WAR")) {
                    src.sendFailure(MessageUtils.error("The declaring guild doesn't have the Arena Warfare upgrade.")); return 0;
                }
                if (!guild.hasUpgrade("ARENA_WAR")) {
                    src.sendFailure(MessageUtils.error("Your guild doesn't have the Arena Warfare upgrade.")); return 0;
                }
                mode = WarMode.ARENA;
            }

            // Challenged guild must also match the wager
            if (!guild.canAfford(war.getWageredAmountPerSide())) {
                src.sendFailure(MessageUtils.error("Your guild can't cover the " + CurrencyManager.format(war.getWageredAmountPerSide()) + " wager. Available: "
                        + CurrencyManager.format(guild.getAvailableBalance()))); return 0;
            }

            Guild declaring = data.getGuildById(war.getDeclaringGuildId());
            List<UUID> declaringOnline = GuildUtils.getOnlineMemberUUIDs(declaring, server);
            List<UUID> challengedOnline = GuildUtils.getOnlineMemberUUIDs(guild, server);

            boolean activated = data.acceptWar(war, guild, lives, mode, declaringOnline, challengedOnline, server);
            if (!activated) { src.sendFailure(MessageUtils.error("Failed to start war.")); return 0; }

            final WarMode finalMode = mode;
            MessageUtils.broadcastToGuild(guild,    MessageUtils.warn("⚔ War started! Lives: " + lives + " | Mode: " + finalMode.name() + " | Wager: " + CurrencyManager.format(war.getWageredAmountPerSide()) + " each."), server);
            MessageUtils.broadcastToGuild(declaring, MessageUtils.warn("⚔ War started vs " + guild.getName() + "! Lives: " + lives + " | Mode: " + finalMode.name()), server);

            // If arena mode, teleport everyone in
            if (mode == WarMode.ARENA) {
                ArenaManager.setupArenaWar(war, declaring, guild, server);
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); }
        return 1;
    }

    // ── /guild war deny ───────────────────────────────────────────────────────

    private static int deny(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            MinecraftServer server = player.getServer();
            GuildSavedData data = GuildSavedData.get(server);
            GuildWar war = data.getPendingWarTargetingGuild(guild.getGuildId());
            if (war == null) { src.sendFailure(MessageUtils.error("No pending war to deny.")); return 0; }

            Guild declaring = data.getGuildById(war.getDeclaringGuildId());
            // Refund declaring guild's escrow
            if (declaring != null) {
                declaring.releaseEscrow(war.getWageredAmountPerSide());
                declaring.setCurrentWarId(null);
                MessageUtils.broadcastToGuild(declaring, MessageUtils.warn(guild.getName() + " denied your war declaration. Wager refunded."), server);
            }
            guild.setCurrentWarId(null);
            data.removeWarEntry(war.getWarId());
            src.sendSuccess(() -> MessageUtils.info("War declaration denied."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild war status ─────────────────────────────────────────────────────

    private static int status(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            GuildWar war = data.getActiveWarForGuild(guild.getGuildId());
            if (war == null) {
                GuildWar pending = data.getPendingWarTargetingGuild(guild.getGuildId());
                if (pending == null) pending = data.getPendingWarDeclaredByGuild(guild.getGuildId());
                if (pending == null) { src.sendFailure(MessageUtils.error("Your guild is not in a war.")); return 0; }
                final GuildWar pendingFinal = pending; // lambda requires effectively-final capture
                src.sendSuccess(() -> MessageUtils.info("War is pending acceptance. Expires in "
                        + GuildUtils.formatSeconds((pendingFinal.getPendingExpiry() - System.currentTimeMillis()) / 1000)), false);
                return 1;
            }

            Guild declaring  = data.getGuildById(war.getDeclaringGuildId());
            Guild challenged = data.getGuildById(war.getChallengedGuildId());
            String dName = declaring  != null ? declaring.getName()  : "Unknown";
            String cName = challenged != null ? challenged.getName() : "Unknown";

            src.sendSuccess(() -> MessageUtils.header("War Status"), false);
            src.sendSuccess(() -> Component.literal("  " + dName + " vs " + cName).withStyle(MessageUtils.YELLOW), false);
            src.sendSuccess(() -> Component.literal("  Mode: " + war.getMode().name() + " | Lives: " + war.getLivesPerPlayer()
                    + " | Wager: " + CurrencyManager.format(war.getWageredAmountPerSide()) + " each").withStyle(MessageUtils.GRAY), false);
            src.sendSuccess(() -> Component.literal("  Time Remaining: " + GuildUtils.formatSeconds(war.getTimeRemainingMs() / 1000)).withStyle(MessageUtils.AQUA), false);
            src.sendSuccess(() -> Component.literal("  " + dName + " kills: " + war.getGuildKills(war.getDeclaringGuildId())).withStyle(MessageUtils.RED), false);
            src.sendSuccess(() -> Component.literal("  " + cName + " kills: " + war.getGuildKills(war.getChallengedGuildId())).withStyle(MessageUtils.RED), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild war forfeit ────────────────────────────────────────────────────

    private static int forfeit(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.DECLARE_WAR)) {
                src.sendFailure(MessageUtils.error("Only officers or above can forfeit a war.")); return 0;
            }

            MinecraftServer server = player.getServer();
            GuildSavedData data = GuildSavedData.get(server);
            GuildWar war = data.getActiveWarForGuild(guild.getGuildId());
            if (war == null) { src.sendFailure(MessageUtils.error("Your guild is not in an active war.")); return 0; }

            UUID opponentId = war.getOpponent(guild.getGuildId());
            Guild opponent  = data.getGuildById(opponentId);

            // If guild has REDUCED_LOSS, they only pay 75% of escrow (keep 25% back)
            if (guild.hasUpgrade("REDUCED_LOSS")) {
                long savedAmount = (long)(war.getWageredAmountPerSide() * 0.25);
                guild.deposit(savedAmount); // Return 25% of escrow before payout calculation
                guild.addLog("War forfeited. REDUCED_LOSS saved " + CurrencyManager.format(savedAmount) + ".");
            }

            data.endWar(war, opponentId, server);

            // Return arena players
            if (war.getMode() == WarMode.ARENA) {
                ArenaManager.returnAllPlayers(war, server);
            }

            MessageUtils.broadcastToGuild(guild, MessageUtils.warn("Your guild has forfeited the war."), server);
            if (opponent != null) MessageUtils.broadcastToGuild(opponent, MessageUtils.success("The enemy guild has forfeited! Victory!"), server);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); }
        return 1;
    }
}
