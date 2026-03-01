package com.botzguildz.command;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight in-memory tracker for pending guild invite and join handshakes.
 * Invites expire after 60 seconds (checked on /guild join).
 */
public class InviteTracker {

    private static final Map<UUID, UUID>  invites    = new HashMap<>(); // playerUUID -> guildId
    private static final Map<UUID, Long>  inviteTimes = new HashMap<>();
    private static final long             TTL_MS      = 60_000L;

    public static void addInvite(UUID playerUUID, UUID guildId) {
        invites.put(playerUUID, guildId);
        inviteTimes.put(playerUUID, System.currentTimeMillis());
    }

    public static UUID getInvitedGuild(UUID playerUUID) {
        Long time = inviteTimes.get(playerUUID);
        if (time == null) return null;
        if (System.currentTimeMillis() - time > TTL_MS) {
            invites.remove(playerUUID);
            inviteTimes.remove(playerUUID);
            return null;
        }
        return invites.get(playerUUID);
    }

    public static void clearInvite(UUID playerUUID) {
        invites.remove(playerUUID);
        inviteTimes.remove(playerUUID);
    }
}
