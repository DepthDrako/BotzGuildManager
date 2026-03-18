package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.AuctionHouseData;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.gui.AuctionBrowseMenu;
import com.botzguildz.gui.AuctionListMenu;
import com.botzguildz.market.AuctionListing;
import com.botzguildz.util.CurrencyParser;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.UUID;

public class AuctionHouseCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ah")
                .requires(CommandSourceStack::isPlayer)

                // /ah — browse all listings
                .executes(ctx -> openBrowse(ctx.getSource(), false))

                // /ah sell <price>
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", StringArgumentType.word())
                                .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                .executes(ctx -> openSellParsed(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "price"), false, 0))))

                // /ah auction <startBid> <minutes>
                .then(Commands.literal("auction")
                        .then(Commands.argument("startBid", StringArgumentType.word())
                                .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                .then(Commands.argument("minutes", LongArgumentType.longArg(1))
                                        .executes(ctx -> openSellParsed(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "startBid"),
                                                true,
                                                LongArgumentType.getLong(ctx, "minutes"))))))

                // /ah cancel <listingId>
                .then(Commands.literal("cancel")
                        .then(Commands.argument("listingId", StringArgumentType.word())
                                .executes(ctx -> cancelListing(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "listingId")))))

                // /ah listings — browse own listings
                .then(Commands.literal("listings")
                        .executes(ctx -> openBrowse(ctx.getSource(), true)))
        );
    }

    // ── /ah [browse] ─────────────────────────────────────────────────────────

    private static int openBrowse(CommandSourceStack src, boolean myListingsOnly) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() { return Component.literal("Auction House"); }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new AuctionBrowseMenu(id, inv,
                                    myListingsOnly ? AuctionBrowseMenu.Filter.MY_LISTINGS : AuctionBrowseMenu.Filter.ALL,
                                    1);
                        }
                    },
                    buf -> {
                        buf.writeInt(myListingsOnly
                                ? AuctionBrowseMenu.Filter.MY_LISTINGS.ordinal()
                                : AuctionBrowseMenu.Filter.ALL.ordinal());
                        buf.writeInt(1);
                    }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /ah sell <price> and /ah auction <bid> <minutes> ─────────────────────

    private static int openSellParsed(CommandSourceStack src, String priceStr,
                                      boolean timed, long durationMinutes) {
        long price;
        try {
            price = CurrencyParser.parse(priceStr);
        } catch (IllegalArgumentException e) {
            src.sendFailure(MessageUtils.error(e.getMessage()));
            return 0;
        }
        return openSell(src, price, timed, durationMinutes, 0);
    }

    private static int openSell(CommandSourceStack src, long price, boolean timed,
                                 long durationMinutes, int ignored) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                src.sendFailure(MessageUtils.error("You must be holding an item to list."));
                return 0;
            }

            // Check max listings
            int maxListings = GuildConfig.AH_MAX_LISTINGS_PER_PLAYER.get();
            List<AuctionListing> current = AuctionHouseData.get(server)
                    .getListingsBySeller(player.getUUID());
            if (current.size() >= maxListings) {
                src.sendFailure(MessageUtils.error("You already have " + maxListings
                        + " active listings (the maximum)."));
                return 0;
            }

            // Validate timed duration
            if (timed) {
                int maxMins = GuildConfig.AH_MAX_DURATION_MINUTES.get();
                if (durationMinutes < 1 || durationMinutes > maxMins) {
                    src.sendFailure(MessageUtils.error("Duration must be between 1 and "
                            + maxMins + " minutes."));
                    return 0;
                }
            }

            final long finalDuration = durationMinutes;
            final boolean isTimed = timed;
            final long finalPrice = price;

            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() { return Component.literal("Create Listing"); }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new AuctionListMenu(id, inv, finalPrice,
                                    isTimed ? AuctionListing.Type.TIMED : AuctionListing.Type.FIXED,
                                    finalDuration);
                        }
                    },
                    buf -> {
                        buf.writeLong(finalPrice);
                        buf.writeBoolean(isTimed);
                        buf.writeLong(finalDuration);
                    }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /ah cancel <listingId> ────────────────────────────────────────────────

    private static int cancelListing(CommandSourceStack src, String listingIdStr) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            UUID listingId;
            try {
                if (listingIdStr.length() == 8) {
                    listingId = AuctionHouseData.get(server)
                            .getListingsBySeller(player.getUUID()).stream()
                            .filter(l -> l.getListingId().toString().startsWith(listingIdStr))
                            .map(AuctionListing::getListingId)
                            .findFirst().orElse(null);
                    if (listingId == null) {
                        src.sendFailure(MessageUtils.error("Listing '" + listingIdStr + "' not found."));
                        return 0;
                    }
                } else {
                    listingId = UUID.fromString(listingIdStr);
                }
            } catch (IllegalArgumentException e) {
                src.sendFailure(MessageUtils.error("Invalid listing ID."));
                return 0;
            }

            boolean cancelled = AuctionHouseData.get(server)
                    .cancelListing(listingId, player.getUUID(), player.getName().getString());
            if (!cancelled) {
                src.sendFailure(MessageUtils.error("Listing not found or you don't own it."));
                return 0;
            }

            src.sendSuccess(() -> MessageUtils.success("Listing cancelled. Your item will be returned shortly."), false);
            // Deliver immediately if still online
            AuctionHouseData.get(server).deliverPending(player);

        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }
}
