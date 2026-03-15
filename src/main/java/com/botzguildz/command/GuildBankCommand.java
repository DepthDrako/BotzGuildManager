package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.RankPermission;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class GuildBankCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild.then(Commands.literal("bank")
                .requires(src -> src.isPlayer())

                .then(Commands.literal("balance")
                        .executes(ctx -> balance(ctx.getSource())))

                .then(Commands.literal("deposit")
                        .then(Commands.literal("all")
                                .executes(ctx -> depositAll(ctx.getSource())))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> deposit(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "amount")))))

                .then(Commands.literal("withdraw")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> withdraw(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "amount")))))
        );
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

    // ── /guild bank withdraw <amount> ─────────────────────────────────────────

    private static int withdraw(CommandSourceStack src, long amount) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_BANK)) {
                src.sendFailure(MessageUtils.error("You don't have permission to withdraw from the guild bank."));
                return 0;
            }

            if (!guild.withdraw(amount)) {
                src.sendFailure(MessageUtils.error("Insufficient guild bank balance. Available: "
                        + CurrencyManager.format(guild.getAvailableBalance())));
                return 0;
            }

            CurrencyManager.give(player, amount);
            GuildSavedData.get(player.getServer()).setDirty();

            final String memberName = player.getName().getString();
            guild.addLog(memberName + " withdrew " + CurrencyManager.format(amount) + " from the bank.");
            src.sendSuccess(() -> MessageUtils.success("Withdrew " + CurrencyManager.format(amount)
                    + " from the guild bank."), false);
        } catch (Exception e) { src.sendFailure(MessageUtils.error("An error occurred.")); }
        return 1;
    }
}
