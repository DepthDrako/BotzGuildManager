package com.botzguildz.data;

public enum WarState {
    PENDING,   // Declared but not yet accepted by the challenged guild
    ACTIVE,    // War is ongoing
    ENDED      // War is over (held briefly for cleanup/logging before removal)
}
