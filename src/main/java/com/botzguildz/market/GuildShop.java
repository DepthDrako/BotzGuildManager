package com.botzguildz.market;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;

public class GuildShop {

    private final UUID guildId;
    private String guildName;
    private final List<MarketListing> listings = new ArrayList<>();
    private boolean open;
    private BlockPos shopBlockPos;
    private String shopDimensionId;

    public GuildShop(UUID guildId, String guildName) {
        this.guildId = guildId;
        this.guildName = guildName;
        this.open = false;
        this.shopBlockPos = null;
        this.shopDimensionId = null;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("guildId", guildId);
        tag.putString("guildName", guildName);
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

    public static GuildShop fromNBT(CompoundTag tag) {
        GuildShop shop = new GuildShop(tag.getUUID("guildId"), tag.getString("guildName"));
        shop.open = tag.getBoolean("open");
        if (tag.contains("shopX")) {
            shop.shopBlockPos = new BlockPos(tag.getInt("shopX"), tag.getInt("shopY"), tag.getInt("shopZ"));
            shop.shopDimensionId = tag.getString("shopDim");
        }
        ListTag list = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) shop.listings.add(MarketListing.fromNBT(list.getCompound(i)));
        return shop;
    }

    public UUID getGuildId() { return guildId; }
    public String getGuildName() { return guildName; }
    public void setGuildName(String name) { this.guildName = name; }
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

    public int getTotalStock() {
        int total = 0;
        for (MarketListing l : listings) total += (l.isUnlimited() ? 1 : l.getStock());
        return total;
    }
}
