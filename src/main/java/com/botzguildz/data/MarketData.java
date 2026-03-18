package com.botzguildz.data;

import com.botzguildz.market.GuildShop;
import com.botzguildz.market.PlayerShop;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;

public class MarketData extends SavedData {

    private static final String DATA_NAME = "botzguildz_market";
    private final Map<UUID, GuildShop>        guildShops  = new LinkedHashMap<>();
    /** ownerUUID → all shops owned by that player (multiple physical chests allowed) */
    private final Map<UUID, List<PlayerShop>> playerShops = new LinkedHashMap<>();

    public static MarketData get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
                tag -> load(tag),
                MarketData::new,
                DATA_NAME
        );
    }

    // ---- Guild shops ----

    public GuildShop getOrCreateGuildShop(UUID guildId, String guildName) {
        return guildShops.computeIfAbsent(guildId, k -> new GuildShop(guildId, guildName));
    }

    public Optional<GuildShop> getGuildShop(UUID guildId) {
        return Optional.ofNullable(guildShops.get(guildId));
    }

    public Collection<GuildShop> getAllGuildShops() { return guildShops.values(); }

    // ---- Player shops ----

    /** All shops belonging to a single player (may be empty if none yet). */
    public List<PlayerShop> getPlayerShops(UUID ownerUUID) {
        return playerShops.getOrDefault(ownerUUID, List.of());
    }

    /**
     * Create a brand-new shop for a player and register it.
     * Does NOT check for duplicates — call {@link #getOrCreateShopAt} if you want
     * to reuse an existing shop at a given block position.
     */
    public PlayerShop createPlayerShop(UUID ownerUUID, String ownerName) {
        PlayerShop shop = new PlayerShop(ownerUUID, ownerName);
        playerShops.computeIfAbsent(ownerUUID, k -> new ArrayList<>()).add(shop);
        return shop;
    }

    /**
     * If the player already has a shop registered at {@code pos} in {@code dimId},
     * return it; otherwise create a new shop (linked to that block) and return it.
     */
    public PlayerShop getOrCreateShopAt(UUID ownerUUID, String ownerName,
                                        BlockPos pos, String dimId) {
        for (PlayerShop s : getPlayerShops(ownerUUID)) {
            if (pos.equals(s.getShopBlockPos()) && dimId.equals(s.getShopDimensionId()))
                return s;
        }
        PlayerShop shop = createPlayerShop(ownerUUID, ownerName);
        shop.setShopBlock(pos, dimId);
        return shop;
    }

    /** Find any player's shop by its unique shopId. */
    public Optional<PlayerShop> getShopById(UUID shopId) {
        for (List<PlayerShop> list : playerShops.values())
            for (PlayerShop s : list)
                if (s.getShopId().equals(shopId)) return Optional.of(s);
        return Optional.empty();
    }

    /** Flat view of every player shop across all owners. */
    public Collection<PlayerShop> getAllPlayerShops() {
        List<PlayerShop> all = new ArrayList<>();
        for (List<PlayerShop> list : playerShops.values()) all.addAll(list);
        return all;
    }

    /** Find a player shop registered at a specific block position in a given dimension. */
    public Optional<PlayerShop> getPlayerShopAtBlock(BlockPos pos, String dimensionId) {
        for (List<PlayerShop> list : playerShops.values())
            for (PlayerShop s : list)
                if (pos.equals(s.getShopBlockPos()) && dimensionId.equals(s.getShopDimensionId()))
                    return Optional.of(s);
        return Optional.empty();
    }

    /**
     * Completely remove a player shop (by its unique shopId) from storage.
     * @return true if a shop was found and removed
     */
    public boolean removeShopById(UUID shopId) {
        for (Map.Entry<UUID, List<PlayerShop>> entry : playerShops.entrySet()) {
            if (entry.getValue().removeIf(s -> s.getShopId().equals(shopId))) {
                setDirty();
                return true;
            }
        }
        return false;
    }

    /** Find a guild shop registered at a specific block position in a given dimension */
    public Optional<GuildShop> getGuildShopAtBlock(BlockPos pos, String dimensionId) {
        for (GuildShop shop : guildShops.values()) {
            if (pos.equals(shop.getShopBlockPos()) && dimensionId.equals(shop.getShopDimensionId()))
                return Optional.of(shop);
        }
        return Optional.empty();
    }

    public static MarketData load(CompoundTag tag) {
        MarketData data = new MarketData();
        ListTag gList = tag.getList("guildShops", Tag.TAG_COMPOUND);
        for (int i = 0; i < gList.size(); i++) {
            GuildShop shop = GuildShop.fromNBT(gList.getCompound(i));
            data.guildShops.put(shop.getGuildId(), shop);
        }
        ListTag pList = tag.getList("playerShops", Tag.TAG_COMPOUND);
        for (int i = 0; i < pList.size(); i++) {
            PlayerShop shop = PlayerShop.fromNBT(pList.getCompound(i));
            data.playerShops.computeIfAbsent(shop.getOwnerUUID(), k -> new ArrayList<>()).add(shop);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag gList = new ListTag();
        for (GuildShop shop : guildShops.values()) gList.add(shop.toNBT());
        tag.put("guildShops", gList);
        ListTag pList = new ListTag();
        for (List<PlayerShop> list : playerShops.values())
            for (PlayerShop shop : list) pList.add(shop.toNBT());
        tag.put("playerShops", pList);
        return tag;
    }
}
