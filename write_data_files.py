import os

base_market = r'C:\Users\WarShip\Downloads\mods\Guilds\src\main\java\com\botzguildz\market'
base_data   = r'C:\Users\WarShip\Downloads\mods\Guilds\src\main\java\com\botzguildz\data'

# ── AuctionHouseData.java ─────────────────────────────────────────────────────
auction_house = r'''package com.botzguildz.data;

import com.botzguildz.market.AuctionListing;
import com.botzguildz.market.PendingDelivery;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;

public class AuctionHouseData extends SavedData {

    private static final String DATA_NAME = "botzguildz_auction";

    private final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<PendingDelivery>> pendingDeliveries = new HashMap<>();

    // ---- Access ----

    public static AuctionHouseData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                tag -> load(tag),
                AuctionHouseData::new,
                DATA_NAME
        );
    }

    // ---- Listing management ----

    public void addListing(AuctionListing listing) {
        listings.put(listing.getListingId(), listing);
        setDirty();
    }

    public Optional<AuctionListing> getListing(UUID listingId) {
        return Optional.ofNullable(listings.get(listingId));
    }

    public List<AuctionListing> getActiveListings() {
        List<AuctionListing> result = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            if (l.isActive() && !l.isExpired()) result.add(l);
        }
        return result;
    }

    public List<AuctionListing> getListingsBySeller(UUID sellerUUID) {
        List<AuctionListing> result = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            if (l.isActive() && l.getSellerUUID().equals(sellerUUID)) result.add(l);
        }
        return result;
    }

    /**
     * Cancel a listing owned by sellerUUID. Returns the item to seller via pending delivery.
     * Returns false if listing not found or not owned by that seller.
     */
    public boolean cancelListing(UUID listingId, UUID sellerUUID, String sellerName) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null || !listing.isActive() || !listing.getSellerUUID().equals(sellerUUID)) return false;
        listing.setActive(false);
        // Refund item to seller
        queueDelivery(sellerUUID, listing.getItem(), 0,
                "Your listing was cancelled: " + listing.getItem().getHoverName().getString());
        // If timed and has a bidder, refund their bid
        if (listing.getType() == AuctionListing.Type.TIMED && listing.getCurrentBidder() != null) {
            queueDelivery(listing.getCurrentBidder(), ItemStack.EMPTY, listing.getPrice(),
                    "Your bid was refunded (listing cancelled): " + listing.getItem().getHoverName().getString());
        }
        setDirty();
        return true;
    }

    /**
     * Place a bid on a TIMED listing. Refunds the previous bidder if outbid.
     * Returns null on success, or an error message string on failure.
     */
    public String placeBid(UUID listingId, ServerPlayer bidder, long bidAmount) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null || !listing.isActive()) return "Listing not found.";
        if (listing.getType() != AuctionListing.Type.TIMED) return "This is a fixed-price listing.";
        if (listing.isExpired()) return "This auction has already ended.";
        if (listing.getSellerUUID().equals(bidder.getUUID())) return "You cannot bid on your own auction.";
        if (bidAmount <= listing.getPrice()) return "Your bid must be higher than the current bid of " + listing.getPrice() + ".";

        // Refund previous bidder
        if (listing.getCurrentBidder() != null) {
            queueDelivery(listing.getCurrentBidder(), ItemStack.EMPTY, listing.getPrice(),
                    "You were outbid on: " + listing.getItem().getHoverName().getString());
        }

        listing.setCurrentBid(bidAmount, bidder.getUUID());
        setDirty();
        return null; // success
    }

    // ---- Pending deliveries ----

    public void queueDelivery(UUID recipientUUID, ItemStack item, long coins, String message) {
        pendingDeliveries.computeIfAbsent(recipientUUID, k -> new ArrayList<>())
                .add(new PendingDelivery(item, coins, message));
        setDirty();
    }

    public void deliverPending(ServerPlayer player) {
        List<PendingDelivery> pending = pendingDeliveries.remove(player.getUUID());
        if (pending == null || pending.isEmpty()) return;
        for (PendingDelivery delivery : pending) {
            if (delivery.hasItem()) {
                boolean added = player.getInventory().add(delivery.getItem());
                if (!added) player.drop(delivery.getItem(), false);
            }
            if (delivery.getCoins() > 0) {
                GuildSavedData.get(player.getServer()).addToWallet(player.getUUID(), delivery.getCoins());
            }
            player.sendSystemMessage(Component.literal(
                    "[AH] " + delivery.getMessage()).withStyle(ChatFormatting.YELLOW));
        }
        setDirty();
    }

    // ---- Tick -- expire timed auctions ----

    public void tick(MinecraftServer server) {
        boolean dirty = false;
        for (AuctionListing listing : new ArrayList<>(listings.values())) {
            if (!listing.isActive() || listing.getType() != AuctionListing.Type.TIMED) continue;
            if (!listing.isExpired()) continue;

            listing.setActive(false);
            dirty = true;

            if (listing.getCurrentBidder() != null) {
                // Someone won -- deliver item to winner, coins to seller
                queueDelivery(listing.getCurrentBidder(), listing.getItem(), 0,
                        "You won the auction for: " + listing.getItem().getHoverName().getString()
                                + " (" + listing.getPrice() + " coins)");
                queueDelivery(listing.getSellerUUID(), ItemStack.EMPTY, listing.getPrice(),
                        "Your auction sold: " + listing.getItem().getHoverName().getString()
                                + " for " + listing.getPrice() + " coins");
            } else {
                // No bids -- return item to seller
                queueDelivery(listing.getSellerUUID(), listing.getItem(), 0,
                        "Your auction ended with no bids: " + listing.getItem().getHoverName().getString());
            }

            // Deliver immediately to any online players
            ServerPlayer winner = listing.getCurrentBidder() != null
                    ? server.getPlayerList().getPlayer(listing.getCurrentBidder()) : null;
            if (winner != null) deliverPending(winner);
            ServerPlayer seller = server.getPlayerList().getPlayer(listing.getSellerUUID());
            if (seller != null) deliverPending(seller);
        }
        if (dirty) setDirty();
    }

    // ---- NBT persistence ----

    public static AuctionHouseData load(CompoundTag tag) {
        AuctionHouseData data = new AuctionHouseData();
        ListTag listingsTag = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < listingsTag.size(); i++) {
            AuctionListing l = AuctionListing.fromNBT(listingsTag.getCompound(i));
            data.listings.put(l.getListingId(), l);
        }
        CompoundTag deliveries = tag.getCompound("pendingDeliveries");
        for (String key : deliveries.getAllKeys()) {
            UUID uuid = UUID.fromString(key);
            ListTag dList = deliveries.getList(key, Tag.TAG_COMPOUND);
            List<PendingDelivery> list = new ArrayList<>();
            for (int i = 0; i < dList.size(); i++) list.add(PendingDelivery.fromNBT(dList.getCompound(i)));
            data.pendingDeliveries.put(uuid, list);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag listingsTag = new ListTag();
        for (AuctionListing l : listings.values()) listingsTag.add(l.toNBT());
        tag.put("listings", listingsTag);

        CompoundTag deliveries = new CompoundTag();
        for (Map.Entry<UUID, List<PendingDelivery>> entry : pendingDeliveries.entrySet()) {
            ListTag dList = new ListTag();
            for (PendingDelivery d : entry.getValue()) dList.add(d.toNBT());
            deliveries.put(entry.getKey().toString(), dList);
        }
        tag.put("pendingDeliveries", deliveries);
        return tag;
    }
}
'''

