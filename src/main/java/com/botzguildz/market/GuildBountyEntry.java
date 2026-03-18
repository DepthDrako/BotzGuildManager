package com.botzguildz.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * One item-collection bounty posted to a guild's bounty board.
 * The {@link #rewardAmount} is locked from the guild bank when the entry is created
 * and is paid out (or refunded) when the bounty is claimed or cancelled.
 */
public class GuildBountyEntry {

    private UUID   entryId;
    private UUID   guildId;
    private UUID   posterUUID;
    private String posterName;
    /** Count is always normalised to 1 — used for item-type matching only. */
    private ItemStack targetItem;
    private int    quantityRequired;
    private long   rewardAmount;
    private long   postedAt;
    private boolean active;

    public GuildBountyEntry(UUID entryId, UUID guildId, UUID posterUUID, String posterName,
                             ItemStack targetItem, int quantityRequired, long rewardAmount) {
        this.entryId          = entryId;
        this.guildId          = guildId;
        this.posterUUID       = posterUUID;
        this.posterName       = posterName;
        ItemStack copy = targetItem.copy();
        copy.setCount(1);
        this.targetItem       = copy;
        this.quantityRequired = quantityRequired;
        this.rewardAmount     = rewardAmount;
        this.postedAt         = System.currentTimeMillis();
        this.active           = true;
    }

    /** Returns true if {@code stack} is the same item type as the target (ignores count / NBT). */
    public boolean matchesItem(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItem(this.targetItem, stack);
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("entryId",          entryId);
        tag.putUUID("guildId",          guildId);
        tag.putUUID("posterUUID",       posterUUID);
        tag.putString("posterName",     posterName);
        tag.put("targetItem",           targetItem.save(new CompoundTag()));
        tag.putInt("quantityRequired",  quantityRequired);
        tag.putLong("rewardAmount",     rewardAmount);
        tag.putLong("postedAt",         postedAt);
        tag.putBoolean("active",        active);
        return tag;
    }

    public static GuildBountyEntry fromNBT(CompoundTag tag) {
        UUID        entryId   = tag.getUUID("entryId");
        UUID        guildId   = tag.getUUID("guildId");
        UUID        poster    = tag.getUUID("posterUUID");
        String      name      = tag.getString("posterName");
        ItemStack   item      = ItemStack.of(tag.getCompound("targetItem"));
        int         qty       = tag.getInt("quantityRequired");
        long        reward    = tag.getLong("rewardAmount");
        GuildBountyEntry e    = new GuildBountyEntry(entryId, guildId, poster, name, item, qty, reward);
        e.postedAt            = tag.getLong("postedAt");
        e.active              = tag.getBoolean("active");
        return e;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID      getEntryId()          { return entryId; }
    public UUID      getGuildId()          { return guildId; }
    public UUID      getPosterUUID()       { return posterUUID; }
    public String    getPosterName()       { return posterName; }
    public ItemStack getTargetItem()       { return targetItem; }
    public int       getQuantityRequired() { return quantityRequired; }
    public long      getRewardAmount()     { return rewardAmount; }
    public long      getPostedAt()         { return postedAt; }
    public boolean   isActive()            { return active; }
    public void      setActive(boolean v)  { this.active = v; }
}
