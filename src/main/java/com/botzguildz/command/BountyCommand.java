package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.BountyData;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.gui.BountyMenu;
import com.botzguildz.market.BountyEntry;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.CurrencyParser;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.UUID;

public class BountyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bounty")
                .requires(CommandSourceStack::isPlayer)

                // /bounty — opens the bounty browse GUI
                .executes(ctx -> openGui(ctx.getSource(), null))

                // /bounty place <player> <amount>
                .then(Commands.literal("place")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                        .executes(ctx -> placeBountyParsed(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"),
                                                StringArgumentType.getString(ctx, "amount"))))))

                // /bounty list [player]
                .then(Commands.literal("list")
                        .executes(ctx -> openGui(ctx.getSource(), null))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> listBounties(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target")))))

                // /bounty cancel <bountyId>
                .then(Commands.literal("cancel")
                        .then(Commands.argument("bountyId", StringArgumentType.word())
                                .executes(ctx -> cancelBounty(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "bountyId")))))
        );
    }

    // ── /bounty (opens GUI) ───────────────────────────────────────────────────

    private static int openGui(CommandSourceStack src, ServerPlayer filterTarget) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            UUID filterUUID = filterTarget != null ? filterTarget.getUUID() : null;
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() { return Component.literal("Bounties"); }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new BountyMenu(id, inv, filterUUID, 1);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(filterUUID != null);
                        if (filterUUID != null) buf.writeUUID(filterUUID);
                        buf.writeInt(1);
                    }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bounty place <player> <amount> ──────────────────────────────────────

    private static int placeBountyParsed(CommandSourceStack src, ServerPlayer target, String amountStr) {
        long amount;
        try {
            amount = CurrencyParser.parse(amountStr);
        } catch (IllegalArgumentException e) {
            src.sendFailure(MessageUtils.error(e.getMessage()));
            return 0;
        }
        return placeBounty(src, target, amount);
    }

    private static int placeBounty(CommandSourceStack src, ServerPlayer target, long amount) {
        try {
            ServerPlayer placer = src.getPlayerOrException();
            MinecraftServer server = placer.getServer();
            if (server == null) return 0;

            long min = GuildConfig.BOUNTY_MIN.get();
            long max = GuildConfig.BOUNTY_MAX.get();
            if (amount < min || amount > max) {
                src.sendFailure(MessageUtils.error("Bounty amount must be between "
                        + CurrencyManager.format(min) + " and " + CurrencyManager.format(max) + "."));
                return 0;
            }

            GuildSavedData guildData = GuildSavedData.get(server);
            long wallet = guildData.getWallet(placer.getUUID());
            if (wallet < amount) {
                src.sendFailure(MessageUtils.error("Insufficient funds. You have "
                        + CurrencyManager.format(wallet) + " but need " + CurrencyManager.format(amount) + "."));
                return 0;
            }

            // Deduct from wallet and place bounty
            guildData.deductFromWallet(placer.getUUID(), amount);
            BountyData.get(server).placeBounty(placer.getUUID(), placer.getName().getString(),
                    target.getUUID(), amount);

            src.sendSuccess(() -> MessageUtils.success("Bounty of " + CurrencyManager.format(amount)
                    + " placed on " + target.getName().getString() + "!"), false);

            // Notify target if online
            ServerPlayer targetOnline = server.getPlayerList().getPlayer(target.getUUID());
            if (targetOnline != null) {
                targetOnline.sendSystemMessage(MessageUtils.warn(
                        "A bounty of " + CurrencyManager.format(amount) + " has been placed on you!"));
            }

        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bounty list <target> — text summary ──────────────────────────────────

    private static int listBounties(CommandSourceStack src, ServerPlayer target) {
        try {
            MinecraftServer server = src.getServer();
            List<BountyEntry> bounties = BountyData.get(server).getBountiesOn(target.getUUID());
            long total = BountyData.get(server).getTotalBounty(target.getUUID());

            src.sendSuccess(() -> MessageUtils.header("Bounties on " + target.getName().getString()), false);
            if (bounties.isEmpty()) {
                src.sendSuccess(() -> MessageUtils.info("  No active bounties."), false);
            } else {
                src.sendSuccess(() -> Component.literal("  Total: ")
                        .withStyle(MessageUtils.GOLD)
                        .append(Component.literal(CurrencyManager.format(total))
                                .withStyle(MessageUtils.YELLOW)), false);
                for (BountyEntry b : bounties) {
                    final BountyEntry entry = b;
                    src.sendSuccess(() -> Component.literal("  [" + entry.getBountyId().toString().substring(0, 8) + "] ")
                            .withStyle(MessageUtils.GRAY)
                            .append(Component.literal(entry.getPlacerName() + ": ")
                                    .withStyle(MessageUtils.WHITE))
                            .append(Component.literal(CurrencyManager.format(entry.getAmount()))
                                    .withStyle(MessageUtils.YELLOW)), false);
                }
            }
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /bounty cancel <bountyId> ─────────────────────────────────────────────

    private static int cancelBounty(CommandSourceStack src, String bountyIdStr) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            UUID bountyId;
            try {
                // Support short 8-char prefix or full UUID
                if (bountyIdStr.length() == 8) {
                    // Find matching bounty by prefix
                    List<BountyEntry> mine = BountyData.get(server).getBountiesByPlacer(player.getUUID());
                    BountyEntry match = mine.stream()
                            .filter(b -> b.getBountyId().toString().startsWith(bountyIdStr))
                            .findFirst().orElse(null);
                    if (match == null) {
                        src.sendFailure(MessageUtils.error("Bounty '" + bountyIdStr + "' not found or not yours."));
                        return 0;
                    }
                    bountyId = match.getBountyId();
                } else {
                    bountyId = UUID.fromString(bountyIdStr);
                }
            } catch (IllegalArgumentException e) {
                src.sendFailure(MessageUtils.error("Invalid bounty ID: " + bountyIdStr));
                return 0;
            }

            long refunded = BountyData.get(server).cancelBounty(bountyId, player.getUUID(), server);
            if (refunded < 0) {
                src.sendFailure(MessageUtils.error("Bounty not found or you don't own it."));
                return 0;
            }

            final long r = refunded;
            src.sendSuccess(() -> MessageUtils.success("Bounty cancelled. Refunded "
                    + CurrencyManager.format(r) + " to your wallet."), false);

        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }
}
