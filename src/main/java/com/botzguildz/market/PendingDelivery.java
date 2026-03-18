package com.botzguildz.market;

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
