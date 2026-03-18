package com.botzguildz.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Lazy-initialised, server-safe cache of all registered items grouped by mod namespace.
 *
 * Unlike creative tabs (which may not be built server-side), this iterates
 * {@link ForgeRegistries#ITEMS} directly — guaranteed available after the registry
 * freeze phase during normal mod loading.
 *
 * Call {@link #get()} from GUI code; the first call initialises the cache.
 */
public class ItemPickerCache {

    private static ItemPickerCache INSTANCE = null;

    /** Ordered list of namespace strings. "minecraft" is always first. */
    private final List<String> namespaces;
    /** namespace → ordered item stacks (count = 1, no NBT). */
    private final Map<String, List<ItemStack>> itemsByNamespace;

    private ItemPickerCache() {
        Map<String, List<ItemStack>> raw = new LinkedHashMap<>();

        ForgeRegistries.ITEMS.getEntries().forEach(entry -> {
            if (entry.getValue() == Items.AIR) return;
            String ns = entry.getKey().location().getNamespace();
            raw.computeIfAbsent(ns, k -> new ArrayList<>())
               .add(new ItemStack(entry.getValue()));
        });

        // Sort: minecraft first, remaining by descending item count
        List<String> sorted = new ArrayList<>(raw.keySet());
        sorted.remove("minecraft");
        sorted.sort((a, b) -> Integer.compare(raw.get(b).size(), raw.get(a).size()));
        if (raw.containsKey("minecraft")) sorted.add(0, "minecraft");

        // Remove empty / forge-internal namespaces that add no items
        sorted.removeIf(ns -> raw.getOrDefault(ns, Collections.emptyList()).isEmpty());

        this.namespaces       = Collections.unmodifiableList(sorted);
        this.itemsByNamespace = Collections.unmodifiableMap(raw);
    }

    /** Obtain the shared instance, initialising on first call. */
    public static ItemPickerCache get() {
        if (INSTANCE == null) INSTANCE = new ItemPickerCache();
        return INSTANCE;
    }

    /** Force a rebuild (e.g., after dynamic item registration in tests). Not normally needed. */
    public static void invalidate() { INSTANCE = null; }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<String> getNamespaces() { return namespaces; }

    public List<ItemStack> getItemsForNamespace(String namespace) {
        return itemsByNamespace.getOrDefault(namespace, Collections.emptyList());
    }

    /**
     * Returns all items (across all namespaces) whose display name or registry ID
     * contains {@code query} (case-insensitive).
     */
    public List<ItemStack> search(String query) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        String lower = query.toLowerCase(Locale.ROOT);
        return itemsByNamespace.values().stream()
                .flatMap(Collection::stream)
                .filter(stack -> {
                    String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                    String id   = Objects.toString(
                            ForgeRegistries.ITEMS.getKey(stack.getItem()), "").toLowerCase(Locale.ROOT);
                    return name.contains(lower) || id.contains(lower);
                })
                .collect(Collectors.toList());
    }

    /** Human-friendly tab label for a namespace string. */
    public static String displayName(String namespace) {
        // Known special cases
        return switch (namespace) {
            case "minecraft" -> "Minecraft";
            case "create"    -> "Create";
            case "forge"     -> "Forge";
            default -> {
                // Convert snake_case to Title Case
                String[] parts = namespace.split("[_\\-]");
                StringBuilder sb = new StringBuilder();
                for (String p : parts) {
                    if (p.isEmpty()) continue;
                    sb.append(Character.toUpperCase(p.charAt(0)));
                    if (p.length() > 1) sb.append(p.substring(1));
                    sb.append(' ');
                }
                yield sb.toString().trim();
            }
        };
    }

    /** Returns an ItemStack representative of a namespace (first item in the list). */
    public ItemStack representativeItem(String namespace) {
        List<ItemStack> items = getItemsForNamespace(namespace);
        return items.isEmpty() ? new ItemStack(Items.BOOK) : items.get(0);
    }
}
