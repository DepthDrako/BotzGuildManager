package com.botzguildz.upgrade;

import com.google.gson.JsonObject;

/**
 * Represents one effect entry inside a {@link GuildUpgradeDef}.
 *
 * Types:
 *   MODIFIER      — persistent runtime modifier queried via UpgradeRegistry.sumModifier()
 *   POTION_EFFECT — ongoing potion granted on login (applied by GuildEventHandler)
 *   GIVE_ITEM     — one-time item grant to the purchasing player on purchase
 *   COMMAND       — one-time command run on purchase
 */
public class UpgradeEffect {

    public enum Type { MODIFIER, POTION_EFFECT, GIVE_ITEM, COMMAND }

    private final Type   type;

    // MODIFIER
    private final String key;
    private final double value;

    // POTION_EFFECT
    private final String effectId;
    private final int    amplifier;
    private final int    durationTicks;

    // GIVE_ITEM
    private final String itemId;
    private final int    count;

    // COMMAND
    private final String command;
    private final String target; // "purchaser" | "all_members" | "console"

    private UpgradeEffect(Builder b) {
        this.type          = b.type;
        this.key           = b.key;
        this.value         = b.value;
        this.effectId      = b.effectId;
        this.amplifier     = b.amplifier;
        this.durationTicks = b.durationTicks;
        this.itemId        = b.itemId;
        this.count         = b.count;
        this.command       = b.command;
        this.target        = b.target;
    }

    /**
     * Parse a single effect JSON object.
     * <pre>
     * { "type": "modifier",      "key": "DAMAGE_MULTIPLIER", "value": 0.05 }
     * { "type": "potion_effect", "effect": "minecraft:resistance", "amplifier": 0, "duration_ticks": 2400 }
     * { "type": "give_item",     "item": "minecraft:diamond", "count": 3 }
     * { "type": "command",       "command": "say %player% bought!", "target": "console" }
     * </pre>
     */
    public static UpgradeEffect fromJson(JsonObject json) {
        Type type = Type.valueOf(json.get("type").getAsString().toUpperCase());
        Builder b = new Builder(type);
        switch (type) {
            case MODIFIER -> {
                b.key   = json.get("key").getAsString();
                b.value = json.get("value").getAsDouble();
            }
            case POTION_EFFECT -> {
                b.effectId      = json.get("effect").getAsString();
                b.amplifier     = json.has("amplifier")     ? json.get("amplifier").getAsInt()     : 0;
                b.durationTicks = json.has("duration_ticks") ? json.get("duration_ticks").getAsInt() : 2400;
            }
            case GIVE_ITEM -> {
                b.itemId = json.get("item").getAsString();
                b.count  = json.has("count") ? json.get("count").getAsInt() : 1;
            }
            case COMMAND -> {
                b.command = json.get("command").getAsString();
                b.target  = json.has("target") ? json.get("target").getAsString() : "purchaser";
            }
        }
        return new UpgradeEffect(b);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Type   getType()          { return type; }
    public String getKey()           { return key; }
    public double getValue()         { return value; }
    public String getEffectId()      { return effectId; }
    public int    getAmplifier()     { return amplifier; }
    public int    getDurationTicks() { return durationTicks; }
    public String getItemId()        { return itemId; }
    public int    getCount()         { return count; }
    public String getCommand()       { return command; }
    public String getTarget()        { return target; }

    // ── Builder ───────────────────────────────────────────────────────────────

    private static class Builder {
        final Type type;
        String key;   double value;
        String effectId; int amplifier; int durationTicks;
        String itemId;   int count = 1;
        String command;  String target = "purchaser";

        Builder(Type t) { this.type = t; }
    }
}
