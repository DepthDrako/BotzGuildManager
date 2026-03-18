package com.botzguildz.command;

import com.botzguildz.config.GuildConfig;
import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.MarketData;
import com.botzguildz.data.RankPermission;
import com.botzguildz.gui.ShopListMenu;
import com.botzguildz.gui.ShopViewMenu;
import com.botzguildz.market.GuildShop;
import com.botzguildz.market.MarketListing;
import com.botzguildz.util.CurrencyParser;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;
import java.util.UUID;

public class GuildShopCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild.then(Commands.literal("shop")
                .requires(CommandSourceStack::isPlayer)

                // /guild shop — open own guild's shop
                .executes(ctx -> openGuildShop(ctx.getSource(), null))

                // /guild shop browse [guildName]
                .then(Commands.literal("browse")
                        .executes(ctx -> browseAll(ctx.getSource()))
                        .then(Commands.argument("guildName", StringArgumentType.word())
                                .executes(ctx -> browseGuild(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "guildName")))))

                // /guild shop add <price>   (officer+)
                .then(Commands.literal("add")
                        .then(Commands.argument("price", StringArgumentType.word())
                                .suggests((ctx, builder) -> CurrencyParser.suggest(builder))
                                .executes(ctx -> addItem(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "price")))))

                // /guild shop remove <listingId>   (officer+)
                .then(Commands.literal("remove")
                        .then(Commands.argument("listingId", StringArgumentType.word())
                                .executes(ctx -> removeItem(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "listingId")))))

                // /guild shop setblock   (officer+)
                .then(Commands.literal("setblock")
                        .executes(ctx -> setBlock(ctx.getSource())))

                // /guild shop open|close   (officer+)
                .then(Commands.literal("open")
                        .executes(ctx -> setOpen(ctx.getSource(), true)))
                .then(Commands.literal("close")
                        .executes(ctx -> setOpen(ctx.getSource(), false)))
        );
    }

    // ── /guild shop ───────────────────────────────────────────────────────────

    private static int openGuildShop(CommandSourceStack src, String unusedFilter) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild."));
                return 0;
            }

            boolean isOfficer = guild.hasPermission(player.getUUID(), RankPermission.MANAGE_UPGRADES);
            UUID guildId = guild.getGuildId();

            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.literal(guild.getName() + "'s Shop");
                        }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new ShopViewMenu(id, inv, guildId, null, isOfficer, 1);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(true); // isGuild
                        buf.writeUUID(guildId);
                        buf.writeBoolean(isOfficer);
                        buf.writeInt(1);
                    }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /guild shop browse ────────────────────────────────────────────────────

    private static int browseAll(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() { return Component.literal("Guild Shops"); }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new ShopListMenu(id, inv, true, 1);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(true); // isGuild
                        buf.writeInt(1);
                    }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /guild shop browse <guildName> ────────────────────────────────────────

    private static int browseGuild(CommandSourceStack src, String guildName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            Guild targetGuild = GuildSavedData.get(server).getGuildByName(guildName);
            if (targetGuild == null) {
                src.sendFailure(MessageUtils.error("Guild '" + guildName + "' not found."));
                return 0;
            }

            Guild myGuild = GuildUtils.getGuildOf(player);
            boolean isOfficer = myGuild != null
                    && myGuild.getGuildId().equals(targetGuild.getGuildId())
                    && targetGuild.hasPermission(player.getUUID(), RankPermission.MANAGE_UPGRADES);
            UUID guildId = targetGuild.getGuildId();

            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.literal(targetGuild.getName() + "'s Shop");
                        }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new ShopViewMenu(id, inv, guildId, null, isOfficer, 1);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(true); // isGuild
                        buf.writeUUID(guildId);
                        buf.writeBoolean(isOfficer);
                        buf.writeInt(1);
                    }
            );
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /guild shop add <price> ───────────────────────────────────────────────

    private static int addItem(CommandSourceStack src, String priceStr) {
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

            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild."));
                return 0;
            }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_UPGRADES)) {
                src.sendFailure(MessageUtils.error("You need officer+ rank to manage the guild shop."));
                return 0;
            }

            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                src.sendFailure(MessageUtils.error("You must hold an item to list."));
                return 0;
            }

            MarketData market = MarketData.get(server);
            GuildShop shop = market.getOrCreateGuildShop(guild.getGuildId(), guild.getName());

            int maxListings = GuildConfig.SHOP_MAX_LISTINGS_PER_GUILD.get();
            if (shop.getListings().size() >= maxListings) {
                src.sendFailure(MessageUtils.error("Guild shop has reached the maximum of " + maxListings + " listings."));
                return 0;
            }

            ItemStack toList = held.copy();
            player.getMainHandItem().shrink(held.getCount());
            // Guild shop listings are unlimited stock (officers restock manually)
            shop.addListing(new MarketListing(toList, price, -1));
            market.setDirty();

            src.sendSuccess(() -> MessageUtils.success("Added " + toList.getHoverName().getString()
                    + " to the guild shop for " + CurrencyManager.format(price) + " each."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /guild shop remove <listingId> ────────────────────────────────────────

    private static int removeItem(CommandSourceStack src, String listingIdStr) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild."));
                return 0;
            }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_UPGRADES)) {
                src.sendFailure(MessageUtils.error("You need officer+ rank to manage the guild shop."));
                return 0;
            }

            MarketData market = MarketData.get(server);
            GuildShop shop = market.getOrCreateGuildShop(guild.getGuildId(), guild.getName());

            UUID listingId;
            try {
                if (listingIdStr.length() == 8) {
                    listingId = shop.getListings().stream()
                            .filter(l -> l.getListingId().toString().startsWith(listingIdStr))
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

            Optional<MarketListing> listingOpt = shop.getListing(listingId);
            if (listingOpt.isEmpty()) {
                src.sendFailure(MessageUtils.error("Listing not found."));
                return 0;
            }
            MarketListing removed = listingOpt.get();
            shop.removeListing(listingId);
            market.setDirty();

            src.sendSuccess(() -> MessageUtils.success("Removed "
                    + removed.getItem().getHoverName().getString() + " from the guild shop."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /guild shop setblock ──────────────────────────────────────────────────

    private static int setBlock(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild."));
                return 0;
            }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_UPGRADES)) {
                src.sendFailure(MessageUtils.error("You need officer+ rank to set the guild shop block."));
                return 0;
            }

            HitResult hit = player.pick(5.0, 1.0f, false);
            if (!(hit instanceof BlockHitResult blockHit)) {
                src.sendFailure(MessageUtils.error("You must be looking at a block."));
                return 0;
            }

            var pos = blockHit.getBlockPos();
            String dimId = player.level().dimension().location().toString();

            MarketData market = MarketData.get(server);
            GuildShop shop = market.getOrCreateGuildShop(guild.getGuildId(), guild.getName());
            shop.setShopBlock(pos, dimId);
            market.setDirty();

            src.sendSuccess(() -> MessageUtils.success("Guild shop block registered at "
                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                    + ". Others can right-click it to browse the guild shop."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // ── /guild shop open|close ────────────────────────────────────────────────

    private static int setOpen(CommandSourceStack src, boolean open) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server == null) return 0;

            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild."));
                return 0;
            }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_UPGRADES)) {
                src.sendFailure(MessageUtils.error("You need officer+ rank to manage the guild shop."));
                return 0;
            }

            MarketData market = MarketData.get(server);
            GuildShop shop = market.getOrCreateGuildShop(guild.getGuildId(), guild.getName());
            shop.setOpen(open);
            market.setDirty();

            src.sendSuccess(() -> MessageUtils.success("Guild shop is now " + (open ? "open" : "closed") + "."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
            return 0;
        }
        return 1;
    }
}
