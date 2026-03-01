package com.botzguildz.upgrade;

import com.botzguildz.BotzGuildz;
import com.botzguildz.data.Guild;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side singleton that holds all datapack-loaded upgrade categories and upgrades.
 *
 * <h3>Data-pack layout</h3>
 * <pre>
 * data/&lt;namespace&gt;/guild_upgrades/categories/&lt;id&gt;.json
 * data/&lt;namespace&gt;/guild_upgrades/upgrades/&lt;id&gt;.json
 * </pre>
 * The filename (without extension, uppercased) is used as the definition ID.
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>Maximum 9 categories total</li>
 *   <li>Each category's slot must be in [0, 8] with no duplicates</li>
 *   <li>Every upgrade's {@code category} field must reference a known category ID</li>
 * </ul>
 * Any validation failure causes the registry to fall back to built-in demo data
 * (derived from the {@link GuildUpgrade} and {@link UpgradeCategory} enums) and
 * logs an error.
 *
 * <h3>Load ordering</h3>
 * {@link CategoryReloadListener} is registered first and stores raw category data.
 * {@link UpgradeReloadListener} is registered second and calls
 * {@link #acceptUpgradesAndFinalize} which parses both lists and validates.
 */
public class UpgradeRegistry {

    public static final UpgradeRegistry INSTANCE = new UpgradeRegistry();

    private static final Gson GSON = new GsonBuilder().create();

    private static final String CAT_FOLDER = "guild_upgrades/categories"; // used as the listener scan directory
    private static final String UPG_FOLDER = "guild_upgrades/upgrades";   // used as the listener scan directory

    // ── State ─────────────────────────────────────────────────────────────────

    private List<UpgradeCategoryDef>            categories   = List.of();
    private List<GuildUpgradeDef>               upgrades     = List.of();
    private Map<String, UpgradeCategoryDef>     categoryById = Map.of();
    private Map<String, GuildUpgradeDef>        upgradeById  = Map.of();

    // Temporary storage set by CategoryReloadListener, consumed by UpgradeReloadListener
    private Map<ResourceLocation, JsonElement>  pendingCategories = null;

    /** Validation errors from the most recent reload, shown in chat to operators. */
    private final List<String> lastErrors = new ArrayList<>();

    // ── Init ──────────────────────────────────────────────────────────────────

    private UpgradeRegistry() {
        loadDemoData(); // sensible defaults before the first datapack reload
    }

    // ── Two-phase load ────────────────────────────────────────────────────────

    synchronized void acceptCategories(Map<ResourceLocation, JsonElement> data) {
        this.pendingCategories = data;
        this.lastErrors.clear(); // reset errors at the start of each reload cycle
    }

    synchronized void acceptUpgradesAndFinalize(Map<ResourceLocation, JsonElement> upgradeData) {
        List<UpgradeCategoryDef> cats = new ArrayList<>();
        List<GuildUpgradeDef>    ups  = new ArrayList<>();

        // ── Parse categories ──────────────────────────────────────────────────
        // SimpleJsonResourceReloadListener already strips the directory prefix and
        // .json extension, so getPath() returns just the bare filename, e.g. "combat".
        if (pendingCategories != null) {
            for (Map.Entry<ResourceLocation, JsonElement> e : pendingCategories.entrySet()) {
                try {
                    String id = e.getKey().getPath().toUpperCase(); // "combat" → "COMBAT"
                    cats.add(UpgradeCategoryDef.fromJson(id, e.getValue().getAsJsonObject()));
                } catch (Exception ex) {
                    BotzGuildz.LOGGER.error("[BotzGuildz] Failed to parse category '{}': {}",
                            e.getKey(), ex.getMessage());
                }
            }
        }
        pendingCategories = null;

        // ── Parse upgrades ────────────────────────────────────────────────────
        for (Map.Entry<ResourceLocation, JsonElement> e : upgradeData.entrySet()) {
            try {
                String id = e.getKey().getPath().toUpperCase(); // "combat_damage_i" → "COMBAT_DAMAGE_I"
                ups.add(GuildUpgradeDef.fromJson(id, e.getValue().getAsJsonObject()));
            } catch (Exception ex) {
                BotzGuildz.LOGGER.error("[BotzGuildz] Failed to parse upgrade '{}': {}",
                        e.getKey(), ex.getMessage());
            }
        }

        // ── No datapack data found → use built-in demo ────────────────────────
        // If no JSON files were found at all (no datapacks define any upgrades),
        // fall back to the programmatic demo data rather than committing an empty registry.
        if (cats.isEmpty() && ups.isEmpty()) {
            BotzGuildz.LOGGER.info("[BotzGuildz] No upgrade datapacks found — using built-in demo data.");
            loadDemoData();
            return;
        }

        // ── Validate & commit ─────────────────────────────────────────────────
        if (!validate(cats, ups)) {
            BotzGuildz.LOGGER.error("[BotzGuildz] Upgrade data failed validation — loading demo data.");
            loadDemoData();
            return;
        }

        cats.sort(Comparator.comparingInt(UpgradeCategoryDef::getSlot));

        this.categories   = Collections.unmodifiableList(cats);
        this.upgrades     = Collections.unmodifiableList(ups);
        this.categoryById = cats.stream().collect(
                Collectors.toUnmodifiableMap(UpgradeCategoryDef::getId, c -> c));
        this.upgradeById  = ups.stream().collect(
                Collectors.toUnmodifiableMap(GuildUpgradeDef::getId, u -> u));

        BotzGuildz.LOGGER.info("[BotzGuildz] Loaded {} upgrade categories and {} upgrades from datapacks.",
                cats.size(), ups.size());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private boolean validate(List<UpgradeCategoryDef> cats, List<GuildUpgradeDef> ups) {
        if (cats.size() > 9) {
            recordError("Too many upgrade categories: " + cats.size()
                    + " defined, maximum is 9. Falling back to demo data.");
            return false;
        }

        Set<Integer> usedSlots = new HashSet<>();
        for (UpgradeCategoryDef cat : cats) {
            if (cat.getSlot() < 0 || cat.getSlot() > 8) {
                recordError("Category '" + cat.getId() + "' has slot " + cat.getSlot()
                        + " which is out of range. Slots are 0-indexed: use 0 (leftmost) through 8 (rightmost)"
                        + " — 9 slots total. Falling back to demo data.");
                return false;
            }
            if (!usedSlots.add(cat.getSlot())) {
                recordError("Duplicate category slot " + cat.getSlot()
                        + " — two categories share the same slot. Falling back to demo data.");
                return false;
            }
        }

        Set<String> catIds = cats.stream().map(UpgradeCategoryDef::getId).collect(Collectors.toSet());
        for (GuildUpgradeDef up : ups) {
            if (!catIds.contains(up.getCategoryId())) {
                recordError("Upgrade '" + up.getId() + "' references unknown category '"
                        + up.getCategoryId() + "'. Falling back to demo data.");
                return false;
            }
        }

        return true;
    }

    /** Logs an error and stores it so it can be shown in-game to operators. */
    private void recordError(String message) {
        BotzGuildz.LOGGER.error("[BotzGuildz] {}", message);
        lastErrors.add(message);
    }

    // ── Demo fallback (built from existing enums) ─────────────────────────────

    private void loadDemoData() {
        UpgradeCategory[] enumCats  = UpgradeCategory.values();
        int[]    demoSlots  = {2, 3, 4, 5, 6};
        String[] demoColors = {"RED", "GREEN", "GOLD", "BLUE", "LIGHT_PURPLE"};
        String[] demoItems  = {
            "minecraft:iron_sword", "minecraft:feather",
            "minecraft:gold_ingot", "minecraft:shield", "minecraft:blaze_powder"
        };

        List<UpgradeCategoryDef> cats = new ArrayList<>();
        for (int i = 0; i < enumCats.length; i++) {
            UpgradeCategory ec = enumCats[i];
            cats.add(new UpgradeCategoryDef(
                    ec.name(), ec.getDisplayName(), demoItems[i], demoColors[i], demoSlots[i]));
        }

        // Track slot index per category for page calculation
        Map<String, Integer> slotCount = new HashMap<>();
        List<GuildUpgradeDef> ups = new ArrayList<>();

        for (GuildUpgrade gu : GuildUpgrade.values()) {
            String catId = gu.getCategory().name();
            int    slot  = slotCount.getOrDefault(catId, 0);
            int    page  = (slot / 27) + 1;
            slotCount.put(catId, slot + 1);

            // Icons matching the old GUI's available-state icon
            String icon = switch (catId) {
                case "COMBAT"  -> "minecraft:lime_dye";
                case "UTILITY" -> "minecraft:feather";
                case "ECONOMY" -> "minecraft:emerald";
                case "DEFENSE" -> "minecraft:shield";
                case "ARCANE"  -> "minecraft:blaze_powder";
                default        -> "minecraft:paper";
            };

            ups.add(new GuildUpgradeDef(
                    gu.name(), catId, gu.getDisplayName(), gu.getDescription(),
                    icon, gu.getCost(), gu.getRequiredLevel(), gu.getPrerequisiteId(),
                    page, List.of()));
        }

        this.categories   = Collections.unmodifiableList(cats);
        this.upgrades     = Collections.unmodifiableList(ups);
        this.categoryById = cats.stream().collect(
                Collectors.toUnmodifiableMap(UpgradeCategoryDef::getId, c -> c));
        this.upgradeById  = ups.stream().collect(
                Collectors.toUnmodifiableMap(GuildUpgradeDef::getId, u -> u));

        BotzGuildz.LOGGER.info("[BotzGuildz] Demo upgrade data loaded ({} categories, {} upgrades).",
                cats.size(), ups.size());
    }

    // ── Public query API ──────────────────────────────────────────────────────

    /** All categories sorted by slot. */
    public List<UpgradeCategoryDef> getCategories() { return categories; }

    /** All upgrades (all categories, all pages). */
    public List<GuildUpgradeDef> getAllUpgrades() { return upgrades; }

    public UpgradeCategoryDef getCategoryById(String id) {
        return categoryById.get(id != null ? id : "");
    }

    public GuildUpgradeDef getUpgradeById(String id) {
        return upgradeById.get(id != null ? id : "");
    }

    /**
     * Returns upgrades for a specific category and page (1-based).
     * Results are in the order they were loaded (stable — determined by datapack order).
     */
    public List<GuildUpgradeDef> getUpgradesForCategory(String catId, int page) {
        return upgrades.stream()
                .filter(u -> u.getCategoryId().equals(catId) && u.getPage() == page)
                .collect(Collectors.toList());
    }

    /** Total number of pages for a category; minimum 1. */
    public int getPageCount(String catId) {
        return upgrades.stream()
                .filter(u -> u.getCategoryId().equals(catId))
                .mapToInt(GuildUpgradeDef::getPage)
                .max().orElse(1);
    }

    /**
     * Returns the ID of the first category (by slot), or {@code null} if no categories are loaded.
     * Used as the default tab when opening the GUI without a specific category argument.
     */
    public String getDefaultCategoryId() {
        return categories.isEmpty() ? null : categories.get(0).getId();
    }

    /**
     * Sums the values of all MODIFIER effects with the given key across all upgrades
     * that the guild currently owns.
     *
     * <p>Example: {@code sumModifier(guild, "DAMAGE_MULTIPLIER")} returns 0.10 if both
     * COMBAT_DAMAGE_I (+0.05) and COMBAT_DAMAGE_II (+0.05) are owned.</p>
     */
    public double sumModifier(Guild guild, String key) {
        double sum = 0;
        for (GuildUpgradeDef up : upgrades) {
            if (!guild.hasUpgrade(up.getId())) continue;
            for (UpgradeEffect eff : up.getEffects()) {
                if (eff.getType() == UpgradeEffect.Type.MODIFIER && key.equals(eff.getKey())) {
                    sum += eff.getValue();
                }
            }
        }
        return sum;
    }

    /**
     * Returns true if any upgrade the guild owns has a MODIFIER effect with the given key.
     */
    public boolean hasModifier(Guild guild, String key) {
        for (GuildUpgradeDef up : upgrades) {
            if (!guild.hasUpgrade(up.getId())) continue;
            for (UpgradeEffect eff : up.getEffects()) {
                if (eff.getType() == UpgradeEffect.Type.MODIFIER && key.equals(eff.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── Error reporting ───────────────────────────────────────────────────────

    /** Returns validation errors from the most recent reload (empty list = no errors). */
    public List<String> getErrors() { return Collections.unmodifiableList(lastErrors); }

    // ── Reload listener registration ──────────────────────────────────────────

    /**
     * Called on {@link AddReloadListenerEvent}.
     * Register category listener BEFORE upgrade listener so categories are parsed first.
     */
    public static void registerListeners(AddReloadListenerEvent event) {
        event.addListener(new CategoryReloadListener());
        event.addListener(new UpgradeReloadListener());
    }

    // ── Inner listener classes ────────────────────────────────────────────────

    public static class CategoryReloadListener extends SimpleJsonResourceReloadListener {
        public CategoryReloadListener() { super(GSON, CAT_FOLDER); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> data,
                             ResourceManager mgr, ProfilerFiller profiler) {
            INSTANCE.acceptCategories(data);
        }
    }

    public static class UpgradeReloadListener extends SimpleJsonResourceReloadListener {
        public UpgradeReloadListener() { super(GSON, UPG_FOLDER); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> data,
                             ResourceManager mgr, ProfilerFiller profiler) {
            INSTANCE.acceptUpgradesAndFinalize(data);
        }
    }
}
