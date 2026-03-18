package com.botzguildz.data;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Persistent item vault for each guild — 45 slots (5 rows of 9).
 * Saved to {@code botzguildz_vault.dat} in the overworld data folder.
 *
 * <p>All members can deposit; only members with {@link RankPermission#MANAGE_VAULT}
 * (or the leader) can withdraw.
 */
public class GuildVaultData extends SavedData {

    public static final int    VAULT_SIZE = 45;
    private static final String DATA_NAME = "botzguildz_vault";

    private final Map<UUID, ItemStack[]> vaults = new HashMap<>();

    // ── Static access ─────────────────────────────────────────────────────────

    public static GuildVaultData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                GuildVaultData::load,
                GuildVaultData::new,
                DATA_NAME
        );
    }

    // ── Slot access ───────────────────────────────────────────────────────────

    public ItemStack[] getVault(UUID guildId) {
        return vaults.computeIfAbsent(guildId, k -> {
            ItemStack[] slots = new ItemStack[VAULT_SIZE];
            Arrays.fill(slots, ItemStack.EMPTY);
            return slots;
        });
    }

    public ItemStack getSlot(UUID guildId, int slot) {
        if (slot < 0 || slot >= VAULT_SIZE) return ItemStack.EMPTY;
        return getVault(guildId)[slot];
    }

    public void setSlot(UUID guildId, int slot, ItemStack stack) {
        if (slot < 0 || slot >= VAULT_SIZE) return;
        getVault(guildId)[slot] = stack == null ? ItemStack.EMPTY : stack;
        setDirty();
    }

    /**
     * Tries to insert {@code stack} into the vault, merging with existing
     * stacks first, then placing in empty slots.
     *
     * @return the remainder (empty if fully inserted).
     */
    public ItemStack addItem(UUID guildId, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack[] v   = getVault(guildId);
        ItemStack   rem = stack.copy();

        // Merge with existing compatible stacks
        for (int i = 0; i < VAULT_SIZE && !rem.isEmpty(); i++) {
            if (!v[i].isEmpty() && ItemStack.isSameItemSameTags(v[i], rem)) {
                int space = rem.getMaxStackSize() - v[i].getCount();
                if (space > 0) {
                    int add = Math.min(space, rem.getCount());
                    v[i].grow(add);
                    rem.shrink(add);
                }
            }
        }
        // Empty slots
        for (int i = 0; i < VAULT_SIZE && !rem.isEmpty(); i++) {
            if (v[i].isEmpty()) {
                v[i] = rem.copy();
                rem  = ItemStack.EMPTY;
            }
        }
        setDirty();
        return rem;
    }

    /** Returns how many slots are occupied. */
    public int usedSlots(UUID guildId) {
        int count = 0;
        for (ItemStack s : getVault(guildId)) if (!s.isEmpty()) count++;
        return count;
    }

    // ── SavedData ─────────────────────────────────────────────────────────────

    public static GuildVaultData load(CompoundTag tag) {
        GuildVaultData data = new GuildVaultData();
        CompoundTag vMap = tag.getCompound("vaults");
        for (String key : vMap.getAllKeys()) {
            UUID guildId = UUID.fromString(key);
            ListTag slotList = vMap.getList(key, Tag.TAG_COMPOUND);
            ItemStack[] slots = new ItemStack[VAULT_SIZE];
            Arrays.fill(slots, ItemStack.EMPTY);
            for (Tag t : slotList) {
                CompoundTag s = (CompoundTag) t;
                int idx = s.getInt("slot");
                if (idx >= 0 && idx < VAULT_SIZE)
                    slots[idx] = ItemStack.of(s.getCompound("item"));
            }
            data.vaults.put(guildId, slots);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag vMap = new CompoundTag();
        for (Map.Entry<UUID, ItemStack[]> e : vaults.entrySet()) {
            ListTag slotList = new ListTag();
            ItemStack[] slots = e.getValue();
            for (int i = 0; i < slots.length; i++) {
                if (!slots[i].isEmpty()) {
                    CompoundTag s = new CompoundTag();
                    s.putInt("slot", i);
                    CompoundTag itemTag = new CompoundTag();
                    slots[i].save(itemTag);
                    s.put("item", itemTag);
                    slotList.add(s);
                }
            }
            vMap.put(e.getKey().toString(), slotList);
        }
        tag.put("vaults", vMap);
        return tag;
    }
}
