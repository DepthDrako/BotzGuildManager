package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.DuelData;
import com.botzguildz.data.DuelState;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DuelCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /duel <player> [wager] [lives]
        dispatcher.register(Commands.literal("duel")
                .requires(src -> src.isPlayer())

                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> challenge(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "target"), 0L, 1))
                        .then(Commands.argument("wager", LongArgumentType.longArg(0))
                                .executes(ctx -> challenge(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target"),
                                        LongArgumentType.getLong(ctx, "wager"), 1))
                                .then(Commands.argument("lives", IntegerArgumentType.integer(1, 999))
                                        .executes(ctx -> challenge(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"),
                                                LongArgumentType.getLong(ctx, "wager"),
                                                IntegerArgumentType.getInteger(ctx, "lives"))))))

                .then(Commands.literal("accept")
                        .executes(ctx -> accept(ctx.getSource())))

                .then(Commands.literal("deny")
                        .executes(ctx -> deny(ctx.getSource())))

                .then(Commands.literal("cancel")
                        .executes(ctx -> cancel(ctx.getSource())))

                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))

                .then(Commands.literal("top")
                        .executes(ctx -> topDuels(ctx.getSource())))
        );
    }

    // ── /duel <player> [wager] [lives] ───────────────────────────────────────

    private static int challenge(CommandSourceStack src, ServerPlayer target, long wager, int lives) {
        try {
            ServerPlayer challenger = src.getPlayerOrException();
            if (challenger.getUUID().equals(target.getUUID())) {
                src.sendFailure(MessageUtils.error("You can't duel yourself.")); return 0;
            }

            GuildSavedData data = GuildSavedData.get(challenger.getServer());
            if (data.isInDuel(challenger.getUUID())) {
                src.sendFailure(MessageUtils.error("You are already in a duel.")); return 0;
            }
            if (data.isInDuel(target.getUUID())) {
                src.sendFailure(MessageUtils.error(target.getName().getString() + " is already in a duel.")); return 0;
            }

            // Check wager funds
            if (wager > 0 && CurrencyManager.getBalance(challenger) < wager) {
                src.sendFailure(MessageUtils.error("You don't have enough " + CurrencyManager.currencyName()
                        + ". Need: " + CurrencyManager.format(wager))); return 0;
            }

            DuelData duel = data.createDuel(challenger.getUUID(), target.getUUID(), wager, lives);
            if (duel == null) { src.sendFailure(MessageUtils.error("Could not create duel.")); return 0; }

            String wagerStr = wager > 0 ? " | Wager: " + CurrencyManager.format(wager) + " each" : "";
            MessageUtils.sendDuelMessage(challenger, "Duel challenge sent to " + target.getName().getString()
                    + ". Lives: " + lives + wagerStr);
            MessageUtils.sendDuelMessage(target, challenger.getName().getString() + " challenged you to a duel!"
                    + " Lives: " + lives + wagerStr
                    + " | Type /duel accept or /duel deny. (" + GuildConfig.DUEL_TIMEOUT_SECONDS.get() + "s)");
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /duel accept ──────────────────────────────────────────────────────────

    private static int accept(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            GuildSavedData data = GuildSavedData.get(player.getServer());
            DuelData duel = data.getDuelByPlayer(player.getUUID());

            if (duel == null || duel.getState() != DuelState.PENDING
                    || !duel.getChallengedId().equals(player.getUUID())) {
                src.sendFailure(MessageUtils.error("No pending duel challenge to accept.")); return 0;
            }
            if (duel.isPendingExpired()) {
                data.endDuel(duel, null);
                src.sendFailure(MessageUtils.error("That duel challenge has expired.")); return 0;
            }

            // Lock wagers from both players
            long wager = duel.getWageredAmountPerPlayer();
            if (wager > 0) {
                ServerPlayer challenger = player.getServer().getPlayerList().getPlayer(duel.getChallengerId());
                if (challenger != null && !CurrencyManager.deduct(challenger, wager)) {
                    src.sendFailure(MessageUtils.error("The challenger no longer has enough " + CurrencyManager.currencyName() + ".")); return 0;
                }
                if (!CurrencyManager.deduct(player, wager)) {
                    // Refund challenger if we already deducted
                    ServerPlayer ch = player.getServer().getPlayerList().getPlayer(duel.getChallengerId());
                    if (ch != null) CurrencyManager.give(ch, wager);
                    src.sendFailure(MessageUtils.error("You don't have enough " + CurrencyManager.currencyName()
                            + ". Need: " + CurrencyManager.format(wager))); return 0;
                }
            }

            data.activateDuel(duel, player.blockPosition(), player.blockPosition());

            ServerPlayer challenger = player.getServer().getPlayerList().getPlayer(duel.getChallengerId());
            if (challenger != null) {
                data.activateDuel(duel, challenger.blockPosition(), player.blockPosition());
                MessageUtils.sendDuelMessage(challenger, "Duel accepted by " + player.getName().getString() + "! Fight!");
            }
            MessageUtils.sendDuelMessage(player, "Duel started vs " + (challenger != null ? challenger.getName().getString() : "opponent") + "! Fight!");
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /duel deny ────────────────────────────────────────────────────────────

    private static int deny(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            GuildSavedData data = GuildSavedData.get(player.getServer());
            DuelData duel = data.getDuelByPlayer(player.getUUID());

            if (duel == null || duel.getState() != DuelState.PENDING
                    || !duel.getChallengedId().equals(player.getUUID())) {
                src.sendFailure(MessageUtils.error("No pending duel to deny.")); return 0;
            }

            ServerPlayer challenger = player.getServer().getPlayerList().getPlayer(duel.getChallengerId());
            if (challenger != null) MessageUtils.sendDuelMessage(challenger, player.getName().getString() + " declined your duel.");

            data.endDuel(duel, null);
            src.sendSuccess(() -> MessageUtils.info("Duel declined."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /duel cancel ──────────────────────────────────────────────────────────

    private static int cancel(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            GuildSavedData data = GuildSavedData.get(player.getServer());
            DuelData duel = data.getDuelByPlayer(player.getUUID());

            if (duel == null || duel.getState() != DuelState.PENDING
                    || !duel.getChallengerId().equals(player.getUUID())) {
                src.sendFailure(MessageUtils.error("No pending duel challenge to cancel.")); return 0;
            }

            ServerPlayer challenged = player.getServer().getPlayerList().getPlayer(duel.getChallengedId());
            if (challenged != null) MessageUtils.sendDuelMessage(challenged, player.getName().getString() + " cancelled their duel challenge.");

            data.endDuel(duel, null);
            src.sendSuccess(() -> MessageUtils.info("Duel challenge cancelled."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /duel status ──────────────────────────────────────────────────────────

    private static int status(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            GuildSavedData data = GuildSavedData.get(player.getServer());
            DuelData duel = data.getDuelByPlayer(player.getUUID());

            if (duel == null) { src.sendFailure(MessageUtils.error("You are not in a duel.")); return 0; }

            MinecraftServer server = player.getServer();
            ServerPlayer challenger = server.getPlayerList().getPlayer(duel.getChallengerId());
            ServerPlayer challenged = server.getPlayerList().getPlayer(duel.getChallengedId());
            String cName  = challenger != null ? challenger.getName().getString() : duel.getChallengerId().toString();
            String dName  = challenged != null ? challenged.getName().getString() : duel.getChallengedId().toString();

            src.sendSuccess(() -> MessageUtils.header("Duel Status"), false);
            src.sendSuccess(() -> MessageUtils.info(cName + " vs " + dName), false);
            if (duel.getWageredAmountPerPlayer() > 0)
                src.sendSuccess(() -> MessageUtils.info("Wager: " + CurrencyManager.format(duel.getWageredAmountPerPlayer()) + " each | Pot: " + CurrencyManager.format(duel.getTotalPot())), false);
            src.sendSuccess(() -> MessageUtils.info(cName + ": " + duel.getChallengerKills() + " kills, " + duel.getChallengerLivesLeft() + " lives left"), false);
            src.sendSuccess(() -> MessageUtils.info(dName + ": " + duel.getChallengedKills() + " kills, " + duel.getChallengedLivesLeft() + " lives left"), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /duel top ─────────────────────────────────────────────────────────────

    private static int topDuels(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            GuildSavedData data = GuildSavedData.get(server);
            List<Map.Entry<UUID, Integer>> top = data.getTopPlayersByDuelWins(10);

            src.sendSuccess(() -> MessageUtils.header("Top Players — Duel Wins"), false);

            if (top.isEmpty()) {
                src.sendSuccess(() -> MessageUtils.info("No duels have been won yet."), false);
                return 1;
            }

            for (int i = 0; i < top.size(); i++) {
                int pos = i + 1;
                Map.Entry<UUID, Integer> entry = top.get(i);
                UUID uuid = entry.getKey();
                int wins = entry.getValue();

                // Resolve display name from the server's profile cache (covers online + offline players)
                String name = server.getProfileCache().get(uuid)
                        .map(com.mojang.authlib.GameProfile::getName)
                        .orElse(uuid.toString().substring(0, 8) + "...");

                Component row = Component.literal(
                        "  #" + pos + "  " + name + "  — " + wins + " win" + (wins == 1 ? "" : "s"))
                        .withStyle(pos == 1 ? MessageUtils.GOLD
                                : pos <= 3  ? MessageUtils.WHITE
                                           : MessageUtils.GRAY);
                src.sendSuccess(() -> row, false);
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }
}
