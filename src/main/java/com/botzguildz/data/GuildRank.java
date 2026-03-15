package com.botzguildz.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.EnumSet;
import java.util.Set;

/**
 * A rank inside a guild. Guilds ship with 4 defaults but leaders can create custom ones.
 * Higher priority = more authority. Leader is always priority Integer.MAX_VALUE.
 */
public class GuildRank {

    private String name;
    private int priority;           // Higher = more authority
    private boolean isDefault;      // Auto-assigned to new members if true
    private Set<RankPermission> permissions;

    public GuildRank(String name, int priority, boolean isDefault, Set<RankPermission> permissions) {
        this.name = name;
        this.priority = priority;
        this.isDefault = isDefault;
        this.permissions = EnumSet.copyOf(permissions.isEmpty() ? EnumSet.noneOf(RankPermission.class) : permissions);
    }

    // ── Defaults ──────────────────────────────────────────────────────────────

    public static GuildRank leader() {
        return new GuildRank("Leader", Integer.MAX_VALUE, false,
                EnumSet.allOf(RankPermission.class));
    }

    public static GuildRank officer() {
        return new GuildRank("Officer", 300, false,
                EnumSet.of(RankPermission.INVITE, RankPermission.KICK,
                        RankPermission.MANAGE_BANK, RankPermission.DECLARE_WAR,
                        RankPermission.MANAGE_UPGRADES, RankPermission.SET_HOME,
                        RankPermission.MANAGE_ALLIES));
    }

    public static GuildRank member() {
        return new GuildRank("Member", 200, true,
                EnumSet.of(RankPermission.MANAGE_BANK));
    }

    public static GuildRank recruit() {
        return new GuildRank("Recruit", 100, false,
                EnumSet.noneOf(RankPermission.class));
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putInt("priority", priority);
        tag.putBoolean("isDefault", isDefault);
        ListTag perms = new ListTag();
        for (RankPermission perm : permissions) {
            perms.add(StringTag.valueOf(perm.name()));
        }
        tag.put("permissions", perms);
        return tag;
    }

    public static GuildRank fromNBT(CompoundTag tag) {
        String name = tag.getString("name");
        int priority = tag.getInt("priority");
        boolean isDefault = tag.getBoolean("isDefault");
        Set<RankPermission> perms = EnumSet.noneOf(RankPermission.class);
        ListTag permList = tag.getList("permissions", Tag.TAG_STRING);
        for (Tag t : permList) {
            try { perms.add(RankPermission.valueOf(t.getAsString())); } catch (IllegalArgumentException ignored) {}
        }
        return new GuildRank(name, priority, isDefault, perms);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public Set<RankPermission> getPermissions() { return permissions; }
    public boolean hasPermission(RankPermission perm) { return permissions.contains(perm); }
    public void addPermission(RankPermission perm) { permissions.add(perm); }
    public void removePermission(RankPermission perm) { permissions.remove(perm); }
}
