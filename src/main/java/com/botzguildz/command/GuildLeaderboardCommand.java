package com.botzguildz.command;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class GuildLeaderboardCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild.then(Commands.literal("top")
                .requires(src -> src.isPlayer())

                // /guild top              — defaults to money leaderboard
                .executes(ctx -> topMoney(ctx.getSource()))

                // /guild top money        — top guilds by bank balance
                .then(Commands.literal("money")
                        .executes(ctx -> topMoney(ctx.getSource())))

                // /guild top wars         — top guilds by war wins
                .then(Commands.literal("wars")
                        .executes(ctx -> topWars(ctx.getSource())))
        );
    }

    // ── /guild top [money] ────────────────────────────────────────────────────

    private static int topMoney(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            GuildSavedData data = GuildSavedData.get(player.getServer());
            List<Guild> top = data.getTopGuildsByBalance(10);

            src.sendSuccess(() -> MessageUtils.header("Top Guilds — Bank Balance"), false);

            if (top.isEmpty()) {
                src.sendSuccess(() -> MessageUtils.info("No guilds exist yet."), false);
                return 1;
            }

            for (int i = 0; i < top.size(); i++) {
                int pos = i + 1;
                Guild g = top.get(i);
                Component row = Component.literal(
                        "  #" + pos + "  " + g.getName() + " [" + g.getTag() + "]"
                        + "  — " + CurrencyManager.format(g.getAvailableBalance()))
                        .withStyle(pos == 1 ? MessageUtils.GOLD
                                : pos <= 3  ? MessageUtils.WHITE
                                           : MessageUtils.GRAY);
                src.sendSuccess(() -> row, false);
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild top wars ───────────────────────────────────────────────────────

    private static int topWars(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            GuildSavedData data = GuildSavedData.get(player.getServer());
            List<Guild> top = data.getTopGuildsByWarWins(10);

            src.sendSuccess(() -> MessageUtils.header("Top Guilds — War Wins"), false);

            if (top.isEmpty()) {
                src.sendSuccess(() -> MessageUtils.info("No guilds exist yet."), false);
                return 1;
            }

            // Filter guilds with at least one win so the board isn't padded with zeros
            List<Guild> nonZero = top.stream().filter(g -> g.getWarWins() > 0).toList();
            if (nonZero.isEmpty()) {
                src.sendSuccess(() -> MessageUtils.info("No guild wars have been won yet."), false);
                return 1;
            }

            for (int i = 0; i < nonZero.size(); i++) {
                int pos = i + 1;
                Guild g = nonZero.get(i);
                int wins = g.getWarWins();
                Component row = Component.literal(
                        "  #" + pos + "  " + g.getName() + " [" + g.getTag() + "]"
                        + "  — " + wins + " win" + (wins == 1 ? "" : "s"))
                        .withStyle(pos == 1 ? MessageUtils.GOLD
                                : pos <= 3  ? MessageUtils.WHITE
                                           : MessageUtils.GRAY);
                src.sendSuccess(() -> row, false);
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }
}
