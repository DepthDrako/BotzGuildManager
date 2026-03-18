package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildRank;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.RankPermission;
import com.botzguildz.gui.GuildBankMenu;
import com.botzguildz.util.CurrencyParser;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;

public class GuildBankCommand {

    /** Suggests rank names in the executing player's guild (for /guild bank limit). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_RANKS =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Guild g = GuildUtils.getGuildOf(player);
                    if (g == null) return builder.buildFuture();
                    return SharedSuggestionProvider.suggest(
                            g.getRanks().stream().map(GuildRank::getName),
                            builder);
                } catch (Exception e) { return builder.buildFuture(); }
            };

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild.then(Commands.literal("bank")
                .requires(src -> src.isPlayer())

                // /guild bank — open GUI
                .executes(ctx -> openGui(ctx.getSource()))

                .then(Commands.literal("balance")
                        .executes(ctx -> balance(ctx.getSource())))

                .then(Commands.literal("deposit")
                        .then(Commands.literal("all")
                                .executes(ctx -> depositAll(ctx.getSource())))
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                .executes(ctx -> depositParsed(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "amount")))))

                .then(Commands.literal("withdraw")
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                .executes(ctx -> withdrawParsed(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "amount")))))

                // /guild bank donate <amount> — personal wallet → guild bank (any member)
                .then(Commands.literal("donate")
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                .executes(ctx -> donateParsed(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "amount")))))

                // /guild bank limits — list current per-rank daily withdrawal limits
                .then(Commands.literal("limits")
                        .executes(ctx -> listLimits(ctx.getSource())))

                // /guild bank limit <rankName> <amount|unlimited|none>
                //   Sets daily withdrawal cap for a rank (leader-only).
                //   "unlimited" = no cap, "none"/"0" = no permission to withdraw.
                .then(Commands.literal("limit")
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests(SUGGEST_RANKS)
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(ctx -> setLimit(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "rank"),
                                                StringArgumentType.getString(ctx, "amount"))))))
        );
    }

    // ── /guild bank — open GUI ────────────────────────────────────────────────

    private static int openGui(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal("Guild Bank"); }
                        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new GuildBankMenu(id, inv);
                        }
                    },
                    buf -> { /* no extra data needed */ }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /guild bank balance ───────────────────────────────────────────────────

    private static int balance(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            src.sendSuccess(() -> MessageUtils.info("Guild bank: "
                    + CurrencyManager.format(guild.getBankBalance())
                    + " | Escrowed: " + CurrencyManager.format(guild.getWarEscrow())
                    + " | Available: " + CurrencyManager.format(guild.getAvailableBalance())), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild bank deposit <amount> ──────────────────────────────────────────

    private static int depositParsed(CommandSourceStack src, String amountStr) {
        try { return deposit(src, CurrencyParser.parse(amountStr)); }
        catch (IllegalArgumentException e) { src.sendFailure(MessageUtils.error(e.getMessage())); return 0; }
    }

    private static int deposit(CommandSourceStack src, long amount) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            // Check bank cap (may be doubled/tripled by upgrades)
            long maxBalance = GuildConfig.MAX_BANK_BALANCE.get();
            if (guild.hasUpgrade("BANK_CAP_II"))      maxBalance *= 3;
            else if (guild.hasUpgrade("BANK_CAP_I"))  maxBalance *= 2;

            if (guild.getBankBalance() + amount > maxBalance) {
                src.sendFailure(MessageUtils.error("That deposit would exceed the guild bank cap of "
                        + CurrencyManager.format(maxBalance) + "."));
                return 0;
            }

            if (!CurrencyManager.deduct(player, amount)) {
                src.sendFailure(MessageUtils.error("You don't have enough " + CurrencyManager.currencyName() + "."));
                return 0;
            }

            guild.deposit(amount);
            GuildSavedData.get(player.getServer()).setDirty();
            src.sendSuccess(() -> MessageUtils.success("Deposited " + CurrencyManager.format(amount)
                    + " into the guild bank. New balance: " + CurrencyManager.format(guild.getBankBalance())), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild bank deposit all ───────────────────────────────────────────────

    private static int depositAll(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            long balance = CurrencyManager.getBalance(player);
            if (balance <= 0) {
                src.sendFailure(MessageUtils.error("You have no " + CurrencyManager.currencyName() + " to deposit."));
                return 0;
            }

            // Respect the same bank cap logic as /guild bank deposit <amount>
            long maxBalance = GuildConfig.MAX_BANK_BALANCE.get();
            if (guild.hasUpgrade("BANK_CAP_II"))      maxBalance *= 3;
            else if (guild.hasUpgrade("BANK_CAP_I"))  maxBalance *= 2;

            long space = maxBalance - guild.getBankBalance();
            if (space <= 0) {
                src.sendFailure(MessageUtils.error("The guild bank is full (cap: "
                        + CurrencyManager.format(maxBalance) + ")."));
                return 0;
            }

            // Deposit as much as the bank can hold; keep the remainder in the player's inventory
            long toDeposit = Math.min(balance, space);

            if (!CurrencyManager.deduct(player, toDeposit)) {
                src.sendFailure(MessageUtils.error("Failed to deduct currency from your inventory."));
                return 0;
            }

            guild.deposit(toDeposit);
            GuildSavedData.get(player.getServer()).setDirty();

            final String memberName = player.getName().getString();
            guild.addLog(memberName + " deposited " + CurrencyManager.format(toDeposit) + " (deposit all).");

            if (toDeposit < balance) {
                // Bank hit the cap — partial deposit
                long kept = balance - toDeposit;
                src.sendSuccess(() -> MessageUtils.success(
                        "Deposited " + CurrencyManager.format(toDeposit)
                        + " into the guild bank (bank cap reached). "
                        + CurrencyManager.format(kept) + " remains in your inventory. "
                        + "New balance: " + CurrencyManager.format(guild.getBankBalance())), false);
            } else {
                // Full deposit
                src.sendSuccess(() -> MessageUtils.success(
                        "Deposited all " + CurrencyManager.format(toDeposit)
                        + " into the guild bank. "
                        + "New balance: " + CurrencyManager.format(guild.getBankBalance())), false);
            }
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild bank withdraw <amount> — guild bank → personal wallet ──────────

    private static int withdrawParsed(CommandSourceStack src, String amountStr) {
        try { return withdraw(src, CurrencyParser.parse(amountStr)); }
        catch (IllegalArgumentException e) { src.sendFailure(MessageUtils.error(e.getMessage())); return 0; }
    }

    private static int withdraw(CommandSourceStack src, long amount) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            if (!guild.canWithdrawFromBank(player.getUUID())) {
                src.sendFailure(MessageUtils.error("Your rank does not allow withdrawing from the guild bank."));
                return 0;
            }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            long remaining = guild.getRemainingDailyWithdraw(player.getUUID(), data.getDailyWithdrawn(player.getUUID()));
            if (remaining != Long.MAX_VALUE && amount > remaining) {
                src.sendFailure(MessageUtils.error("Exceeds your daily withdrawal limit. You can still withdraw "
                        + CurrencyManager.format(remaining) + " today."));
                return 0;
            }

            if (!guild.withdraw(amount)) {
                src.sendFailure(MessageUtils.error("Insufficient guild bank balance. Available: "
                        + CurrencyManager.format(guild.getAvailableBalance())));
                return 0;
            }

            data.addDailyWithdrawn(player.getUUID(), amount);
            data.addToWallet(player.getUUID(), amount);
            data.setDirty();

            final String memberName = player.getName().getString();
            guild.addLog(memberName + " withdrew " + CurrencyManager.format(amount) + " from the bank.");
            long newWallet = data.getWallet(player.getUUID());
            src.sendSuccess(() -> MessageUtils.success("Withdrew " + CurrencyManager.format(amount)
                    + " from the guild bank into your wallet. Wallet: "
                    + CurrencyManager.format(newWallet)), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild bank donate <amount> — personal wallet → guild bank ────────────

    private static int donateParsed(CommandSourceStack src, String amountStr) {
        try { return donate(src, CurrencyParser.parse(amountStr)); }
        catch (IllegalArgumentException e) { src.sendFailure(MessageUtils.error(e.getMessage())); return 0; }
    }

    private static int donate(CommandSourceStack src, long amount) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            GuildSavedData data = GuildSavedData.get(player.getServer());
            if (!data.deductFromWallet(player.getUUID(), amount)) {
                long bal = data.getWallet(player.getUUID());
                src.sendFailure(MessageUtils.error("Insufficient wallet balance. Have "
                        + CurrencyManager.format(bal) + ", need "
                        + CurrencyManager.format(amount) + "."));
                return 0;
            }

            // Respect bank cap
            long maxBalance = GuildConfig.MAX_BANK_BALANCE.get();
            if (guild.hasUpgrade("BANK_CAP_II"))      maxBalance *= 3;
            else if (guild.hasUpgrade("BANK_CAP_I"))  maxBalance *= 2;

            if (guild.getBankBalance() + amount > maxBalance) {
                // Refund the wallet and reject
                data.addToWallet(player.getUUID(), amount);
                src.sendFailure(MessageUtils.error("That donation would exceed the guild bank cap of "
                        + CurrencyManager.format(maxBalance) + "."));
                return 0;
            }

            guild.deposit(amount);
            data.setDirty();

            final String memberName = player.getName().getString();
            guild.addLog(memberName + " donated " + CurrencyManager.format(amount) + " from their personal wallet.");
            src.sendSuccess(() -> MessageUtils.success("Donated " + CurrencyManager.format(amount)
                    + " to the guild bank (" + guild.getName() + "). Guild balance: "
                    + CurrencyManager.format(guild.getAvailableBalance())), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild bank limits ────────────────────────────────────────────────────

    private static int listLimits(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            src.sendSuccess(() -> MessageUtils.header("Daily Withdrawal Limits — " + guild.getName()), false);
            for (GuildRank rank : guild.getRanks()) {
                long limit = guild.getRankDailyLimit(rank.getName());
                String limitStr = (limit == Long.MAX_VALUE) ? "Unlimited"
                        : (limit == 0L) ? "No permission"
                        : CurrencyManager.format(limit) + " / day";
                src.sendSuccess(() -> Component.literal("  " + rank.getName() + ":  " + limitStr)
                        .withStyle(limit == 0L ? MessageUtils.GRAY
                                : limit == Long.MAX_VALUE ? MessageUtils.GOLD
                                : MessageUtils.YELLOW), false);
            }
            src.sendSuccess(() -> Component.literal("Use /guild bank limit <rank> <amount|unlimited|none> to change.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }

    // ── /guild bank limit <rank> <amount|unlimited|none> ─────────────────────

    private static int setLimit(CommandSourceStack src, String rankName, String amountStr) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            // Leader-only
            if (!player.getUUID().equals(guild.getLeaderUUID())) {
                src.sendFailure(MessageUtils.error("Only the guild leader can configure withdrawal limits."));
                return 0;
            }

            GuildRank rank = guild.getRank(rankName);
            if (rank == null) { src.sendFailure(MessageUtils.error("Rank '" + rankName + "' not found.")); return 0; }
            if (rankName.equalsIgnoreCase("Leader")) {
                src.sendFailure(MessageUtils.error("The Leader rank always has unlimited withdrawal."));
                return 0;
            }

            long limit;
            String normalised = amountStr.trim().toLowerCase();
            if (normalised.equals("unlimited")) {
                limit = Long.MAX_VALUE;
            } else if (normalised.equals("none") || normalised.equals("0")) {
                limit = 0L;
            } else {
                try { limit = CurrencyParser.parse(amountStr); }
                catch (IllegalArgumentException e) {
                    src.sendFailure(MessageUtils.error("Invalid amount. Use a number, 'unlimited', or 'none'."));
                    return 0;
                }
                if (limit <= 0) { limit = 0L; } // treat negatives as "no permission"
            }

            guild.setRankDailyLimit(rankName, limit);
            GuildSavedData.get(player.getServer()).setDirty();

            final long finalLimit = limit;
            String limitStr = (finalLimit == Long.MAX_VALUE) ? "Unlimited"
                    : (finalLimit == 0L) ? "No permission"
                    : CurrencyManager.format(finalLimit) + " / day";
            src.sendSuccess(() -> MessageUtils.success(
                    "Set daily withdrawal limit for '" + rankName + "' to: " + limitStr), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }
}
