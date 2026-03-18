package com.botzguildz.registry;

import com.botzguildz.BotzGuildz;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Tiered guild currency items.
 *
 * Each tier is worth 8× the tier below it:
 * <pre>
 *   Guild Bit    = 1
 *   Guild Chip   = 8
 *   Guild Token  = 64
 *   Guild Coin   = 512
 *   Guild Mark   = 4 096
 *   Guild Seal   = 32 768
 * </pre>
 *
 * Conversion crafting (shapeless):
 *   8× lower tier → 1× higher tier
 *   1× higher tier → 8× lower tier
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BotzGuildz.MODID);

    // ── Tier 1 ─── value: 1 ──────────────────────────────────────────────────
    public static final RegistryObject<Item> GUILD_BIT =
            ITEMS.register("guild_bit",
                    () -> new Item(new Item.Properties().stacksTo(64)));

    // ── Tier 2 ─── value: 8 ──────────────────────────────────────────────────
    public static final RegistryObject<Item> GUILD_CHIP =
            ITEMS.register("guild_chip",
                    () -> new Item(new Item.Properties().stacksTo(64)));

    // ── Tier 3 ─── value: 64 ─────────────────────────────────────────────────
    public static final RegistryObject<Item> GUILD_TOKEN =
            ITEMS.register("guild_token",
                    () -> new Item(new Item.Properties().stacksTo(64)));

    // ── Tier 4 ─── value: 512 ────────────────────────────────────────────────
    public static final RegistryObject<Item> GUILD_COIN =
            ITEMS.register("guild_coin",
                    () -> new Item(new Item.Properties().stacksTo(64)));

    // ── Tier 5 ─── value: 4 096 ──────────────────────────────────────────────
    public static final RegistryObject<Item> GUILD_MARK =
            ITEMS.register("guild_mark",
                    () -> new Item(new Item.Properties().stacksTo(64)));

    // ── Tier 6 ─── value: 32 768 ─────────────────────────────────────────────
    public static final RegistryObject<Item> GUILD_SEAL =
            ITEMS.register("guild_seal",
                    () -> new Item(new Item.Properties().stacksTo(64)));

    // ── Value table (index 0 = lowest) ───────────────────────────────────────

    /** Base values for each tier, ascending. */
    public static final long[] TIER_VALUES = { 1L, 8L, 64L, 512L, 4_096L, 32_768L };

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
