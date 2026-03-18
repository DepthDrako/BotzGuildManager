package com.botzguildz.data;

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
