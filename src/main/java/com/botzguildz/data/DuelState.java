package com.botzguildz.data;

public enum DuelState {
    PENDING,  // Challenge sent, waiting for response
    ACTIVE,   // Duel in progress
    ENDED     // Duel finished (win, forfeit, or draw)
}
