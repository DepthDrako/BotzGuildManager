package com.botzguildz.raid;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Holds the roster of a raid party.
 * All mutation is package-private; external code uses RaidManager.
 */
public class RaidParty {

    private final UUID partyId;
    private UUID leader;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

    public RaidParty(UUID leader) {
        this.partyId = UUID.randomUUID();
        this.leader  = leader;
        members.add(leader);
    }

    // ── read ──────────────────────────────────────────────────────────────

    public UUID getPartyId() { return partyId; }
    public UUID getLeader()  { return leader;  }

    /** Unmodifiable snapshot of the member set. */
    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public boolean isLeader(UUID uuid) { return leader.equals(uuid); }
    public int size()                  { return members.size(); }

    // ── mutate (package-private) ──────────────────────────────────────────

    void addMember(UUID uuid)    { members.add(uuid); }
    void removeMember(UUID uuid) { members.remove(uuid); }
    void setLeader(UUID uuid)    { leader = uuid; }
}
