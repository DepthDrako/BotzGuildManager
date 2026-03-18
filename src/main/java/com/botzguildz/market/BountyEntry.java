package com.botzguildz.market;

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
