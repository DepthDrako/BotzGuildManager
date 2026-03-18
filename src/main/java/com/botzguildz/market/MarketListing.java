package com.botzguildz.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public class MarketListing {

    private final UUID listingId;
    private final ItemStack item;
    private final long priceEach;
    private int stock; // -1 = unlimited

    public MarketListing(ItemStack item, long priceEach, int stock) {
        this.listingId = UUID.randomUUID();
        this.item = item.copy();
        this.priceEach = priceEach;
        this.stock = stock;
    }

    private MarketListing(UUID listingId, ItemStack item, long priceEach, int stock) {
        this.listingId = listingId;
        this.item = item;
        this.priceEach = priceEach;
        this.stock = stock;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("listingId", listingId);
        tag.put("item", item.save(new CompoundTag()));
        tag.putLong("priceEach", priceEach);
        tag.putInt("stock", stock);
        return tag;
    }

    public static MarketListing fromNBT(CompoundTag tag) {
        return new MarketListing(
                tag.getUUID("listingId"),
                ItemStack.of(tag.getCompound("item")),
                tag.getLong("priceEach"),
                tag.getInt("stock")
        );
    }

    public UUID getListingId() { return listingId; }
    public ItemStack getItem() { return item.copy(); }
    public long getPriceEach() { return priceEach; }
    public int getStock() { return stock; }
    public boolean isUnlimited() { return stock == -1; }

    /** Returns false if out of stock (stock was 0 before call) */
    public boolean decrementStock() {
        if (stock == -1) return true;
        if (stock <= 0) return false;
        stock--;
        return true;
    }
}
