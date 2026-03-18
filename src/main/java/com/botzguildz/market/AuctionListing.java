package com.botzguildz.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public class AuctionListing {

    public enum Type { FIXED, TIMED }

    private final UUID listingId;
    private final UUID sellerUUID;
    private final ItemStack item;
    private final Type type;
    private long price;          // fixed price OR current bid (timed)
    private final long startingBid; // only meaningful for TIMED
    private UUID currentBidder;   // nullable -- only for TIMED
    private final long expiresAt; // epoch ms -- only for TIMED; 0 for FIXED
    private boolean active;

    /** Fixed-price constructor */
    public AuctionListing(UUID sellerUUID, ItemStack item, long price) {
        this.listingId = UUID.randomUUID();
        this.sellerUUID = sellerUUID;
        this.item = item.copy();
        this.type = Type.FIXED;
        this.price = price;
        this.startingBid = price;
        this.currentBidder = null;
        this.expiresAt = 0L;
        this.active = true;
    }

    /** Timed-auction constructor */
    public AuctionListing(UUID sellerUUID, ItemStack item, long startingBid, long durationMillis) {
        this.listingId = UUID.randomUUID();
        this.sellerUUID = sellerUUID;
        this.item = item.copy();
        this.type = Type.TIMED;
        this.price = startingBid;
        this.startingBid = startingBid;
        this.currentBidder = null;
        this.expiresAt = System.currentTimeMillis() + durationMillis;
        this.active = true;
    }

    // NBT constructor (private)
    private AuctionListing(UUID listingId, UUID sellerUUID, ItemStack item, Type type,
                            long price, long startingBid, UUID currentBidder,
                            long expiresAt, boolean active) {
        this.listingId = listingId;
        this.sellerUUID = sellerUUID;
        this.item = item;
        this.type = type;
        this.price = price;
        this.startingBid = startingBid;
        this.currentBidder = currentBidder;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("listingId", listingId);
        tag.putUUID("sellerUUID", sellerUUID);
        tag.put("item", item.save(new CompoundTag()));
        tag.putString("type", type.name());
        tag.putLong("price", price);
        tag.putLong("startingBid", startingBid);
        if (currentBidder != null) tag.putUUID("currentBidder", currentBidder);
        tag.putLong("expiresAt", expiresAt);
        tag.putBoolean("active", active);
        return tag;
    }

    public static AuctionListing fromNBT(CompoundTag tag) {
        UUID listingId = tag.getUUID("listingId");
        UUID sellerUUID = tag.getUUID("sellerUUID");
        ItemStack item = ItemStack.of(tag.getCompound("item"));
        Type type = Type.valueOf(tag.getString("type"));
        long price = tag.getLong("price");
        long startingBid = tag.getLong("startingBid");
        UUID currentBidder = tag.hasUUID("currentBidder") ? tag.getUUID("currentBidder") : null;
        long expiresAt = tag.getLong("expiresAt");
        boolean active = tag.getBoolean("active");
        return new AuctionListing(listingId, sellerUUID, item, type, price, startingBid,
                currentBidder, expiresAt, active);
    }

    // --- Getters ---
    public UUID getListingId() { return listingId; }
    public UUID getSellerUUID() { return sellerUUID; }
    public ItemStack getItem() { return item.copy(); }
    public Type getType() { return type; }
    public long getPrice() { return price; }
    public long getStartingBid() { return startingBid; }
    public UUID getCurrentBidder() { return currentBidder; }
    public long getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }
    public boolean isExpired() { return type == Type.TIMED && System.currentTimeMillis() > expiresAt; }

    // --- Mutators ---
    public void setActive(boolean active) { this.active = active; }
    public void setCurrentBid(long bid, UUID bidder) { this.price = bid; this.currentBidder = bidder; }

    /** Remaining time in seconds; 0 if not timed or already expired */
    public long getRemainingSeconds() {
        if (type != Type.TIMED) return 0;
        long rem = (expiresAt - System.currentTimeMillis()) / 1000L;
        return Math.max(0, rem);
    }

    public String formatTimeLeft() {
        long secs = getRemainingSeconds();
        if (secs <= 0) return "Expired";
        long days = secs / 86400; secs %= 86400;
        long hours = secs / 3600; secs %= 3600;
        long mins = secs / 60; secs %= 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m " + secs + "s";
    }
}
