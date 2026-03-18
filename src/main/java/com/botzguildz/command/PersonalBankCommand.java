package com.botzguildz.command;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.gui.BankTestMenu;
import com.botzguildz.gui.PersonalBankMenu;
import com.botzguildz.util.ChestStockHelper;
import com.botzguildz.util.CurrencyParser;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;

public class PersonalBankCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bank")
                .requires(CommandSourceStack::isPlayer)

                // /bank — open GUI
                .executes(ctx -> openGui(ctx.getSource()))

                // /bank test — open the custom fantasy GUI prototype
                .then(Commands.literal("test")
                        .executes(ctx -> openTestGui(ctx.getSource())))

                // /bank balance
                .then(Commands.literal("balance")
                        .executes(ctx -> balance(ctx.getSource())))

                // /bank deposit <amount> | all
                .then(Commands.literal("deposit")
                        .then(Commands.literal("all")
                                .executes(ctx -> depositAll(ctx.getSource())))
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                .executes(ctx -> depositParsed(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "amount")))))

                // /bank withdraw <amount> | all
                .then(Commands.literal("withdraw")
                        .then(Commands.literal("all")
                                .executes(ctx -> withdrawAll(ctx.getSource())))
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                .executes(ctx -> withdrawParsed(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "amount")))))

                // /bank donate <amount>  — wallet → guild bank
                .then(Commands.literal("donate")
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                .executes(ctx -> donateParsed(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "amount")))))

                // /bank pay <player> <amount>  — wallet → another player's wallet
                .then(Commands.literal("pay")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                        .executes(ctx -> payParsed(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"),
                                                StringArgumentType.getString(ctx, "amount"))))))

                // /bank vault — register / clear a physical storage block as the bank vault
                .then(Commands.literal("vault")
                        .then(Commands.literal("set")
                                .executes(ctx -> setVault(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearVault(ctx.getSource()))))
        );
    }

    // ── /bank test — open custom fantasy GUI ──────────────────────────────────

    private static int openTestGui(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal("Bank Test Custom GUI"); }
                        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new BankTestMenu(id, inv);
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

    // ── /bank — open GUI ──────────────────────────────────────────────────────

    static int openGui(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal("Personal Bank"); }
                        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new PersonalBankMenu(id, inv);
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

    // ── /bank balance ─────────────────────────────────────────────────────────

    static int balance(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            long wallet = GuildSavedData.get(server).getWallet(player.getUUID());
            long onHand = CurrencyManager.getBalance(player);

            src.sendSuccess(() -> MessageUtils.header("Personal Bank"), false);
            src.sendSuccess(() -> Component.literal("  Wallet balance: ")
                    .withStyle(MessageUtils.GOLD)
                    .append(Component.literal(CurrencyManager.format(wallet))
                            .withStyle(MessageUtils.YELLOW)), false);
            src.sendSuccess(() -> Component.literal("  In hand (" + CurrencyManager.currencyName() + "): ")
                    .withStyle(MessageUtils.GOLD)
                    .append(Component.literal(CurrencyManager.format(onHand))
                            .withStyle(MessageUtils.WHITE)), false);

            // Guild bank info if applicable
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild != null) {
                src.sendSuccess(() -> Component.literal("  Guild bank (" + guild.getName() + "): ")
                        .withStyle(MessageUtils.GOLD)
                        .append(Component.literal(CurrencyManager.format(guild.getAvailableBalance()))
                                .withStyle(MessageUtils.GREEN)), false);
            }
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bank deposit <amount> — physical items → wallet ─────────────────────

    static int depositParsed(CommandSourceStack src, String amountStr) {
        try { return deposit(src, CurrencyParser.parse(amountStr)); }
        catch (IllegalArgumentException e) { src.sendFailure(MessageUtils.error(e.getMessage())); return 0; }
    }

    static int deposit(CommandSourceStack src, long amount) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            if (!CurrencyManager.deduct(player, amount)) {
                src.sendFailure(MessageUtils.error("You don't have enough "
                        + CurrencyManager.currencyName() + " on hand. (Need "
                        + CurrencyManager.format(amount) + ")"));
                return 0;
            }

            GuildSavedData.get(server).addToWallet(player.getUUID(), amount);
            long newBal = GuildSavedData.get(server).getWallet(player.getUUID());

            src.sendSuccess(() -> MessageUtils.success("Deposited "
                    + CurrencyManager.format(amount) + " into your bank. Balance: "
                    + CurrencyManager.format(newBal)), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bank deposit all ─────────────────────────────────────────────────────

    static int depositAll(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            long onHand = CurrencyManager.getBalance(player);
            if (onHand <= 0) {
                src.sendFailure(MessageUtils.error("You have no "
                        + CurrencyManager.currencyName() + " on hand to deposit."));
                return 0;
            }

            if (!CurrencyManager.deduct(player, onHand)) {
                src.sendFailure(MessageUtils.error("Failed to collect currency from your inventory."));
                return 0;
            }

            GuildSavedData.get(server).addToWallet(player.getUUID(), onHand);
            long newBal = GuildSavedData.get(server).getWallet(player.getUUID());

            final long deposited = onHand;
            src.sendSuccess(() -> MessageUtils.success("Deposited all "
                    + CurrencyManager.format(deposited) + " into your bank. Balance: "
                    + CurrencyManager.format(newBal)), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bank withdraw <amount> — wallet → physical items ────────────────────

    static int withdrawParsed(CommandSourceStack src, String amountStr) {
        try { return withdraw(src, CurrencyParser.parse(amountStr)); }
        catch (IllegalArgumentException e) { src.sendFailure(MessageUtils.error(e.getMessage())); return 0; }
    }

    static int withdraw(CommandSourceStack src, long amount) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            if (!GuildSavedData.get(server).deductFromWallet(player.getUUID(), amount)) {
                long bal = GuildSavedData.get(server).getWallet(player.getUUID());
                src.sendFailure(MessageUtils.error("Insufficient wallet balance. Have "
                        + CurrencyManager.format(bal) + ", need "
                        + CurrencyManager.format(amount) + "."));
                return 0;
            }

            CurrencyManager.give(player, amount);
            long newBal = GuildSavedData.get(server).getWallet(player.getUUID());

            src.sendSuccess(() -> MessageUtils.success("Withdrew "
                    + CurrencyManager.format(amount) + " from your bank. Remaining: "
                    + CurrencyManager.format(newBal)), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bank withdraw all ────────────────────────────────────────────────────

    static int withdrawAll(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            long bal = GuildSavedData.get(server).getWallet(player.getUUID());
            if (bal <= 0) {
                src.sendFailure(MessageUtils.error("Your wallet is empty."));
                return 0;
            }

            GuildSavedData.get(server).deductFromWallet(player.getUUID(), bal);
            CurrencyManager.give(player, bal);

            final long withdrawn = bal;
            src.sendSuccess(() -> MessageUtils.success("Withdrew all "
                    + CurrencyManager.format(withdrawn) + " from your bank."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bank donate <amount> — personal wallet → guild bank ─────────────────

    static int donateParsed(CommandSourceStack src, String amountStr) {
        try { return donate(src, CurrencyParser.parse(amountStr)); }
        catch (IllegalArgumentException e) { src.sendFailure(MessageUtils.error(e.getMessage())); return 0; }
    }

    static int donate(CommandSourceStack src, long amount) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild."));
                return 0;
            }

            GuildSavedData data = GuildSavedData.get(server);
            if (!data.deductFromWallet(player.getUUID(), amount)) {
                long bal = data.getWallet(player.getUUID());
                src.sendFailure(MessageUtils.error("Insufficient wallet balance. Have "
                        + CurrencyManager.format(bal) + ", need "
                        + CurrencyManager.format(amount) + "."));
                return 0;
            }

            guild.deposit(amount);
            data.setDirty();

            guild.addLog(player.getName().getString() + " donated "
                    + CurrencyManager.format(amount) + " from their personal bank.");

            src.sendSuccess(() -> MessageUtils.success("Donated "
                    + CurrencyManager.format(amount) + " to the guild bank ("
                    + guild.getName() + "). Guild balance: "
                    + CurrencyManager.format(guild.getAvailableBalance())), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bank pay <player> <amount> — wallet to wallet ────────────────────────

    static int payParsed(CommandSourceStack src, ServerPlayer target, String amountStr) {
        try { return pay(src, target, CurrencyParser.parse(amountStr)); }
        catch (IllegalArgumentException e) { src.sendFailure(MessageUtils.error(e.getMessage())); return 0; }
    }

    static int pay(CommandSourceStack src, ServerPlayer target, long amount) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            if (player.getUUID().equals(target.getUUID())) {
                src.sendFailure(MessageUtils.error("You cannot pay yourself."));
                return 0;
            }

            GuildSavedData data = GuildSavedData.get(server);
            if (!data.deductFromWallet(player.getUUID(), amount)) {
                long bal = data.getWallet(player.getUUID());
                src.sendFailure(MessageUtils.error("Insufficient wallet balance. Have "
                        + CurrencyManager.format(bal) + ", need "
                        + CurrencyManager.format(amount) + "."));
                return 0;
            }

            data.addToWallet(target.getUUID(), amount);

            src.sendSuccess(() -> MessageUtils.success("Paid "
                    + CurrencyManager.format(amount) + " to "
                    + target.getName().getString() + "."), false);

            target.sendSystemMessage(MessageUtils.success(player.getName().getString()
                    + " sent you " + CurrencyManager.format(amount) + "!"));
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bank vault set — register the block the player is looking at ─────────

    static int setVault(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.serverLevel();

            // Ray-cast to the block the player is looking at (up to 5 blocks)
            HitResult hit = player.pick(5.0, 1.0f, false);
            if (!(hit instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) {
                src.sendFailure(MessageUtils.error("You must be looking at a storage block (within 5 blocks)."));
                return 0;
            }

            BlockPos pos = bhr.getBlockPos();

            // Validate: block must not be blacklisted and must expose IItemHandler
            if (!ChestStockHelper.isValidStorage(level, pos)) {
                src.sendFailure(MessageUtils.error(
                        "That block cannot be used as a bank vault. "
                        + "It is either blacklisted or does not support item storage."));
                return 0;
            }

            // Validate: block is not already claimed by someone else
            String dimId = level.dimension().location().toString();
            GuildSavedData data = GuildSavedData.get(player.getServer());
            java.util.Optional<java.util.UUID> existing = data.getVaultOwnerAt(pos, dimId);
            if (existing.isPresent() && !existing.get().equals(player.getUUID())) {
                src.sendFailure(MessageUtils.error("That block is already registered as another player's bank vault."));
                return 0;
            }

            data.setBankVault(player.getUUID(), pos, dimId);
            src.sendSuccess(() -> MessageUtils.success(
                    "Bank vault registered at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                    + ". Deposit/withdraw will now use this block."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bank vault clear — unregister the bank vault ─────────────────────────

    static int clearVault(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            GuildSavedData data = GuildSavedData.get(player.getServer());
            GuildSavedData.BankVault vault = data.getBankVault(player.getUUID());
            if (vault == null) {
                src.sendFailure(MessageUtils.error("You do not have a bank vault registered."));
                return 0;
            }
            data.clearBankVault(player.getUUID());
            src.sendSuccess(() -> MessageUtils.success(
                    "Bank vault unregistered. Deposit/withdraw will use your inventory again."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }
}