# ── BountyData.java ───────────────────────────────────────────────────────────
bounty_data = r'''package com.botzguildz.data;

import com.botzguildz.market.BountyEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;

public class BountyData extends SavedData {

    private static final String DATA_NAME = "botzguildz_bounties";
    // Key = target UUID, Value = list of bounties placed on that player
    private final Map<UUID, List<BountyEntry>> bounties = new HashMap<>();

    public static BountyData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                tag -> load(tag),
                BountyData::new,
                DATA_NAME
        );
    }

    public void placeBounty(UUID placerUUID, String placerName, UUID targetUUID, long amount) {
        bounties.computeIfAbsent(targetUUID, k -> new ArrayList<>())
                .add(new BountyEntry(placerUUID, placerName, targetUUID, amount));
        setDirty();
    }

    public long getTotalBounty(UUID targetUUID) {
        List<BountyEntry> list = bounties.get(targetUUID);
        if (list == null) return 0;
        return list.stream().mapToLong(BountyEntry::getAmount).sum();
    }

    public List<BountyEntry> getBountiesOn(UUID targetUUID) {
        return bounties.getOrDefault(targetUUID, Collections.emptyList());
    }

    public List<BountyEntry> getBountiesByPlacer(UUID placerUUID) {
        List<BountyEntry> result = new ArrayList<>();
        for (List<BountyEntry> list : bounties.values())
            for (BountyEntry e : list) if (e.getPlacerUUID().equals(placerUUID)) result.add(e);
        return result;
    }

    /**
     * Collect all bounties on targetUUID, paying them to killer.
     * Returns total coins collected (already added to killer's wallet).
     */
    public long collectBounty(UUID killerUUID, UUID targetUUID, MinecraftServer server) {
        List<BountyEntry> list = bounties.remove(targetUUID);
        if (list == null || list.isEmpty()) return 0;
        long total = list.stream().mapToLong(BountyEntry::getAmount).sum();
        GuildSavedData.get(server).addToWallet(killerUUID, total);
        setDirty();
        return total;
    }

    /**
     * Cancel a specific bounty by its placer. Refunds the amount.
     * Returns the refunded amount, or -1 if not found/not owned.
     */
    public long cancelBounty(UUID bountyId, UUID placerUUID, MinecraftServer server) {
        for (Map.Entry<UUID, List<BountyEntry>> entry : bounties.entrySet()) {
            List<BountyEntry> list = entry.getValue();
            for (Iterator<BountyEntry> it = list.iterator(); it.hasNext(); ) {
                BountyEntry b = it.next();
                if (b.getBountyId().equals(bountyId) && b.getPlacerUUID().equals(placerUUID)) {
                    it.remove();
                    if (list.isEmpty()) bounties.remove(entry.getKey());
                    GuildSavedData.get(server).addToWallet(placerUUID, b.getAmount());
                    setDirty();
                    return b.getAmount();
                }
            }
        }
        return -1;
    }

    /** All targets with at least one bounty, sorted by total bounty descending */
    public List<Map.Entry<UUID, Long>> getTopBounties(int limit) {
        List<Map.Entry<UUID, Long>> result = new ArrayList<>();
        for (UUID target : bounties.keySet()) {
            long total = getTotalBounty(target);
            if (total > 0) result.add(Map.entry(target, total));
        }
        result.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return result.subList(0, Math.min(limit, result.size()));
    }

    public static BountyData load(CompoundTag tag) {
        BountyData data = new BountyData();
        CompoundTag bountiesTag = tag.getCompound("bounties");
        for (String key : bountiesTag.getAllKeys()) {
            UUID targetUUID = UUID.fromString(key);
            ListTag list = bountiesTag.getList(key, Tag.TAG_COMPOUND);
            List<BountyEntry> entries = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) entries.add(BountyEntry.fromNBT(list.getCompound(i)));
            data.bounties.put(targetUUID, entries);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag bountiesTag = new CompoundTag();
        for (Map.Entry<UUID, List<BountyEntry>> entry : bounties.entrySet()) {
            ListTag list = new ListTag();
            for (BountyEntry b : entry.getValue()) list.add(b.toNBT());
            bountiesTag.put(entry.getKey().toString(), list);
        }
        tag.put("bounties", bountiesTag);
        return tag;
    }
}
'''

