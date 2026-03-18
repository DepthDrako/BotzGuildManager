package com.botzguildz.data;

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
