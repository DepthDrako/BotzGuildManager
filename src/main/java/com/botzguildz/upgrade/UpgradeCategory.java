package com.botzguildz.upgrade;

public enum UpgradeCategory {
    COMBAT("⚔ Combat"),
    UTILITY("🏃 Utility"),
    ECONOMY("💰 Economy"),
    DEFENSE("🛡 Defense"),
    ARCANE("🌀 Arcane");

    private final String displayName;
    UpgradeCategory(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
