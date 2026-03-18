package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.MarketData;
import com.botzguildz.gui.ShopCreateMenu;
import com.botzguildz.gui.ShopListMenu;
import com.botzguildz.gui.ShopViewMenu;
import com.botzguildz.market.MarketListing;
import com.botzguildz.market.PlayerShop;
import com.botzguildz.util.CurrencyParser;
import com.botzguildz.util.MessageUtils;
import com.botzguildz.util.ShopSignHelper;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.UUID;

public class ShopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
                .requires(CommandSourceStack::isPlayer)

                // /shop — open own shop (owner view)
                .executes(ctx -> openOwnShop(ctx.getSource()))

                // /shop create — look at a chest/block, open the listing GUI
                .then(Commands.literal("create")
                        .executes(ctx -> createListing(ctx.getSource())))

                // /shop browse [playerName]
                .then(Commands.literal("browse")
                        .executes(ctx -> browseAll(ctx.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> browsePlayer(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))

                // /shop add <price>
                .then(Commands.literal("add")
                        .then(Commands.argument("price", StringArgumentType.word())
                                .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                .executes(ctx -> addItem(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "price")))))

                // /shop remove <listingId>
                .then(Commands.literal("remove")
                        .then(Commands.argument("listingId", StringArgumentType.word())
                                .executes(ctx -> removeItem(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "listingId")))))

                // /shop setblock — registers the block you're looking at
                .then(Commands.literal("setblock")
                        .executes(ctx -> setBlock(ctx.getSource())))

                // /shop open
                .then(Commands.literal("open")
                        .executes(ctx -> setOpen(ctx.getSource(), true)))

                // /shop close
                .then(Commands.literal("close")
                        .executes(ctx -> setOpen(ctx.getSource(), false)))
        );
    }

    // ── /shop (own shop, owner mode) ──────────────────────────────────────────

    private static int openOwnShop(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() { return Component.literal("My Shops"); }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new ShopViewMenu(id, inv, null, player.getUUID(), true, 1);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(false);        // isGuild
                        buf.writeUUID(player.getUUID());
                        buf.writeBoolean(true);         // isOwner
                        buf.writeInt(1);
                        buf.writeBoolean(false);        // no specific shopId — show all
                    }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /shop create — look at a block, open shop-listing GUI ────────────────

    private static int createListing(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            // Ray-cast to find the block the player is looking at (up to 5 blocks)
            net.minecraft.world.phys.HitResult hit = player.pick(5.0, 1.0f, false);
            BlockPos shopPos = null;
            if (hit instanceof BlockHitResult blockHit) {
                BlockPos target = blockHit.getBlockPos();
                net.minecraft.world.level.block.state.BlockState state =
                        player.level().getBlockState(target);

                // If looking at a wall sign, try the block it's attached to
                if (state.getBlock() instanceof net.minecraft.world.level.block.WallSignBlock) {
                    net.minecraft.core.Direction facing = state.getValue(
                            net.minecraft.world.level.block.WallSignBlock.FACING);
                    shopPos = target.relative(facing.getOpposite());
                } else {
                    shopPos = target;
                }
            }

            if (shopPos == null) {
                src.sendFailure(MessageUtils.error("Look at a chest or block to register as your shop, then run /shop create."));
                return 0;
            }

            final BlockPos finalPos = shopPos;
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() { return Component.literal("Create Listing"); }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new ShopCreateMenu(id, inv, finalPos);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(true);
                        buf.writeBlockPos(finalPos);
                    }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /shop browse ──────────────────────────────────────────────────────────

    private static int browseAll(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() { return Component.literal("Player Shops"); }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new ShopListMenu(id, inv, false, 1);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(false); // isGuild
                        buf.writeInt(1);
                    }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /shop browse <player> ─────────────────────────────────────────────────

    private static int browsePlayer(CommandSourceStack src, ServerPlayer target) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            boolean isOwner = player.getUUID().equals(target.getUUID());
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.literal(target.getName().getString() + "'s Shop");
                        }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new ShopViewMenu(id, inv, null, target.getUUID(), isOwner, 1);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(false);         // isGuild
                        buf.writeUUID(target.getUUID());
                        buf.writeBoolean(isOwner);
                        buf.writeInt(1);
                        buf.writeBoolean(false);         // no specific shopId — show all
                    }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /shop add <price> ─────────────────────────────────────────────────────

    static int addItem(CommandSourceStack src, String priceStr) {
        long price;
        try {
            price = CurrencyParser.parse(priceStr);
        } catch (IllegalArgumentException e) {
            src.sendFailure(MessageUtils.error(e.getMessage()));
            return 0;
        }
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                src.sendFailure(MessageUtils.error("You must hold an item to list."));
                return 0;
            }

            // Resolve which shop to add to by ray-casting to the shop block
            HitResult hit = player.pick(5.0, 1.0f, false);
            if (!(hit instanceof BlockHitResult blockHit)) {
                src.sendFailure(MessageUtils.error("Look at your shop chest to add a listing."));
                return 0;
            }
            BlockPos lookPos = blockHit.getBlockPos();
            String dimId = player.level().dimension().location().toString();

            MarketData market = MarketData.get(server);
            // Find existing shop at that block; if none, create a new one
            PlayerShop shop = market.getOrCreateShopAt(player.getUUID(), player.getName().getString(), lookPos, dimId);

            int maxListings = GuildConfig.SHOP_MAX_LISTINGS_PER_PLAYER.get();
            if (shop.getListings().size() >= maxListings) {
                src.sendFailure(MessageUtils.error("This shop has reached the maximum of " + maxListings + " listings."));
                return 0;
            }

            // Item stays in player's hand — it's a price template; stock comes from the chest
            ItemStack toList = held.copy();

            shop.addListing(new MarketListing(toList, price, held.getCount()));
            shop.setOpen(true);
            market.setDirty();

            // Update adjacent signs
            if (player.level() instanceof ServerLevel sl) {
                ShopSignHelper.updateAdjacentSigns(sl, lookPos, shop);
            }

            src.sendSuccess(() -> MessageUtils.success("Listed " + toList.getHoverName().getString()
                    + " for " + CurrencyManager.format(price) + " each. Stock the chest to make sales."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /shop remove <listingId> ──────────────────────────────────────────────

    static int removeItem(CommandSourceStack src, String listingIdStr) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            MarketData market = MarketData.get(server);
            java.util.List<PlayerShop> ownedShops = market.getPlayerShops(player.getUUID());
            if (ownedShops.isEmpty()) {
                src.sendFailure(MessageUtils.error("You have no shops."));
                return 0;
            }

            // Search all of the player's shops for the listing
            UUID listingId;
            try {
                if (listingIdStr.length() == 8) {
                    final String prefix = listingIdStr;
                    listingId = ownedShops.stream()
                            .flatMap(s -> s.getListings().stream())
                            .filter(l -> l.getListingId().toString().startsWith(prefix))
                            .map(MarketListing::getListingId)
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

            // Find which shop holds the listing
            PlayerShop shop = null;
            MarketListing removed = null;
            for (PlayerShop s : ownedShops) {
                java.util.Optional<MarketListing> opt = s.getListing(listingId);
                if (opt.isPresent()) { shop = s; removed = opt.get(); break; }
            }
            if (shop == null || removed == null) {
                src.sendFailure(MessageUtils.error("Listing not found."));
                return 0;
            }

            shop.removeListing(listingId);
            market.setDirty();

            // Update adjacent signs
            if (shop.getShopBlockPos() != null && player.level() instanceof ServerLevel sl) {
                ShopSignHelper.updateAdjacentSigns(sl, shop.getShopBlockPos(), shop);
            }

            final MarketListing finalRemoved = removed;
            src.sendSuccess(() -> MessageUtils.success("Removed " + finalRemoved.getItem().getHoverName().getString()
                    + " listing from your shop."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /shop setblock ────────────────────────────────────────────────────────

    private static int setBlock(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            // Ray-cast to find the block the player is looking at
            HitResult hit = player.pick(5.0, 1.0f, false);
            if (!(hit instanceof BlockHitResult blockHit)) {
                src.sendFailure(MessageUtils.error("You must be looking at a block."));
                return 0;
            }

            BlockPos pos = blockHit.getBlockPos();
            String dimId = player.level().dimension().location().toString();

            MarketData market = MarketData.get(server);
            // Find or create a shop at this exact position
            PlayerShop shop = market.getOrCreateShopAt(player.getUUID(), player.getName().getString(), pos, dimId);
            market.setDirty();

            src.sendSuccess(() -> MessageUtils.success("Shop block registered at "
                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                    + ". Other players can right-click it to browse your shop."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /shop open|close ──────────────────────────────────────────────────────

    private static int setOpen(CommandSourceStack src, boolean open) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            MarketData market = MarketData.get(server);
            java.util.List<PlayerShop> ownedShops = market.getPlayerShops(player.getUUID());
            if (ownedShops.isEmpty()) {
                src.sendFailure(MessageUtils.error("You have no shops."));
                return 0;
            }
            // Toggle all of the player's shops
            for (PlayerShop shop : ownedShops) {
                shop.setOpen(open);
                if (shop.getShopBlockPos() != null && player.level() instanceof ServerLevel sl) {
                    ShopSignHelper.updateAdjacentSigns(sl, shop.getShopBlockPos(), shop);
                }
            }
            market.setDirty();

            int count = ownedShops.size();
            src.sendSuccess(() -> MessageUtils.success("All " + count + " of your shop(s) are now "
                    + (open ? "open" : "closed") + "."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }
}
