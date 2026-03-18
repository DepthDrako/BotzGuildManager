package com.botzguildz.market;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;

public class PlayerShop {

    private final UUID shopId;     // unique ID for THIS specific shop (one player can have many)
    private final UUID ownerUUID;
    private String ownerName;
    private final List<MarketListing> listings = new ArrayList<>();
    private boolean open;
    private BlockPos shopBlockPos;    // null if no physical block set
    private String shopDimensionId;   // null if no physical block set

    /** Create a brand-new shop — shopId is generated automatically. */
    public PlayerShop(UUID ownerUUID, String ownerName) {
        this.shopId    = UUID.randomUUID();
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.open = false;
        this.shopBlockPos = null;
        this.shopDimensionId = null;
    }

    /** Private constructor used only by fromNBT. */
    private PlayerShop(UUID shopId, UUID ownerUUID, String ownerName) {
        this.shopId    = shopId;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.open = false;
        this.shopBlockPos = null;
        this.shopDimensionId = null;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("shopId",    shopId);
        tag.putUUID("ownerUUID", ownerUUID);
        tag.putString("ownerName", ownerName);
        tag.putBoolean("open", open);
        if (shopBlockPos != null) {
            tag.putInt("shopX", shopBlockPos.getX());
            tag.putInt("shopY", shopBlockPos.getY());
            tag.putInt("shopZ", shopBlockPos.getZ());
            tag.putString("shopDim", shopDimensionId);
        }
        ListTag list = new ListTag();
        for (MarketListing ml : listings) list.add(ml.toNBT());
        tag.put("listings", list);
        return tag;
    }

    public static PlayerShop fromNBT(CompoundTag tag) {
        // Support old saves that didn't store shopId
        UUID shopId = tag.contains("shopId") ? tag.getUUID("shopId") : UUID.randomUUID();
        PlayerShop shop = new PlayerShop(shopId, tag.getUUID("ownerUUID"), tag.getString("ownerName"));
        shop.open = tag.getBoolean("open");
        if (tag.contains("shopX")) {
            shop.shopBlockPos = new BlockPos(tag.getInt("shopX"), tag.getInt("shopY"), tag.getInt("shopZ"));
            shop.shopDimensionId = tag.getString("shopDim");
        }
        ListTag list = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) shop.listings.add(MarketListing.fromNBT(list.getCompound(i)));
        return shop;
    }

    public UUID getShopId()    { return shopId; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String name) { this.ownerName = name; }
    public List<MarketListing> getListings() { return Collections.unmodifiableList(listings); }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public BlockPos getShopBlockPos() { return shopBlockPos; }
    public String getShopDimensionId() { return shopDimensionId; }

    public void setShopBlock(BlockPos pos, String dimensionId) {
        this.shopBlockPos = pos;
        this.shopDimensionId = dimensionId;
    }

    public void clearShopBlock() {
        this.shopBlockPos = null;
        this.shopDimensionId = null;
    }

    public void addListing(MarketListing listing) { listings.add(listing); }

    public boolean removeListing(UUID listingId) {
        return listings.removeIf(l -> l.getListingId().equals(listingId));
    }

    public Optional<MarketListing> getListing(UUID listingId) {
        return listings.stream().filter(l -> l.getListingId().equals(listingId)).findFirst();
    }
}
