package com.botzguildz.upgrade;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Datapack-driven upgrade category definition.
 *
 * Loaded from: data/&lt;namespace&gt;/guild_upgrades/categories/&lt;filename&gt;.json
 * The filename (without extension, uppercased) becomes the category ID.
 *
 * JSON fields:
 * <pre>
 * {
 *   "name":  "⚔ Combat",
 *   "item":  "minecraft:iron_sword",
 *   "color": "RED",
 *   "slot":  2
 * }
 * </pre>
 *
 * Constraints (validated by UpgradeRegistry):
 *  - Maximum 9 categories total
 *  - slot must be in range [0, 8] (row 0 of the chest GUI)
 *  - No two categories may share the same slot
 */
public class UpgradeCategoryDef {

    private final String id;
    private final String name;
    private final String itemId;
    private final String color;
    private final int    slot;

    public UpgradeCategoryDef(String id, String name, String itemId, String color, int slot) {
        this.id     = id;
        this.name   = name;
        this.itemId = itemId;
        this.color  = color;
        this.slot   = slot;
    }

    /**
     * @param id   derived from the JSON file's name (uppercased, e.g. "COMBAT")
     * @param json the parsed JSON object
     */
    public static UpgradeCategoryDef fromJson(String id, JsonObject json) {
        String name   = json.get("name").getAsString();
        String itemId = json.get("item").getAsString();
        String color  = json.has("color") ? json.get("color").getAsString() : "WHITE";
        int    slot   = json.get("slot").getAsInt();
        return new UpgradeCategoryDef(id, name, itemId, color, slot);
    }

    /** Resolves the category's display item; falls back to BARRIER if the ID is invalid. */
    public Item getItem() {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return Items.BARRIER;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item != null ? item : Items.BARRIER;
    }

    /** Resolves the category's name colour; falls back to WHITE if the name is invalid. */
    public ChatFormatting getColor() {
        ChatFormatting fmt = ChatFormatting.getByName(color.toLowerCase());
        return fmt != null ? fmt : ChatFormatting.WHITE;
    }

    public String getId()   { return id; }
    public String getName() { return name; }
    public int    getSlot() { return slot; }
}
