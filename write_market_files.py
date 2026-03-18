import os

base_market = r'C:\Users\WarShip\Downloads\mods\Guilds\src\main\java\com\botzguildz\market'
os.makedirs(base_market, exist_ok=True)

# ── AuctionListing.java ───────────────────────────────────────────────────────
auction_listing = r'''package com.botzguildz.market;

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
'''

# ── BountyEntry.java ──────────────────────────────────────────────────────────
bounty_entry = r'''package com.botzguildz.market;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public class BountyEntry {

    private final UUID bountyId;
    private final UUID placerUUID;
    private String placerName;
    private final UUID targetUUID;
    private final long amount;
    private final long placedAt;

    public BountyEntry(UUID placerUUID, String placerName, UUID targetUUID, long amount) {
        this.bountyId = UUID.randomUUID();
        this.placerUUID = placerUUID;
        this.placerName = placerName;
        this.targetUUID = targetUUID;
        this.amount = amount;
        this.placedAt = System.currentTimeMillis();
    }

    private BountyEntry(UUID bountyId, UUID placerUUID, String placerName,
                        UUID targetUUID, long amount, long placedAt) {
        this.bountyId = bountyId;
        this.placerUUID = placerUUID;
        this.placerName = placerName;
        this.targetUUID = targetUUID;
        this.amount = amount;
        this.placedAt = placedAt;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("bountyId", bountyId);
        tag.putUUID("placerUUID", placerUUID);
        tag.putString("placerName", placerName);
        tag.putUUID("targetUUID", targetUUID);
        tag.putLong("amount", amount);
        tag.putLong("placedAt", placedAt);
        return tag;
    }

    public static BountyEntry fromNBT(CompoundTag tag) {
        return new BountyEntry(
                tag.getUUID("bountyId"),
                tag.getUUID("placerUUID"),
                tag.getString("placerName"),
                tag.getUUID("targetUUID"),
                tag.getLong("amount"),
                tag.getLong("placedAt")
        );
    }

    public UUID getBountyId() { return bountyId; }
    public UUID getPlacerUUID() { return placerUUID; }
    public String getPlacerName() { return placerName; }
    public UUID getTargetUUID() { return targetUUID; }
    public long getAmount() { return amount; }
    public long getPlacedAt() { return placedAt; }
}
'''

# ── MarketListing.java ────────────────────────────────────────────────────────
market_listing = r'''package com.botzguildz.market;

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
'''

# ── GuildShop.java ────────────────────────────────────────────────────────────
guild_shop = r'''package com.botzguildz.market;

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
'''

# ── PlayerShop.java ───────────────────────────────────────────────────────────
player_shop = r'''package com.botzguildz.market;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;

public class PlayerShop {

    private final UUID ownerUUID;
    private String ownerName;
    private final List<MarketListing> listings = new ArrayList<>();
    private boolean open;
    private BlockPos shopBlockPos;    // null if no physical block set
    private String shopDimensionId;   // null if no physical block set

    public PlayerShop(UUID ownerUUID, String ownerName) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.open = false;
        this.shopBlockPos = null;
        this.shopDimensionId = null;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
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
        PlayerShop shop = new PlayerShop(tag.getUUID("ownerUUID"), tag.getString("ownerName"));
        shop.open = tag.getBoolean("open");
        if (tag.contains("shopX")) {
            shop.shopBlockPos = new BlockPos(tag.getInt("shopX"), tag.getInt("shopY"), tag.getInt("shopZ"));
            shop.shopDimensionId = tag.getString("shopDim");
        }
        ListTag list = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) shop.listings.add(MarketListing.fromNBT(list.getCompound(i)));
        return shop;
    }

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
'''

# ── PendingDelivery.java ──────────────────────────────────────────────────────
pending_delivery = r'''package com.botzguildz.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class PendingDelivery {

    private final ItemStack item;  // may be ItemStack.EMPTY
    private final long coins;
    private final String message;

    public PendingDelivery(ItemStack item, long coins, String message) {
        this.item = item == null ? ItemStack.EMPTY : item.copy();
        this.coins = coins;
        this.message = message;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("item", item.save(new CompoundTag()));
        tag.putLong("coins", coins);
        tag.putString("message", message);
        return tag;
    }

    public static PendingDelivery fromNBT(CompoundTag tag) {
        ItemStack item = ItemStack.of(tag.getCompound("item"));
        return new PendingDelivery(item, tag.getLong("coins"), tag.getString("message"));
    }

    public ItemStack getItem() { return item.copy(); }
    public boolean hasItem() { return !item.isEmpty(); }
    public long getCoins() { return coins; }
    public String getMessage() { return message; }
}
'''

files = {
    os.path.join(base_market, 'AuctionListing.java'):  auction_listing,
    os.path.join(base_market, 'BountyEntry.java'):      bounty_entry,
    os.path.join(base_market, 'MarketListing.java'):    market_listing,
    os.path.join(base_market, 'GuildShop.java'):        guild_shop,
    os.path.join(base_market, 'PlayerShop.java'):       player_shop,
    os.path.join(base_market, 'PendingDelivery.java'):  pending_delivery,
}

for path, content in files.items():
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Written:', path)

print('All market files done.')
