package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.data.*;
import com.botzguildz.dimension.ArenaGenerator;
import com.botzguildz.dimension.ArenaManager;
import com.botzguildz.ftb.FTBBridge;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public class GuildCommand {

    // ── Suggestion providers ───────────────────────────────────────────────────

    /**
     * Suggests the names of all members in the executing player's guild.
     * Used for /guild kick and any other command that targets a guild member.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GUILD_MEMBERS =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Guild guild = GuildUtils.getGuildOf(player);
                    if (guild == null) return builder.buildFuture();
                    return SharedSuggestionProvider.suggest(
                            guild.getAllMembers().stream()
                                    .map(GuildMember::getPlayerName)
                                    .filter(name -> !name.equalsIgnoreCase(player.getName().getString())),
                            builder);
                } catch (Exception e) {
                    return builder.buildFuture();
                }
            };

    /**
     * Suggests the names of all guilds on the server.
     * Used for /guild info <guild>.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ALL_GUILDS =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    GuildSavedData data = GuildSavedData.get(player.getServer());
                    return SharedSuggestionProvider.suggest(
                            data.getAllGuilds().stream().map(Guild::getName),
                            builder);
                } catch (Exception e) {
                    return builder.buildFuture();
                }
            };

    /**
     * Suggests all guild names — works from both player and console contexts.
     * Used for admin commands that don't require a player source.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GUILDS_ADMIN =
            (ctx, builder) -> {
                try {
                    MinecraftServer server = ctx.getSource().getServer();
                    if (server == null) return builder.buildFuture();
                    return SharedSuggestionProvider.suggest(
                            GuildSavedData.get(server).getAllGuilds().stream().map(Guild::getName),
                            builder);
                } catch (Exception e) {
                    return builder.buildFuture();
                }
            };

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> guild = Commands.literal("guild")
                .requires(src -> src.isPlayer());

        // Core subcommands
        guild.then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("tag", StringArgumentType.word())
                                        .executes(ctx -> create(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "tag"))))))

                .then(Commands.literal("disband")
                        .executes(ctx -> disband(ctx.getSource())))

                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> invite(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))

                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(SUGGEST_GUILD_MEMBERS)
                                .executes(ctx -> kick(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player")))))

                .then(Commands.literal("join")
                        .then(Commands.argument("guildName", StringArgumentType.word())
                                .suggests(SUGGEST_ALL_GUILDS)
                                .executes(ctx -> join(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "guildName")))))

                .then(Commands.literal("leave")
                        .executes(ctx -> leave(ctx.getSource())))

                // Changed from greedyString to word so suggestions work; guild names are single words
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource(), null))
                        .then(Commands.argument("guild", StringArgumentType.word())
                                .suggests(SUGGEST_ALL_GUILDS)
                                .executes(ctx -> info(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "guild")))))

                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))

                .then(Commands.literal("sethome")
                        .executes(ctx -> sethome(ctx.getSource())))

                .then(Commands.literal("home")
                        .executes(ctx -> home(ctx.getSource())))

                .then(Commands.literal("log")
                        .executes(ctx -> log(ctx.getSource())))

                .then(Commands.literal("ff")
                        .then(Commands.literal("on").executes(ctx -> ff(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> ff(ctx.getSource(), false))))

                .then(Commands.literal("motd")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> motd(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "message")))))

                // Chat shortcuts (also registered as top-level /gc and /goc)
                .then(Commands.literal("chat")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> guildChat(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "message")))))

                // ── Admin: /guild xp <guildName> <amount>  (OP level 2) ────────────────
                // Works from in-game or server console; accepts negative values to subtract XP.
                .then(Commands.literal("xp")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("guildName", StringArgumentType.word())
                                .suggests(SUGGEST_GUILDS_ADMIN)
                                .then(Commands.argument("amount", LongArgumentType.longArg())
                                        .executes(ctx -> adminGiveXp(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "guildName"),
                                                LongArgumentType.getLong(ctx, "amount"))))))

                // ── Admin: /guild arena [edit|reset]  (OP level 2) ────────────────────
                //   /guild arena        — regenerate the TEST arena (negative slot) and tp in
                //   /guild arena edit   — tp to the PRODUCTION arena WITHOUT regenerating;
                //                         enables custom-arena mode so wars use this build
                //   /guild arena reset  — regenerate the PRODUCTION arena with the default
                //                         Colosseum and disable custom-arena mode
                .then(Commands.literal("arena")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> adminArena(ctx.getSource()))
                        .then(Commands.literal("edit")
                                .executes(ctx -> adminArenaEdit(ctx.getSource())))
                        .then(Commands.literal("reset")
                                .executes(ctx -> adminArenaReset(ctx.getSource()))));

        // Register subcommand groups
        GuildBankCommand.register(guild);
        GuildRankCommand.register(guild);
        GuildUpgradeCommand.register(guild);
        GuildAllyCommand.register(guild);
        GuildWarCommand.register(guild);
        GuildLeaderboardCommand.register(guild);

        dispatcher.register(guild);

        // /gc <message> shortcut
        dispatcher.register(Commands.literal("gc")
                .requires(src -> src.isPlayer())
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> guildChat(ctx.getSource(),
                                StringArgumentType.getString(ctx, "message")))));

        // /goc <message> shortcut
        dispatcher.register(Commands.literal("goc")
                .requires(src -> src.isPlayer())
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> officerChat(ctx.getSource(),
                                StringArgumentType.getString(ctx, "message")))));
    }

    // ── /guild create <name> <tag> ────────────────────────────────────────────

    private static int create(CommandSourceStack src, String name, String tag) {
        try {
            ServerPlayer player = src.getPlayerOrException();

            if (name.length() > GuildConfig.MAX_GUILD_NAME_LENGTH.get()) {
                src.sendFailure(MessageUtils.error("Guild name too long (max " + GuildConfig.MAX_GUILD_NAME_LENGTH.get() + " chars).")); return 0;
            }
            if (tag.length() > GuildConfig.MAX_GUILD_TAG_LENGTH.get()) {
                src.sendFailure(MessageUtils.error("Guild tag too long (max " + GuildConfig.MAX_GUILD_TAG_LENGTH.get() + " chars).")); return 0;
            }
            if (GuildUtils.isInGuild(player)) {
                src.sendFailure(MessageUtils.error("You are already in a guild. Leave first.")); return 0;
            }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            if (data.getGuildByName(name) != null) {
                src.sendFailure(MessageUtils.error("A guild named '" + name + "' already exists.")); return 0;
            }

            Guild guild = data.createGuild(name, tag, player);
            if (guild == null) { src.sendFailure(MessageUtils.error("Failed to create guild.")); return 0; }

            src.sendSuccess(() -> MessageUtils.success("Guild '" + name + "' [" + tag + "] created! You are the Leader."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild disband ────────────────────────────────────────────────────────

    private static int disband(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!player.getUUID().equals(guild.getLeaderUUID())) {
                src.sendFailure(MessageUtils.error("Only the guild leader can disband the guild.")); return 0;
            }

            MinecraftServer server = player.getServer();
            MessageUtils.broadcastToGuild(guild, MessageUtils.warn("The guild has been disbanded by the leader."), server);
            GuildSavedData.get(server).disbandGuild(guild.getGuildId(), server);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild invite <player> ────────────────────────────────────────────────

    private static int invite(CommandSourceStack src, ServerPlayer target) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.INVITE)) {
                src.sendFailure(MessageUtils.error("You don't have permission to invite players.")); return 0;
            }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            if (data.isInGuild(target.getUUID())) {
                src.sendFailure(MessageUtils.error(target.getName().getString() + " is already in a guild.")); return 0;
            }

            // Track invite in a simple static map (invitation expires after 60s via tick check)
            InviteTracker.addInvite(target.getUUID(), guild.getGuildId());

            target.sendSystemMessage(MessageUtils.warn("You have been invited to join guild '"
                    + guild.getName() + "' by " + player.getName().getString()
                    + ". Type /guild join " + guild.getName() + " to accept."));
            src.sendSuccess(() -> MessageUtils.success("Invite sent to " + target.getName().getString() + "."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild join <guildName> ───────────────────────────────────────────────

    private static int join(CommandSourceStack src, String guildName) {
        try {
            ServerPlayer player = src.getPlayerOrException();

            if (GuildUtils.isInGuild(player)) {
                src.sendFailure(MessageUtils.error("You are already in a guild. Leave first.")); return 0;
            }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            Guild targetGuild = data.getGuildByName(guildName);
            if (targetGuild == null) {
                src.sendFailure(MessageUtils.error("Guild '" + guildName + "' not found.")); return 0;
            }

            // Verify the player has a valid (non-expired) invite from this guild
            java.util.UUID invitedGuildId = InviteTracker.getInvitedGuild(player.getUUID());
            if (invitedGuildId == null || !invitedGuildId.equals(targetGuild.getGuildId())) {
                src.sendFailure(MessageUtils.error("You don't have a pending invite to '" + guildName + "', or it has expired.")); return 0;
            }

            boolean added = data.addMemberToGuild(targetGuild.getGuildId(), player);
            if (!added) {
                src.sendFailure(MessageUtils.error("Could not join '" + guildName + "'. The guild may be full.")); return 0;
            }

            InviteTracker.clearInvite(player.getUUID());

            src.sendSuccess(() -> MessageUtils.success("You joined " + targetGuild.getName() + " [" + targetGuild.getTag() + "]!"), false);
            MessageUtils.broadcastToGuild(targetGuild,
                    MessageUtils.info(player.getName().getString() + " joined the guild!"),
                    player.getServer());
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild kick <player> ──────────────────────────────────────────────────

    private static int kick(CommandSourceStack src, String targetName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.KICK)) {
                src.sendFailure(MessageUtils.error("You don't have permission to kick members.")); return 0;
            }

            GuildMember target = guild.getAllMembers().stream()
                    .filter(m -> m.getPlayerName().equalsIgnoreCase(targetName))
                    .findFirst().orElse(null);
            if (target == null) { src.sendFailure(MessageUtils.error(targetName + " is not in your guild.")); return 0; }
            if (target.getPlayerUUID().equals(guild.getLeaderUUID())) {
                src.sendFailure(MessageUtils.error("You cannot kick the guild leader.")); return 0;
            }

            // Cannot kick someone of equal or higher rank
            GuildRank myRank     = guild.getMemberRank(player.getUUID());
            GuildRank targetRank = guild.getMemberRank(target.getPlayerUUID());
            if (myRank != null && targetRank != null && targetRank.getPriority() >= myRank.getPriority()
                    && !player.getUUID().equals(guild.getLeaderUUID())) {
                src.sendFailure(MessageUtils.error("You cannot kick someone of equal or higher rank.")); return 0;
            }

            MinecraftServer server = player.getServer();
            ServerPlayer onlineTarget = server.getPlayerList().getPlayer(target.getPlayerUUID());
            if (onlineTarget != null)
                onlineTarget.sendSystemMessage(MessageUtils.warn("You have been kicked from " + guild.getName() + "."));

            UUID removedUUID = target.getPlayerUUID();
            GuildSavedData.get(server).removeMemberFromGuild(removedUUID, "was kicked by " + player.getName().getString());
            FTBBridge.removePlayerFromGuildTeam(guild, removedUUID, server);
            src.sendSuccess(() -> MessageUtils.success("Kicked " + targetName + " from the guild."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild leave ──────────────────────────────────────────────────────────

    private static int leave(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (player.getUUID().equals(guild.getLeaderUUID())) {
                src.sendFailure(MessageUtils.error("You are the leader. Transfer leadership or disband with /guild disband.")); return 0;
            }

            GuildSavedData.get(player.getServer()).removeMemberFromGuild(player.getUUID(), "left the guild");
            FTBBridge.removePlayerFromGuildTeam(guild, player.getUUID(), player.getServer());
            src.sendSuccess(() -> MessageUtils.success("You left " + guild.getName() + "."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild info [guild] ───────────────────────────────────────────────────

    private static int info(CommandSourceStack src, String guildName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            GuildSavedData data = GuildSavedData.get(player.getServer());
            Guild guild = guildName == null ? GuildUtils.getGuildOf(player) : data.getGuildByName(guildName);

            if (guild == null) {
                src.sendFailure(MessageUtils.error(guildName == null ? "You are not in a guild." : "Guild '" + guildName + "' not found.")); return 0;
            }

            List<ServerPlayer> online = GuildUtils.getOnlineMembers(guild, player.getServer());
            String leaderName = guild.getMember(guild.getLeaderUUID()) != null
                    ? guild.getMember(guild.getLeaderUUID()).getPlayerName() : "Unknown";

            src.sendSuccess(() -> MessageUtils.separator(), false);
            src.sendSuccess(() -> Component.literal("  " + guild.getName() + " [" + guild.getTag() + "]  — Level " + guild.getLevel())
                    .withStyle(MessageUtils.GOLD), false);
            if (!guild.getMotd().isEmpty())
                src.sendSuccess(() -> Component.literal("  \"" + guild.getMotd() + "\"").withStyle(MessageUtils.GRAY), false);
            src.sendSuccess(() -> Component.literal("  Leader: ").withStyle(MessageUtils.GOLD)
                    .append(Component.literal(leaderName).withStyle(MessageUtils.YELLOW)), false);
            src.sendSuccess(() -> Component.literal("  Members: ").withStyle(MessageUtils.GOLD)
                    .append(Component.literal(guild.getMemberCount() + " | " + online.size() + " online").withStyle(MessageUtils.WHITE)), false);
            src.sendSuccess(() -> Component.literal("  XP: ").withStyle(MessageUtils.GOLD)
                    .append(Component.literal(guild.getExperience() + " | Next level: "
                            + guild.xpToNextLevel(GuildConfig.MAX_GUILD_LEVEL.get()) + " XP").withStyle(MessageUtils.WHITE)), false);
            src.sendSuccess(() -> Component.literal("  Friendly Fire: ").withStyle(MessageUtils.GOLD)
                    .append(Component.literal(guild.isFriendlyFire() ? "ON" : "OFF")
                            .withStyle(guild.isFriendlyFire() ? MessageUtils.GREEN : MessageUtils.RED)), false);
            src.sendSuccess(() -> Component.literal("  Allies: " + guild.getAlliedGuildIds().size()
                    + " | Upgrades: " + guild.getPurchasedUpgrades().size()).withStyle(MessageUtils.GRAY), false);
            src.sendSuccess(() -> MessageUtils.separator(), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild list ───────────────────────────────────────────────────────────

    private static int list(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            GuildSavedData data = GuildSavedData.get(player.getServer());

            src.sendSuccess(() -> MessageUtils.header("All Guilds"), false);
            if (data.getAllGuilds().isEmpty()) {
                src.sendSuccess(() -> MessageUtils.info("No guilds exist yet."), false);
            } else {
                for (Guild g : data.getAllGuilds()) {
                    int online = GuildUtils.getOnlineMembers(g, player.getServer()).size();
                    src.sendSuccess(() -> Component.literal("  [" + g.getTag() + "] ")
                            .withStyle(MessageUtils.GOLD)
                            .append(Component.literal(g.getName() + " — Lv." + g.getLevel()
                                    + " | " + g.getMemberCount() + " members (" + online + " online)").withStyle(MessageUtils.WHITE)), false);
                }
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild sethome ────────────────────────────────────────────────────────

    private static int sethome(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.SET_HOME)) {
                src.sendFailure(MessageUtils.error("You don't have permission to set the guild home.")); return 0;
            }

            guild.setHomePos(player.blockPosition());
            guild.setHomeDimensionId(player.level().dimension().location().toString());
            GuildSavedData.get(player.getServer()).setDirty();
            guild.addLog(player.getName().getString() + " set the guild home.");
            src.sendSuccess(() -> MessageUtils.success("Guild home set at your current location."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild home ───────────────────────────────────────────────────────────

    private static int home(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (guild.getHomePos() == null) { src.sendFailure(MessageUtils.error("No guild home set. Use /guild sethome first.")); return 0; }

            if (GuildUtils.isOnHomeCooldown(guild, player.getUUID())) {
                src.sendFailure(MessageUtils.error("Home cooldown: " + GuildUtils.homeCooldownRemainingSeconds(guild, player.getUUID()) + "s remaining.")); return 0;
            }

            guild.getHomeCooldowns().put(player.getUUID(), System.currentTimeMillis());
            GuildUtils.teleportPlayer(player, guild.getHomePos(), guild.getHomeDimensionId());
            src.sendSuccess(() -> MessageUtils.success("Teleporting to guild home..."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild log ────────────────────────────────────────────────────────────

    private static int log(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            src.sendSuccess(() -> MessageUtils.header("Guild Log — " + guild.getName()), false);
            List<String> entries = guild.getActivityLog();
            if (entries.isEmpty()) {
                src.sendSuccess(() -> MessageUtils.info("No activity logged yet."), false);
            } else {
                entries.stream().limit(15).forEach(entry ->
                        src.sendSuccess(() -> Component.literal("  " + entry).withStyle(MessageUtils.GRAY), false));
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild ff <on|off> ────────────────────────────────────────────────────

    private static int ff(CommandSourceStack src, boolean enabled) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.TOGGLE_FF)) {
                src.sendFailure(MessageUtils.error("You don't have permission to toggle friendly fire.")); return 0;
            }

            guild.setFriendlyFire(enabled);
            GuildSavedData.get(player.getServer()).setDirty();
            guild.addLog(player.getName().getString() + " turned friendly fire " + (enabled ? "ON" : "OFF") + ".");
            MessageUtils.broadcastToGuild(guild, MessageUtils.warn("Friendly fire is now " + (enabled ? "§aON" : "§cOFF") + "."), player.getServer());
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild motd <message> ─────────────────────────────────────────────────

    private static int motd(CommandSourceStack src, String message) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_RANKS)) {
                src.sendFailure(MessageUtils.error("You need rank management permission to set the MOTD.")); return 0;
            }

            guild.setMotd(message);
            GuildSavedData.get(player.getServer()).setDirty();
            src.sendSuccess(() -> MessageUtils.success("MOTD set: \"" + message + "\""), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /gc <message> ─────────────────────────────────────────────────────────

    private static int guildChat(CommandSourceStack src, String message) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            MessageUtils.sendGuildChat(guild, player, message, player.getServer());
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /goc <message> ────────────────────────────────────────────────────────

    private static int officerChat(CommandSourceStack src, String message) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            GuildRank rank = guild.getMemberRank(player.getUUID());
            if (rank == null || rank.getPriority() < 300) {
                src.sendFailure(MessageUtils.error("You must be Officer or higher to use officer chat.")); return 0;
            }
            MessageUtils.sendOfficerChat(guild, player, message, player.getServer());
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── [ADMIN] /guild xp <guildName> <amount> ────────────────────────────────

    private static int adminGiveXp(CommandSourceStack src, String guildName, long amount) {
        try {
            MinecraftServer server = src.getServer();
            if (server == null) { src.sendFailure(MessageUtils.error("No server available.")); return 0; }

            GuildSavedData data = GuildSavedData.get(server);
            Guild guild = data.getGuildByName(guildName);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("Guild '" + guildName + "' not found."));
                return 0;
            }

            int  maxLevel   = GuildConfig.MAX_GUILD_LEVEL.get();
            int  levelBefore = guild.getLevel();
            long xpBefore   = guild.getExperience();
            long newXp      = xpBefore + amount; // setExperienceAndRecalculate clamps to ≥ 0

            int levelDelta = guild.setExperienceAndRecalculate(newXp, maxLevel);

            data.setDirty();
            guild.addLog("[Admin] " + src.getTextName()
                    + " adjusted XP by " + (amount >= 0 ? "+" : "") + amount
                    + "  (was Lv." + levelBefore + " / " + xpBefore + " XP).");

            // Broadcast level-up or level-down message to all online guild members
            if (levelDelta > 0) {
                MessageUtils.broadcastToGuild(guild,
                        MessageUtils.success("Guild leveled up to level " + guild.getLevel() + "!"),
                        server);
            } else if (levelDelta < 0) {
                MessageUtils.broadcastToGuild(guild,
                        MessageUtils.warn("Guild dropped to level " + guild.getLevel() + "."),
                        server);
            }

            long xpToNext = guild.xpToNextLevel(maxLevel);
            String nextInfo = (guild.getLevel() >= maxLevel)
                    ? "MAX LEVEL"
                    : xpToNext + " XP to next level";

            final long finalXp    = guild.getExperience();
            final int  finalLevel = guild.getLevel();
            final int  delta      = levelDelta;
            src.sendSuccess(() -> Component.literal(
                    "[Guild XP] " + guild.getName()
                    + "  |  XP: " + finalXp
                    + "  |  Level: " + finalLevel
                    + (delta != 0
                            ? "  (" + (delta > 0 ? "+" : "") + delta + " level" + (Math.abs(delta) > 1 ? "s" : "") + ")"
                            : "")
                    + "  |  " + nextInfo)
                    .withStyle(MessageUtils.GREEN), true);

        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); }
        return 1;
    }

    // ── [ADMIN] /guild arena ──────────────────────────────────────────────────
    //
    // TEST slot  (cx = -300): used by plain /guild arena for safe isolated testing.
    //   Negative X ensures no overlap with real war slots (slot 0 = cx 0, slot 1 = cx 250 …).
    //   Each /guild arena call regenerates it fresh.
    //
    // PRODUCTION slot (cx = 0): the permanent arena used by real wars.
    //   /guild arena edit  — enters it without regenerating; enables custom-arena mode.
    //   /guild arena reset — regenerates it back to the default Colosseum; disables custom mode.

    private static final int TEST_ARENA_CX = -300;
    private static final int TEST_ARENA_CZ = 0;

    private static int adminArena(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) { src.sendFailure(MessageUtils.error("No server available.")); return 0; }

            net.minecraft.server.level.ServerLevel arenaLevel =
                    server.getLevel(ArenaManager.ARENA_DIMENSION);
            if (arenaLevel == null) {
                src.sendFailure(MessageUtils.error(
                        "Arena dimension not found. Make sure guild_arena is registered correctly."));
                return 0;
            }

            // (Re-)generate the arena at the reserved test coordinates
            ArenaGenerator.generate(arenaLevel, TEST_ARENA_CX, TEST_ARENA_CZ);

            // Teleport the player to Team A's spawn
            net.minecraft.core.BlockPos spawn =
                    ArenaGenerator.getTeamASpawn(TEST_ARENA_CX, TEST_ARENA_CZ);
            GuildUtils.teleportPlayer(player, spawn, ArenaManager.ARENA_DIMENSION_ID);

            src.sendSuccess(() -> MessageUtils.success(
                    "Test arena generated at (" + TEST_ARENA_CX + ", "
                            + ArenaGenerator.FLOOR_Y + ", " + TEST_ARENA_CZ
                            + "). Teleporting to spawn A…"), false);

        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); }
        return 1;
    }

    // ── [ADMIN] /guild arena edit ─────────────────────────────────────────────
    //
    // Teleports to the production arena (cx=0) WITHOUT regenerating it, so any
    // blocks already placed there are preserved.  Enables custom-arena mode so
    // that future guild wars use this build instead of generating a fresh one.

    private static int adminArenaEdit(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) { src.sendFailure(MessageUtils.error("No server available.")); return 0; }

            net.minecraft.server.level.ServerLevel arenaLevel =
                    server.getLevel(ArenaManager.ARENA_DIMENSION);
            if (arenaLevel == null) {
                src.sendFailure(MessageUtils.error(
                        "Arena dimension not found. Is guild_arena registered correctly?"));
                return 0;
            }

            // Enable custom-arena mode — wars will now use this arena without regenerating
            GuildSavedData.get(server).setCustomArena(true);

            // Teleport to the production arena (cx=0) without touching any blocks
            net.minecraft.core.BlockPos spawn =
                    ArenaGenerator.getTeamASpawn(ArenaManager.CUSTOM_ARENA_CX, ArenaManager.CUSTOM_ARENA_CZ);
            GuildUtils.teleportPlayer(player, spawn, ArenaManager.ARENA_DIMENSION_ID);

            src.sendSuccess(() -> MessageUtils.success(
                    "Arena edit mode ON. Teleporting to the production arena — your changes save automatically."), false);
            src.sendSuccess(() -> MessageUtils.warn(
                    "Wars will now use this custom arena. Run /guild arena reset to restore the default Colosseum."), false);

        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); }
        return 1;
    }

    // ── [ADMIN] /guild arena reset ────────────────────────────────────────────
    //
    // Regenerates the production arena (cx=0) with the default Colosseum layout,
    // wiping any custom edits.  Disables custom-arena mode so wars go back to
    // generating fresh arenas per match.

    private static int adminArenaReset(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) { src.sendFailure(MessageUtils.error("No server available.")); return 0; }

            net.minecraft.server.level.ServerLevel arenaLevel =
                    server.getLevel(ArenaManager.ARENA_DIMENSION);
            if (arenaLevel == null) {
                src.sendFailure(MessageUtils.error(
                        "Arena dimension not found. Is guild_arena registered correctly?"));
                return 0;
            }

            // Regenerate the production arena from scratch
            ArenaGenerator.generate(arenaLevel, ArenaManager.CUSTOM_ARENA_CX, ArenaManager.CUSTOM_ARENA_CZ);

            // Disable custom-arena mode — wars will generate fresh arenas again
            GuildSavedData.get(server).setCustomArena(false);

            // Teleport the admin in to inspect the freshly built arena
            net.minecraft.core.BlockPos spawn =
                    ArenaGenerator.getTeamASpawn(ArenaManager.CUSTOM_ARENA_CX, ArenaManager.CUSTOM_ARENA_CZ);
            GuildUtils.teleportPlayer(player, spawn, ArenaManager.ARENA_DIMENSION_ID);

            src.sendSuccess(() -> MessageUtils.success(
                    "Production arena reset to default Colosseum. Custom arena mode DISABLED."), false);
            src.sendSuccess(() -> MessageUtils.warn(
                    "Wars will now generate a fresh arena per match. Use /guild arena edit to re-enable custom mode."), false);

        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); }
        return 1;
    }
}
