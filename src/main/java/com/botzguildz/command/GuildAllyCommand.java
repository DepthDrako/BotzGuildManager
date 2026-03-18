package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.RankPermission;
import com.botzguildz.ftb.FTBBridge;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * /guild ally — Alliance management. Requires the ALLIANCE Arcane upgrade.
 *
 * Subcommands:
 *   list                    — show current allies
 *   invite  <guild>         — send alliance invitation (MANAGE_ALLIES)
 *   accept  <guild>         — accept a pending invitation (MANAGE_ALLIES)
 *   deny    <guild>         — decline a pending invitation
 *   break   <guild>         — dissolve an existing alliance (MANAGE_ALLIES)
 *   chat                    — toggle ally-chat mode (MANAGE_ALLIES; officer cross-guild line)
 *   tp      <guild>         — teleport to an allied guild's home (any member)
 */
public class GuildAllyCommand {

    // ── Session state (not persisted — cleared on server restart) ─────────────

    /** Tracks pending alliance invitations: invitedGuildId → invitingGuildId. */
    private static final Map<UUID, UUID> pendingInvites = new HashMap<>();

    /**
     * Players who have ally-chat mode toggled ON.
     * Their messages are routed to the cross-guild officer channel instead of
     * normal global chat.  Accessed from both command and chat event threads.
     */
    private static final Set<UUID> allyChatEnabled =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * Per-player timestamp (ms) of last successful /guild ally tp use.
     * Wrapped in synchronizedMap to match allyChatEnabled's thread-safety contract.
     */
    private static final Map<UUID, Long> allyTpCooldowns =
            Collections.synchronizedMap(new HashMap<>());

    // ── Public API used by GuildEventHandler ──────────────────────────────────

    /** Returns true if the given player currently has ally-chat mode enabled. */
    public static boolean isInAllyChat(UUID playerUUID) {
        return allyChatEnabled.contains(playerUUID);
    }

    /**
     * Remove a player from ally-chat mode — called on logout so their session
     * state is clean on next login.
     */
    public static void clearAllyChat(UUID playerUUID) {
        allyChatEnabled.remove(playerUUID);
    }

    /**
     * Evict TP cooldown entries that are older than twice the configured max cooldown.
     * Call periodically from the server tick (e.g. every 5 minutes) to prevent
     * unbounded map growth.
     */
    public static void pruneExpiredCooldowns() {
        long expiryMs = GuildConfig.ALLY_TP_COOLDOWN_SECONDS.get() * 2000L;
        long now      = System.currentTimeMillis();
        allyTpCooldowns.entrySet().removeIf(e -> now - e.getValue() > expiryMs);
    }

    // ── Private stream helper ─────────────────────────────────────────────────

    /** Resolves each allied guild UUID to a Guild object, filtering nulls. */
    private static Stream<Guild> streamAllies(Guild self, GuildSavedData data) {
        return self.getAlliedGuildIds().stream()
                .map(data::getGuildById)
                .filter(g -> g != null);
    }

    // ── Suggestion providers ──────────────────────────────────────────────────

