package com.botzguildz.event;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.AuctionHouseData;
import com.botzguildz.data.BountyData;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.GuildSavedData.BankVault;
import com.botzguildz.data.MarketData;
import com.botzguildz.gui.ShopViewMenu;
import com.botzguildz.market.GuildShop;
import com.botzguildz.market.PlayerShop;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.UUID;

public class EconomyEventHandler {

    // ── Server Tick — expire timed auctions every second ─────────────────────

    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter % 20 != 0) return; // once per second

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        AuctionHouseData.get(server).tick(server);
    }

    // ── Player Login — deliver pending AH items/coins ─────────────────────────

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        AuctionHouseData.get(server).deliverPending(player);
    }

    // ── LivingDeath — bounty payout ───────────────────────────────────────────

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer killer)) return;
        if (killer.equals(victim)) return; // no self-kill bounty

        MinecraftServer server = killer.getServer();
        if (server == null) return;

        long collected = BountyData.get(server).collectBounty(killer.getUUID(), victim.getUUID(), server);
        if (collected > 0) {
            killer.sendSystemMessage(Component.literal(
                    "[Bounty] You collected " + CurrencyManager.format(collected)
                            + " in bounties for killing " + victim.getName().getString() + "!")
                    .withStyle(ChatFormatting.GOLD));
            // Server-wide broadcast
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[Bounty] ")
                            .withStyle(ChatFormatting.RED)
                            .append(Component.literal(killer.getName().getString())
                                    .withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" collected " + CurrencyManager.format(collected)
                                    + " by killing " + victim.getName().getString() + "!")
                                    .withStyle(ChatFormatting.WHITE)),
                    false);
        }
    }

    // ── Right-click block — open bank vault or physical shop ─────────────────

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        BlockPos pos = event.getPos();
        Level level = event.getLevel();
        String dimId = level.dimension().location().toString();

        // ── Bank vault — only the owner can open it via right-click ──────────
        GuildSavedData guildData = GuildSavedData.get(server);
        BankVault vault = guildData.getBankVault(player.getUUID());
        if (vault != null && vault.pos().equals(pos) && vault.dimensionId().equals(dimId)) {
            event.setCanceled(true);
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal("Personal Bank"); }
                        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new com.botzguildz.gui.PersonalBankMenu(id, inv);
                        }
                    },
                    buf -> { /* no extra data */ }
            );
            return;
        }

        MarketData market = MarketData.get(server);

        // Check player shops — opens only THIS specific shop (chest-scoped)
        PlayerShop playerShop = market.getPlayerShopAtBlock(pos, dimId).orElse(null);
        if (playerShop != null && playerShop.isOpen()) {
            event.setCanceled(true);
            final PlayerShop ps = playerShop;
            final boolean isOwner = player.getUUID().equals(ps.getOwnerUUID());
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.literal(ps.getOwnerName() + "'s Shop");
                        }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new ShopViewMenu(id, inv, null, ps.getOwnerUUID(),
                                    ps.getShopId(), isOwner, 1);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(false);           // isGuild
                        buf.writeUUID(ps.getOwnerUUID());
                        buf.writeBoolean(isOwner);
                        buf.writeInt(1);
                        buf.writeBoolean(true);            // hasShopId
                        buf.writeUUID(ps.getShopId());
                    }
            );
            return;
        }

        // Check guild shops
        GuildShop guildShop = market.getGuildShopAtBlock(pos, dimId).orElse(null);
        if (guildShop != null && guildShop.isOpen()) {
            event.setCanceled(true);
            final GuildShop gs = guildShop;
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.literal(gs.getGuildName() + "'s Shop");
                        }
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new ShopViewMenu(id, inv, gs.getGuildId(), null, false, 1);
                        }
                    },
                    buf -> {
                        buf.writeBoolean(true);  // isGuild
                        buf.writeUUID(gs.getGuildId());
                        buf.writeBoolean(false); // isOwner
                        buf.writeInt(1);
                        buf.writeBoolean(false); // no shopId for guild shops
                    }
            );
        }
    }

    // ── Block Break — clean up shop data when a shop chest OR sign is destroyed ─

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;

        BlockPos brokenPos = event.getPos();
        String dimId = serverLevel.dimension().location().toString();
        MarketData market = MarketData.get(server);

        // If the broken block is a wall sign, resolve the shop chest behind it.
        // WallSignBlock.FACING points AWAY from the wall, so the chest is in the
        // OPPOSITE direction.
        BlockPos shopPos = brokenPos; // default: assume the broken block IS the chest
        net.minecraft.world.level.block.state.BlockState brokenState =
                serverLevel.getBlockState(brokenPos);
        if (brokenState.getBlock() instanceof net.minecraft.world.level.block.WallSignBlock) {
            net.minecraft.core.Direction signFacing =
                    brokenState.getValue(net.minecraft.world.level.block.WallSignBlock.FACING);
            shopPos = brokenPos.relative(signFacing.getOpposite());
        }

        final BlockPos finalShopPos = shopPos;

        // Bank vault: clear registration if the vault block was broken
        GuildSavedData guildData = GuildSavedData.get(server);
        guildData.getVaultOwnerAt(finalShopPos, dimId).ifPresent(ownerUUID -> {
            guildData.clearBankVault(ownerUUID);
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                owner.sendSystemMessage(Component.literal(
                        "[Bank] Your vault block was broken — vault registration cleared.")
                        .withStyle(ChatFormatting.RED));
            }
        });

        // Player shop: remove the entire shop (listings are backed by this chest)
        market.getPlayerShopAtBlock(finalShopPos, dimId).ifPresent(shop -> {
            market.removeShopById(shop.getShopId());
            ServerPlayer owner = server.getPlayerList().getPlayer(shop.getOwnerUUID());
            if (owner != null) {
                owner.sendSystemMessage(Component.literal(
                        "[Shop] Your shop was removed (chest or sign broken).")
                        .withStyle(ChatFormatting.RED));
            }
        });

        // Guild shop: clear its block reference and all listings
        market.getGuildShopAtBlock(finalShopPos, dimId).ifPresent(shop -> {
            shop.clearShopBlock();
            shop.setOpen(false);
            new ArrayList<>(shop.getListings())
                    .forEach(l -> shop.removeListing(l.getListingId()));
            market.setDirty();
        });
    }
}
