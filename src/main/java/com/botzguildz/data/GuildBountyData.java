package com.botzguildz.data;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.market.GuildBountyEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Persistent store for all guild item-collection bounties.
 * Saved as "botzguildz_guild_bounties.dat" in the overworld data folder.
 *
 * When a bounty is posted, its reward is immediately withdrawn from the guild bank
 * (held in escrow inside this entry).  On claim the reward is paid to the claimer;
 * on cancel it is refunded to the guild bank.
 */
public class GuildBountyData extends SavedData {

    public static final String DATA_NAME = "botzguildz_guild_bounties";

    /** Guild UUID → list of active bounty entries for that guild. */
    private final Map<UUID, List<GuildBountyEntry>> bounties = new HashMap<>();

    // ── Access ────────────────────────────────────────────────────────────────

    public static GuildBountyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                GuildBountyData::load,
                GuildBountyData::new,
                DATA_NAME
        );
    }

    private static GuildBountyData load(CompoundTag nbt) {
        GuildBountyData data = new GuildBountyData();
        ListTag list = nbt.getList("entries", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            GuildBountyEntry e = GuildBountyEntry.fromNBT((CompoundTag) t);
            if (e.isActive()) {
                data.bounties.computeIfAbsent(e.getGuildId(), k -> new ArrayList<>()).add(e);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (List<GuildBountyEntry> entries : bounties.values()) {
            for (GuildBountyEntry e : entries) {
                list.add(e.toNBT());
            }
        }
        tag.put("entries", list);
        return tag;
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /** Add a newly-created bounty (reward already deducted from guild bank by caller). */
    public void addBounty(GuildBountyEntry entry) {
        bounties.computeIfAbsent(entry.getGuildId(), k -> new ArrayList<>()).add(entry);
        setDirty();
    }

    /** Active bounties for a guild, newest first. */
    public List<GuildBountyEntry> getBountiesForGuild(UUID guildId) {
        return bounties.getOrDefault(guildId, Collections.emptyList())
                .stream()
                .filter(GuildBountyEntry::isActive)
                .sorted(Comparator.comparingLong(GuildBountyEntry::getPostedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Cancel a bounty and refund its reward to the guild bank.
     * Returns the refunded amount, or -1 if not found.
     * The caller must still call {@link GuildSavedData#setDirty()} if the guild was modified.
     */
    public long cancelBounty(UUID entryId, UUID guildId, Guild guild) {
        List<GuildBountyEntry> list = bounties.get(guildId);
        if (list == null) return -1;
        GuildBountyEntry entry = list.stream()
                .filter(e -> e.getEntryId().equals(entryId) && e.isActive())
                .findFirst().orElse(null);
        if (entry == null) return -1;

        entry.setActive(false);
        list.remove(entry);
        guild.deposit(entry.getRewardAmount()); // refund to guild bank
        setDirty();
        return entry.getRewardAmount();
    }

    /**
     * Attempt to claim a bounty.
     * Checks the player has enough items, removes them from inventory, and pays the reward.
     * @return true on success, false if the player lacks the items or the entry was not found.
     */
    public boolean claimBounty(UUID entryId, UUID guildId,
                                ServerPlayer claimant, MinecraftServer server) {
        List<GuildBountyEntry> list = bounties.get(guildId);
        if (list == null) return false;
        GuildBountyEntry entry = list.stream()
                .filter(e -> e.getEntryId().equals(entryId) && e.isActive())
                .findFirst().orElse(null);
        if (entry == null) return false;

        // Count matching items in inventory
        int found = 0;
        for (int i = 0; i < claimant.getInventory().getContainerSize(); i++) {
            ItemStack s = claimant.getInventory().getItem(i);
            if (entry.matchesItem(s)) found += s.getCount();
        }
        if (found < entry.getQuantityRequired()) return false;

        // Deduct items
        int toRemove = entry.getQuantityRequired();
        for (int i = 0; i < claimant.getInventory().getContainerSize() && toRemove > 0; i++) {
            ItemStack s = claimant.getInventory().getItem(i);
            if (entry.matchesItem(s)) {
                int take = Math.min(s.getCount(), toRemove);
                s.shrink(take);
                toRemove -= take;
            }
        }

        // Pay the reward (already withdrawn from guild bank when posted)
        CurrencyManager.give(claimant, entry.getRewardAmount());

        // Mark inactive and remove
        entry.setActive(false);
        list.remove(entry);

        // Guild log
        GuildSavedData guildData = GuildSavedData.get(server);
        Guild guild = guildData.getGuildById(entry.getGuildId());
        if (guild != null) {
            guild.addLog(claimant.getName().getString() + " claimed bounty: "
                    + entry.getQuantityRequired() + "x "
                    + entry.getTargetItem().getHoverName().getString()
                    + " → " + CurrencyManager.format(entry.getRewardAmount()) + ".");
            guildData.setDirty();
        }

        setDirty();
        return true;
    }

    /** Lookup a single entry (for cancel confirmation text etc.). */
    public Optional<GuildBountyEntry> getEntry(UUID entryId, UUID guildId) {
        return bounties.getOrDefault(guildId, Collections.emptyList()).stream()
                .filter(e -> e.getEntryId().equals(entryId) && e.isActive())
                .findFirst();
    }
}
