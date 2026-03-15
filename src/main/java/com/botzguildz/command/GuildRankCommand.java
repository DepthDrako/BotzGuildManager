package com.botzguildz.command;

import com.botzguildz.data.*;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.EnumSet;

public class GuildRankCommand {

    // ── Suggestion providers ───────────────────────────────────────────────────

    /** Suggests all rank names in the player's guild. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_RANKS =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Guild guild = GuildUtils.getGuildOf(player);
                    if (guild == null) return builder.buildFuture();
                    return SharedSuggestionProvider.suggest(
                            guild.getRanks().stream().map(GuildRank::getName),
                            builder);
                } catch (Exception e) {
                    return builder.buildFuture();
                }
            };

    /** Suggests all RankPermission enum names. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PERMISSIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(RankPermission.values()).map(Enum::name),
                    builder);

    /** Suggests guild member names (excluding the leader, who can't be re-ranked). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_MEMBERS =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Guild guild = GuildUtils.getGuildOf(player);
                    if (guild == null) return builder.buildFuture();
                    return SharedSuggestionProvider.suggest(
                            guild.getAllMembers().stream()
                                    .filter(m -> !m.getPlayerUUID().equals(guild.getLeaderUUID()))
                                    .map(GuildMember::getPlayerName),
                            builder);
                } catch (Exception e) {
                    return builder.buildFuture();
                }
            };

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild.then(Commands.literal("rank")
                .requires(src -> src.isPlayer())

                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))

                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("priority", IntegerArgumentType.integer(1, 999))
                                        .executes(ctx -> create(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "priority"))))))

                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(SUGGEST_RANKS)
                                .executes(ctx -> delete(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))

                .then(Commands.literal("rename")
                        .then(Commands.argument("old", StringArgumentType.word())
                                .suggests(SUGGEST_RANKS)
                                .then(Commands.argument("new", StringArgumentType.word())
                                        .executes(ctx -> rename(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "old"),
                                                StringArgumentType.getString(ctx, "new"))))))

                .then(Commands.literal("setperm")
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests(SUGGEST_RANKS)
                                .then(Commands.argument("perm", StringArgumentType.word())
                                        .suggests(SUGGEST_PERMISSIONS)
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setPerm(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "rank"),
                                                        StringArgumentType.getString(ctx, "perm"),
                                                        BoolArgumentType.getBool(ctx, "value")))))))

                .then(Commands.literal("setdefault")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(SUGGEST_RANKS)
                                .executes(ctx -> setDefault(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))

                .then(Commands.literal("set")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(SUGGEST_MEMBERS)
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests(SUGGEST_RANKS)
                                        .executes(ctx -> setPlayerRank(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "rank"))))))
        );
    }

    // ── /guild rank list ──────────────────────────────────────────────────────

    private static int list(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            src.sendSuccess(() -> MessageUtils.header("Ranks — " + guild.getName()), false);
            for (GuildRank rank : guild.getRanks()) {
                StringBuilder perms = new StringBuilder();
                for (RankPermission p : rank.getPermissions()) {
                    if (perms.length() > 0) perms.append(", ");
                    perms.append(p.name());
                }
                src.sendSuccess(() -> Component.literal("  [P:" + rank.getPriority() + "] ")
                        .withStyle(MessageUtils.GOLD)
                        .append(Component.literal(rank.getName() + (rank.isDefault() ? " (default)" : ""))
                                .withStyle(MessageUtils.YELLOW))
                        .append(Component.literal("\n    Perms: " + (perms.length() > 0 ? perms : "none"))
                                .withStyle(MessageUtils.GRAY)), false);
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild rank create ────────────────────────────────────────────────────

    private static int create(CommandSourceStack src, String name, int priority) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_RANKS)) {
                src.sendFailure(MessageUtils.error("You don't have permission to manage ranks.")); return 0;
            }
            if (guild.getRank(name) != null) {
                src.sendFailure(MessageUtils.error("A rank with that name already exists.")); return 0;
            }
            guild.addRank(new GuildRank(name, priority, false, EnumSet.noneOf(RankPermission.class)));
            GuildSavedData.get(player.getServer()).setDirty();
            src.sendSuccess(() -> MessageUtils.success("Created rank '" + name + "' with priority " + priority + "."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild rank delete ────────────────────────────────────────────────────

    private static int delete(CommandSourceStack src, String name) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_RANKS)) {
                src.sendFailure(MessageUtils.error("You don't have permission to manage ranks.")); return 0;
            }
            if (name.equalsIgnoreCase("Leader")) {
                src.sendFailure(MessageUtils.error("The Leader rank cannot be deleted.")); return 0;
            }

            // Reassign members on the deleted rank to the default rank
            GuildRank defaultRank = guild.getDefaultRank();
            String fallback = defaultRank != null ? defaultRank.getName() : "Recruit";
            for (GuildMember member : guild.getAllMembers()) {
                if (member.getRankName().equalsIgnoreCase(name)) member.setRankName(fallback);
            }

            if (!guild.removeRank(name)) {
                src.sendFailure(MessageUtils.error("Rank '" + name + "' not found.")); return 0;
            }
            GuildSavedData.get(player.getServer()).setDirty();
            src.sendSuccess(() -> MessageUtils.success("Deleted rank '" + name + "'. Members reassigned to '" + fallback + "'."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild rank rename ────────────────────────────────────────────────────

    private static int rename(CommandSourceStack src, String oldName, String newName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_RANKS)) {
                src.sendFailure(MessageUtils.error("You don't have permission to manage ranks.")); return 0;
            }
            GuildRank rank = guild.getRank(oldName);
            if (rank == null) { src.sendFailure(MessageUtils.error("Rank '" + oldName + "' not found.")); return 0; }
            if (oldName.equalsIgnoreCase("Leader")) { src.sendFailure(MessageUtils.error("Cannot rename the Leader rank.")); return 0; }

            // Update members' rankName references
            for (GuildMember member : guild.getAllMembers()) {
                if (member.getRankName().equalsIgnoreCase(oldName)) member.setRankName(newName);
            }
            rank.setName(newName);
            GuildSavedData.get(player.getServer()).setDirty();
            src.sendSuccess(() -> MessageUtils.success("Renamed rank '" + oldName + "' to '" + newName + "'."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild rank setperm ───────────────────────────────────────────────────

    private static int setPerm(CommandSourceStack src, String rankName, String permName, boolean value) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_RANKS)) {
                src.sendFailure(MessageUtils.error("You don't have permission to manage ranks.")); return 0;
            }
            GuildRank rank = guild.getRank(rankName);
            if (rank == null) { src.sendFailure(MessageUtils.error("Rank '" + rankName + "' not found.")); return 0; }

            RankPermission perm;
            try { perm = RankPermission.valueOf(permName.toUpperCase()); }
            catch (IllegalArgumentException e) {
                src.sendFailure(MessageUtils.error("Unknown permission '" + permName + "'. Valid: " + Arrays.toString(RankPermission.values())));
                return 0;
            }

            if (value) rank.addPermission(perm); else rank.removePermission(perm);
            GuildSavedData.get(player.getServer()).setDirty();
            src.sendSuccess(() -> MessageUtils.success(
                    (value ? "Granted " : "Revoked ") + perm.name() + " for rank '" + rankName + "'."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild rank setdefault ────────────────────────────────────────────────

    private static int setDefault(CommandSourceStack src, String rankName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_RANKS)) {
                src.sendFailure(MessageUtils.error("You don't have permission to manage ranks.")); return 0;
            }
            GuildRank target = guild.getRank(rankName);
            if (target == null) { src.sendFailure(MessageUtils.error("Rank '" + rankName + "' not found.")); return 0; }

            for (GuildRank r : guild.getRanks()) r.setDefault(false);
            target.setDefault(true);
            GuildSavedData.get(player.getServer()).setDirty();
            src.sendSuccess(() -> MessageUtils.success("'" + rankName + "' is now the default rank for new members."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild rank set <player> <rank> ──────────────────────────────────────

    private static int setPlayerRank(CommandSourceStack src, String targetName, String rankName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_RANKS)) {
                src.sendFailure(MessageUtils.error("You don't have permission to manage ranks.")); return 0;
            }

            // Find target member by name
            GuildMember target = guild.getAllMembers().stream()
                    .filter(m -> m.getPlayerName().equalsIgnoreCase(targetName))
                    .findFirst().orElse(null);
            if (target == null) { src.sendFailure(MessageUtils.error(targetName + " is not in your guild.")); return 0; }
            if (target.getPlayerUUID().equals(guild.getLeaderUUID())) {
                src.sendFailure(MessageUtils.error("Cannot change the rank of the guild leader.")); return 0;
            }

            GuildRank newRank = guild.getRank(rankName);
            if (newRank == null) { src.sendFailure(MessageUtils.error("Rank '" + rankName + "' not found.")); return 0; }

            // Cannot promote someone to a rank of equal or higher priority than yourself (unless leader)
            GuildRank myRank = guild.getMemberRank(player.getUUID());
            if (myRank != null && newRank.getPriority() >= myRank.getPriority()
                    && !player.getUUID().equals(guild.getLeaderUUID())) {
                src.sendFailure(MessageUtils.error("You cannot assign a rank equal to or higher than your own."));
                return 0;
            }

            target.setRankName(newRank.getName());
            GuildSavedData.get(player.getServer()).setDirty();
            guild.addLog(player.getName().getString() + " set " + target.getPlayerName() + "'s rank to " + newRank.getName() + ".");
            src.sendSuccess(() -> MessageUtils.success(target.getPlayerName() + " is now " + newRank.getName() + "."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }
}