    /** All guild names except the player's own. */
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
                } catch (Exception e) { return builder.buildFuture(); }
            };

    /** Current allies of the player's guild. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CURRENT_ALLIES =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Guild self = GuildUtils.getGuildOf(player);
                    if (self == null) return builder.buildFuture();
                    GuildSavedData data = GuildSavedData.get(player.getServer());
                    return SharedSuggestionProvider.suggest(
                            streamAllies(self, data).map(Guild::getName), builder);
                } catch (Exception e) { return builder.buildFuture(); }
            };

    /** Allied guilds that have a home set (valid TP targets). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ALLY_HOMES =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Guild self = GuildUtils.getGuildOf(player);
                    if (self == null) return builder.buildFuture();
                    GuildSavedData data = GuildSavedData.get(player.getServer());
                    return SharedSuggestionProvider.suggest(
                            streamAllies(self, data)
                                    .filter(g -> g.getHomePos() != null)
                                    .map(Guild::getName),
                            builder);
                } catch (Exception e) { return builder.buildFuture(); }
            };

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild.then(Commands.literal("ally")
                .requires(src -> src.isPlayer())

                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))

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

                // ── Alliance Network Effects ──────────────────────────────────
                .then(Commands.literal("chat")
                        .executes(ctx -> allyChat(ctx.getSource())))

                .then(Commands.literal("tp")
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .suggests(SUGGEST_ALLY_HOMES)
                                .executes(ctx -> allyTp(ctx.getSource(),
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
                    if (ally != null) {
                        String homeInfo = ally.getHomePos() != null ? " ✔ home set" : " ✖ no home";
                        src.sendSuccess(() -> Component.literal(
                                "  • " + ally.getName() + " [" + ally.getTag() + "]"
                                + homeInfo).withStyle(MessageUtils.GREEN), false);
                    }
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
                    MessageUtils.warn(guild.getName() + " has sent you an alliance invitation! Use /guild ally accept " + guild.getName() + " to accept."),
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
            MessageUtils.broadcastToGuild(guild,
                    MessageUtils.success("Allied with " + inviter.getName() + "! Use /guild ally chat to open the officer line, /guild ally tp " + inviter.getName() + " to visit their home."),
                    server);
            MessageUtils.broadcastToGuild(inviter,
                    MessageUtils.success("Alliance with " + guild.getName() + " accepted! Use /guild ally chat to open the officer line, /guild ally tp " + guild.getName() + " to visit their home."),
                    server);

            // Sync the ALLY relation into FTB Teams so FTBChunks renders their
            // claimed chunks in a distinct allied colour on the map.
            FTBBridge.syncAllianceRelation(guild, inviter, server, true);

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

            // Clear the FTB Teams ALLY relation → revert to NEUTRAL.
            FTBBridge.syncAllianceRelation(guild, target, server, false);

        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild ally chat ──────────────────────────────────────────────────────

    /**
     * Toggle the cross-guild officer chat channel on or off.
     *
     * <p>While enabled, every chat message the player sends is intercepted by
     * {@code GuildEventHandler.onServerChat} and routed to all online members
     * of the player's guild <em>plus</em> all online members of every allied
     * guild, with an {@code [ALLY]} gold prefix prepended.
     *
     * <p>Requires {@link RankPermission#MANAGE_ALLIES} — this is the officer line,
     * not a general member channel.
     */
    private static int allyChat(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_ALLIES)) {
                src.sendFailure(MessageUtils.error("You need MANAGE_ALLIES permission to use the ally officer chat.")); return 0;
            }
            if (guild.getAlliedGuildIds().isEmpty()) {
                src.sendFailure(MessageUtils.error("Your guild has no allies to chat with.")); return 0;
            }

            UUID uuid = player.getUUID();
            // Set.remove() returns true if the element was present — use as atomic toggle
            if (allyChatEnabled.remove(uuid)) {
                src.sendSuccess(() -> MessageUtils.info("Ally chat disabled — messages go to normal chat."), false);
            } else {
                allyChatEnabled.add(uuid);
                src.sendSuccess(() -> MessageUtils.success(
                        "Ally chat enabled! Your messages are sent to all allied guilds. Type /guild ally chat again to disable."), false);
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild ally tp <guild> ────────────────────────────────────────────────

    /**
     * Teleport to an allied guild's home location.
     *
     * <p>Any guild member may use this — it is a passive network benefit of the
     * alliance, not an officer action.  Subject to a configurable cooldown
     * ({@code GuildConfig.ALLY_TP_COOLDOWN_SECONDS}).
     */
    private static int allyTp(CommandSourceStack src, String guildName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            Guild target = data.getGuildByName(guildName);
            if (target == null) {
                src.sendFailure(MessageUtils.error("Guild '" + guildName + "' not found.")); return 0;
            }
            if (!guild.isAlliedWith(target.getGuildId())) {
                src.sendFailure(MessageUtils.error("You are not allied with " + guildName + ".")); return 0;
            }
            if (target.getHomePos() == null) {
                src.sendFailure(MessageUtils.error(guildName + " has not set a guild home yet.")); return 0;
            }

            UUID playerUUID  = player.getUUID();
            long cooldownMs  = GuildConfig.ALLY_TP_COOLDOWN_SECONDS.get() * 1000L;
            if (GuildUtils.isOnCooldown(allyTpCooldowns, playerUUID, cooldownMs)) {
                long remaining = GuildUtils.cooldownRemainingSeconds(allyTpCooldowns, playerUUID, cooldownMs);
                src.sendFailure(MessageUtils.error("Ally teleport on cooldown: " + remaining + "s remaining.")); return 0;
            }

            allyTpCooldowns.put(playerUUID, System.currentTimeMillis());
            GuildUtils.teleportPlayer(player, target.getHomePos(), target.getHomeDimensionId());
            final String targetName = target.getName();
            src.sendSuccess(() -> MessageUtils.success("Teleporting to " + targetName + "'s home..."), false);

        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }
}
