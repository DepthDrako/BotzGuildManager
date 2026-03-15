package com.botzguildz.command;

import com.botzguildz.raid.RaidBossFight;
import com.botzguildz.raid.RaidManager;
import com.botzguildz.raid.RaidParty;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public class RaidCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raid")
                .requires(CommandSourceStack::isPlayer)

                .then(Commands.literal("party")

                        .then(Commands.literal("create")
                                .executes(ctx -> create(ctx.getSource())))

                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> invite(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))

                        .then(Commands.literal("accept")
                                .executes(ctx -> accept(ctx.getSource())))

                        .then(Commands.literal("decline")
                                .executes(ctx -> decline(ctx.getSource())))

                        .then(Commands.literal("leave")
                                .executes(ctx -> leave(ctx.getSource())))

                        .then(Commands.literal("disband")
                                .executes(ctx -> disband(ctx.getSource())))

                        .then(Commands.literal("kick")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> kick(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))

                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                )
        );
    }

    // ── /raid party create ────────────────────────────────────────────────

    private static int create(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            RaidManager.Result result = RaidManager.INSTANCE.createParty(player.getUUID());
            switch (result) {
                case CREATE_OK ->
                        src.sendSuccess(() -> MessageUtils.success("Raid party created! Invite players with /raid party invite <player>."), false);
                case ALREADY_IN_PARTY ->
                        src.sendFailure(MessageUtils.error("You are already in a raid party."));
                default ->
                        src.sendFailure(MessageUtils.error("Could not create party."));
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /raid party invite <player> ───────────────────────────────────────

    private static int invite(CommandSourceStack src, ServerPlayer target) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            if (player.getUUID().equals(target.getUUID())) {
                src.sendFailure(MessageUtils.error("You can't invite yourself.")); return 0;
            }
            RaidManager.Result result = RaidManager.INSTANCE.invite(player.getUUID(), target.getUUID());
            switch (result) {
                case INVITE_SENT -> {
                    src.sendSuccess(() -> MessageUtils.success("Invite sent to " + target.getName().getString() + " (expires in 60s)."), false);
                    target.sendSystemMessage(Component.literal(
                            player.getName().getString() + " invited you to a raid party! Type /raid party accept or /raid party decline.")
                            .withStyle(ChatFormatting.YELLOW));
                }
                case NOT_IN_PARTY ->
                        src.sendFailure(MessageUtils.error("You are not in a raid party. Use /raid party create first."));
                case NOT_LEADER ->
                        src.sendFailure(MessageUtils.error("Only the party leader can invite players."));
                case TARGET_IN_PARTY ->
                        src.sendFailure(MessageUtils.error(target.getName().getString() + " is already in a raid party."));
                case ALREADY_INVITED ->
                        src.sendFailure(MessageUtils.error(target.getName().getString() + " already has a pending invite."));
                default ->
                        src.sendFailure(MessageUtils.error("Could not send invite."));
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /raid party accept ────────────────────────────────────────────────

    private static int accept(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();

            // Grab leader name BEFORE accepting (so we can show it)
            Optional<UUID> leaderIdOpt = RaidManager.INSTANCE.getPendingInviteLeader(player.getUUID());

            RaidManager.Result result = RaidManager.INSTANCE.accept(player.getUUID());
            switch (result) {
                case ACCEPT_OK -> {
                    String leaderName = leaderIdOpt
                            .map(lid -> { ServerPlayer lp = server.getPlayerList().getPlayer(lid);
                                          return lp != null ? lp.getName().getString() : "the leader"; })
                            .orElse("the leader");
                    src.sendSuccess(() -> MessageUtils.success("You joined " + leaderName + "'s raid party!"), false);
                    // Notify the rest of the party
                    RaidManager.INSTANCE.getPartyOf(player.getUUID()).ifPresent(party -> {
                        for (UUID m : party.getMembers()) {
                            if (m.equals(player.getUUID())) continue;
                            RaidManager.INSTANCE.sendMessage(m,
                                    player.getName().getString() + " joined the raid party.", server);
                        }
                    });
                }
                case NO_INVITE ->
                        src.sendFailure(MessageUtils.error("You don't have a pending raid invite."));
                case INVITE_EXPIRED ->
                        src.sendFailure(MessageUtils.error("That invite has expired."));
                case ALREADY_IN_PARTY ->
                        src.sendFailure(MessageUtils.error("You are already in a raid party."));
                default ->
                        src.sendFailure(MessageUtils.error("Could not accept invite."));
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /raid party decline ───────────────────────────────────────────────

    private static int decline(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Optional<UUID> leaderIdOpt = RaidManager.INSTANCE.getPendingInviteLeader(player.getUUID());

            RaidManager.Result result = RaidManager.INSTANCE.decline(player.getUUID());
            switch (result) {
                case DECLINE_OK -> {
                    src.sendSuccess(() -> MessageUtils.info("Invite declined."), false);
                    leaderIdOpt.ifPresent(leaderId ->
                            RaidManager.INSTANCE.sendMessage(leaderId,
                                    player.getName().getString() + " declined your raid invite.",
                                    player.getServer()));
                }
                case NO_INVITE ->
                        src.sendFailure(MessageUtils.error("You don't have a pending raid invite."));
                default ->
                        src.sendFailure(MessageUtils.error("Could not decline invite."));
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /raid party leave ─────────────────────────────────────────────────

    private static int leave(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            RaidManager.Result result = RaidManager.INSTANCE.leave(player.getUUID(), player.getServer());
            switch (result) {
                case LEAVE_OK ->
                        src.sendSuccess(() -> MessageUtils.info("You left the raid party."), false);
                case DISBAND_OK ->
                        src.sendSuccess(() -> MessageUtils.info("Raid party disbanded (you were the leader)."), false);
                case NOT_IN_PARTY ->
                        src.sendFailure(MessageUtils.error("You are not in a raid party."));
                default ->
                        src.sendFailure(MessageUtils.error("Could not leave party."));
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /raid party disband ───────────────────────────────────────────────

    private static int disband(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            RaidManager.Result result = RaidManager.INSTANCE.disband(player.getUUID(), player.getServer());
            switch (result) {
                case DISBAND_OK ->
                        src.sendSuccess(() -> MessageUtils.success("Raid party disbanded."), false);
                case NOT_IN_PARTY ->
                        src.sendFailure(MessageUtils.error("You are not in a raid party."));
                case NOT_LEADER ->
                        src.sendFailure(MessageUtils.error("Only the party leader can disband."));
                default ->
                        src.sendFailure(MessageUtils.error("Could not disband party."));
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /raid party kick <player> ─────────────────────────────────────────

    private static int kick(CommandSourceStack src, ServerPlayer target) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            RaidManager.Result result = RaidManager.INSTANCE.kick(player.getUUID(), target.getUUID(), player.getServer());
            switch (result) {
                case KICK_OK -> {
                    src.sendSuccess(() -> MessageUtils.success(target.getName().getString() + " was kicked from the party."), false);
                    target.sendSystemMessage(Component.literal("You were kicked from the raid party.")
                            .withStyle(ChatFormatting.RED));
                }
                case NOT_IN_PARTY ->
                        src.sendFailure(MessageUtils.error("You are not in a raid party."));
                case NOT_LEADER ->
                        src.sendFailure(MessageUtils.error("Only the party leader can kick players."));
                case TARGET_NOT_IN_PARTY ->
                        src.sendFailure(MessageUtils.error(target.getName().getString() + " is not in your party."));
                default ->
                        src.sendFailure(MessageUtils.error("Could not kick player."));
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /raid party status ────────────────────────────────────────────────

    private static int status(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();

            Optional<RaidParty> partyOpt = RaidManager.INSTANCE.getPartyOf(player.getUUID());
            if (partyOpt.isEmpty()) {
                src.sendFailure(MessageUtils.error("You are not in a raid party.")); return 0;
            }
            RaidParty party = partyOpt.get();

            // Look for an active fight for this party
            Optional<RaidBossFight> fightOpt = Optional.empty();
            for (UUID m : party.getMembers()) {
                // Any member might have triggered the fight; scan via leader
                if (m.equals(party.getLeader())) {
                    // tryClaimBoss stores it under the leader UUID; access via getFight if bossId known
                    // Instead, iterate damage participants to find the fight
                    break;
                }
            }
            // Simpler: walk fightByBoss via public API — check each member's UUID as potential boss
            // Actually RaidManager exposes getFight(bossId) but we don't know the bossId here.
            // We'll print fight info only if we can find it by inspecting via the leader.
            // Since leaderToBoss is private, we expose a helper.
            fightOpt = RaidManager.INSTANCE.getActiveFightForParty(party);

            src.sendSuccess(() -> MessageUtils.header("Raid Party"), false);

            // Leader indicator
            ServerPlayer leader = server.getPlayerList().getPlayer(party.getLeader());
            String leaderName = leader != null ? leader.getName().getString() : party.getLeader().toString().substring(0, 8);
            src.sendSuccess(() -> MessageUtils.info("Leader: " + leaderName), false);

            // Member list
            for (UUID memberId : party.getMembers()) {
                ServerPlayer mp = server.getPlayerList().getPlayer(memberId);
                String name = mp != null ? mp.getName().getString()
                        : server.getProfileCache().get(memberId)
                                 .map(com.mojang.authlib.GameProfile::getName)
                                 .orElse(memberId.toString().substring(0, 8) + "...");
                boolean online = mp != null;

                if (fightOpt.isPresent()) {
                    RaidBossFight fight = fightOpt.get();
                    float dmg   = fight.getDamageDealt(memberId);
                    float share = fight.getShare(memberId) * 100f;
                    String line = "  " + (online ? "●" : "○") + " " + name
                            + "  " + String.format("%.1f", dmg) + " dmg"
                            + "  (" + String.format("%.1f", share) + "%)";
                    src.sendSuccess(() -> Component.literal(line).withStyle(online ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
                } else {
                    String line = "  " + (online ? "●" : "○") + " " + name;
                    src.sendSuccess(() -> Component.literal(line).withStyle(online ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
                }
            }

            if (fightOpt.isPresent()) {
                String bossLine = "Fighting: " + fightOpt.get().getBossName();
                src.sendSuccess(() -> MessageUtils.warn(bossLine), false);
            }

        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }
}
