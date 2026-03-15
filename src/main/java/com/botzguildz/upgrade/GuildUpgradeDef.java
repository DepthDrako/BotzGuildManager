package com.botzguildz.upgrade;

import com.botzguildz.data.Guild;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Datapack-driven upgrade definition.
 *
 * Loaded from: data/&lt;namespace&gt;/guild_upgrades/upgrades/&lt;filename&gt;.json
 * The filename (without extension, uppercased) becomes the upgrade ID.
 *
 * JSON fields:
 * <pre>
 * {
 *   "category":       "COMBAT",
 *   "display_name":   "Combat Boost I",
 *   "description":    "+5% damage for all guild members.",
 *   "icon":           "minecraft:lime_dye",
 *   "cost":           500,
 *   "required_level": 3,
 *   "prerequisite":   null,
 *   "page":           1,
 *   "effects": [
 *     { "type": "modifier", "key": "DAMAGE_MULTIPLIER", "value": 0.05 }
 *   ]
 * }
 * </pre>
 *
 * The {@code page} field (1-based) controls which page of the GUI this upgrade appears on
 * within its category. If a category has more than 27 upgrades, use page 2, 3, etc.
 */
public class GuildUpgradeDef {

    private final String             id;
    private final String             categoryId;
    private final String             displayName;
    private final String             description;
    private final String             iconItemId;
    private final long               cost;
    private final int                requiredLevel;
    private final String             prerequisiteId; // null = no prereq
    private final int                page;           // 1-based
    private final List<UpgradeEffect> effects;

    public GuildUpgradeDef(String id, String categoryId, String displayName,
                           String description, String iconItemId,
                           long cost, int requiredLevel, String prerequisiteId,
                           int page, List<UpgradeEffect> effects) {
        this.id             = id;
        this.categoryId     = categoryId;
        this.displayName    = displayName;
        this.description    = description;
        this.iconItemId     = iconItemId;
        this.cost           = cost;
        this.requiredLevel  = requiredLevel;
        this.prerequisiteId = prerequisiteId;
        this.page           = Math.max(1, page);
        this.effects        = Collections.unmodifiableList(effects);
    }

    /**
     * @param id   derived from the JSON file's name (uppercased)
     * @param json the parsed JSON object
     */
    public static GuildUpgradeDef fromJson(String id, JsonObject json) {
        String catId    = json.get("category").getAsString();
        String dName    = json.get("display_name").getAsString();
        String desc     = json.has("description") ? json.get("description").getAsString() : "";
        String iconId   = json.has("icon") ? json.get("icon").getAsString() : "minecraft:paper";
        long   cost     = json.get("cost").getAsLong();
        int    reqLvl   = json.has("required_level") ? json.get("required_level").getAsInt() : 1;
        String prereq   = (json.has("prerequisite") && !json.get("prerequisite").isJsonNull())
                          ? json.get("prerequisite").getAsString() : null;
        int    page     = json.has("page") ? json.get("page").getAsInt() : 1;

        List<UpgradeEffect> effects = new ArrayList<>();
        if (json.has("effects") && json.get("effects").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("effects");
            for (JsonElement el : arr) {
                try { effects.add(UpgradeEffect.fromJson(el.getAsJsonObject())); }
                catch (Exception ignored) { /* skip malformed entries */ }
            }
        }

        return new GuildUpgradeDef(id, catId, dName, desc, iconId, cost, reqLvl, prereq, page, effects);
    }

    // ── Business logic ────────────────────────────────────────────────────────

    /**
     * Returns true if the guild can purchase this upgrade
     * (not already owned, level met, prerequisite met).
     */
    public boolean isAvailableTo(Guild guild) {
        if (guild.hasUpgrade(this.id)) return false;
        if (guild.getLevel() < requiredLevel) return false;
        if (prerequisiteId != null && !guild.hasUpgrade(prerequisiteId)) return false;
        return true;
    }

    /** Resolves the display icon item; falls back to PAPER if the ID is invalid. */
    public Item getIcon() {
        ResourceLocation rl = ResourceLocation.tryParse(iconItemId);
        if (rl == null) return Items.PAPER;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item != null ? item : Items.PAPER;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String              getId()             { return id; }
    public String              getCategoryId()     { return categoryId; }
    public String              getDisplayName()    { return displayName; }
    public String              getDescription()    { return description; }
    public long                getCost()           { return cost; }
    public int                 getRequiredLevel()  { return requiredLevel; }
    public String              getPrerequisiteId() { return prerequisiteId; }
    public int                 getPage()           { return page; }
    public List<UpgradeEffect> getEffects()        { return effects; }
}