# ── MarketData.java ───────────────────────────────────────────────────────────
market_data = r'''package com.botzguildz.data;

import com.botzguildz.market.GuildShop;
import com.botzguildz.market.PlayerShop;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;

public class MarketData extends SavedData {

    private static final String DATA_NAME = "botzguildz_market";
    private final Map<UUID, GuildShop> guildShops = new LinkedHashMap<>();
    private final Map<UUID, PlayerShop> playerShops = new LinkedHashMap<>();

    public static MarketData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                tag -> load(tag),
                MarketData::new,
                DATA_NAME
        );
    }

    // ---- Guild shops ----

    public GuildShop getOrCreateGuildShop(UUID guildId, String guildName) {
        return guildShops.computeIfAbsent(guildId, k -> new GuildShop(guildId, guildName));
    }

    public Optional<GuildShop> getGuildShop(UUID guildId) {
        return Optional.ofNullable(guildShops.get(guildId));
    }

    public Collection<GuildShop> getAllGuildShops() { return guildShops.values(); }

    // ---- Player shops ----

    public PlayerShop getOrCreatePlayerShop(UUID ownerUUID, String ownerName) {
        return playerShops.computeIfAbsent(ownerUUID, k -> new PlayerShop(ownerUUID, ownerName));
    }

    public Optional<PlayerShop> getPlayerShop(UUID ownerUUID) {
        return Optional.ofNullable(playerShops.get(ownerUUID));
    }

    public Collection<PlayerShop> getAllPlayerShops() { return playerShops.values(); }

    /** Find a player shop registered at a specific block position in a given dimension */
    public Optional<PlayerShop> getPlayerShopAtBlock(BlockPos pos, String dimensionId) {
        for (PlayerShop shop : playerShops.values()) {
            if (pos.equals(shop.getShopBlockPos()) && dimensionId.equals(shop.getShopDimensionId()))
                return Optional.of(shop);
        }
        return Optional.empty();
    }

    /** Find a guild shop registered at a specific block position in a given dimension */
    public Optional<GuildShop> getGuildShopAtBlock(BlockPos pos, String dimensionId) {
        for (GuildShop shop : guildShops.values()) {
            if (pos.equals(shop.getShopBlockPos()) && dimensionId.equals(shop.getShopDimensionId()))
                return Optional.of(shop);
        }
        return Optional.empty();
    }

    public static MarketData load(CompoundTag tag) {
        MarketData data = new MarketData();
        ListTag gList = tag.getList("guildShops", Tag.TAG_COMPOUND);
        for (int i = 0; i < gList.size(); i++) {
            GuildShop shop = GuildShop.fromNBT(gList.getCompound(i));
            data.guildShops.put(shop.getGuildId(), shop);
        }
        ListTag pList = tag.getList("playerShops", Tag.TAG_COMPOUND);
        for (int i = 0; i < pList.size(); i++) {
            PlayerShop shop = PlayerShop.fromNBT(pList.getCompound(i));
            data.playerShops.put(shop.getOwnerUUID(), shop);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag gList = new ListTag();
        for (GuildShop shop : guildShops.values()) gList.add(shop.toNBT());
        tag.put("guildShops", gList);
        ListTag pList = new ListTag();
        for (PlayerShop shop : playerShops.values()) pList.add(shop.toNBT());
        tag.put("playerShops", pList);
        return tag;
    }
}
'''

files = {
    os.path.join(base_data, 'AuctionHouseData.java'): auction_house,
    os.path.join(base_data, 'BountyData.java'):        bounty_data,
    os.path.join(base_data, 'MarketData.java'):        market_data,
}

for path, content in files.items():
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Written:', path)

print('All done.')
