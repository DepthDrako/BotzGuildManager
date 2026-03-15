package com.botzguildz.raid;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Central state for the raid system.
 * All methods are synchronous and must only be called from the server thread.
 */
public class RaidManager {

    public static final RaidManager INSTANCE = new RaidManager();

    private RaidManager() {}

    // ── party state ───────────────────────────────────────────────────────

    /** leader UUID → party */
    private final Map<UUID, RaidParty> parties = new HashMap<>();
    /** member UUID → leader UUID (every member including leader) */
    private final Map<UUID, UUID> memberToLeader = new HashMap<>();

    // ── invite state ──────────────────────────────────────────────────────

    /** invitee UUID → leader UUID */
    private final Map<UUID, UUID> inviteLeader = new HashMap<>();
    /** invitee UUID → expiry timestamp (ms) */
    private final Map<UUID, Long> inviteExpiry = new HashMap<>();

    // ── fight state ───────────────────────────────────────────────────────

    /** boss entity UUID → active fight */
    private final Map<UUID, RaidBossFight> fightByBoss = new HashMap<>();
    /** leader UUID → boss entity UUID (so we can look up the party's fight) */
    private final Map<UUID, UUID> leaderToBoss = new HashMap<>();

    // ── result enum ───────────────────────────────────────────────────────

    public enum Result {
        CREATE_OK,
        ALREADY_IN_PARTY,
        INVITE_SENT,
        NOT_IN_PARTY,
        NOT_LEADER,
        ALREADY_INVITED,
        TARGET_IN_PARTY,
        INVITE_EXPIRED,
        NO_INVITE,
        ACCEPT_OK,
        DECLINE_OK,
        LEAVE_OK,
        DISBAND_OK,
        KICK_OK,
        TARGET_NOT_IN_PARTY,
        PARTY_BUSY
    }

    // ── party commands ────────────────────────────────────────────────────

    public Result createParty(UUID leader) {
        if (memberToLeader.containsKey(leader)) return Result.ALREADY_IN_PARTY;
        RaidParty party = new RaidParty(leader);
        parties.put(leader, party);
        memberToLeader.put(leader, leader);
        return Result.CREATE_OK;
    }

    public Result invite(UUID leader, UUID target) {
        if (!memberToLeader.containsKey(leader))           return Result.NOT_IN_PARTY;
        UUID myLeader = memberToLeader.get(leader);
        if (!myLeader.equals(leader))                      return Result.NOT_LEADER;
        if (memberToLeader.containsKey(target))            return Result.TARGET_IN_PARTY;
        if (isInvitePending(target))                       return Result.ALREADY_INVITED;
        inviteLeader.put(target, leader);
        inviteExpiry.put(target, System.currentTimeMillis() + 60_000L);
        return Result.INVITE_SENT;
    }

    public Result accept(UUID invitee) {
        if (!inviteLeader.containsKey(invitee))            return Result.NO_INVITE;
        if (!isInviteValid(invitee)) {
            clearInvite(invitee);
            return Result.INVITE_EXPIRED;
        }
        if (memberToLeader.containsKey(invitee)) {
            clearInvite(invitee);
            return Result.ALREADY_IN_PARTY;
        }
        UUID leader = inviteLeader.get(invitee);
        RaidParty party = parties.get(leader);
        if (party == null) {
            clearInvite(invitee);
            return Result.NO_INVITE;  // party was disbanded
        }
        party.addMember(invitee);
        memberToLeader.put(invitee, leader);
        clearInvite(invitee);
        return Result.ACCEPT_OK;
    }

    public Result decline(UUID invitee) {
        if (!inviteLeader.containsKey(invitee)) return Result.NO_INVITE;
        clearInvite(invitee);
        return Result.DECLINE_OK;
    }

    /**
     * A member leaves. If the leader leaves the whole party is disbanded.
     * {@code server} is used to send notifications to remaining members.
     */
    public Result leave(UUID member, MinecraftServer server) {
        if (!memberToLeader.containsKey(member)) return Result.NOT_IN_PARTY;
        UUID leader = memberToLeader.get(member);
        if (leader.equals(member)) {
            // leader leaving = disband
            return disbandParty(leader, server);
        }
        RaidParty party = parties.get(leader);
        party.removeMember(member);
        memberToLeader.remove(member);
        // notify remaining members
        notifyParty(party, member, "left the raid party.", server);
        return Result.LEAVE_OK;
    }

    public Result disband(UUID leader, MinecraftServer server) {
        if (!memberToLeader.containsKey(leader))    return Result.NOT_IN_PARTY;
        if (!memberToLeader.get(leader).equals(leader)) return Result.NOT_LEADER;
        return disbandParty(leader, server);
    }

    public Result kick(UUID leader, UUID target, MinecraftServer server) {
        if (!memberToLeader.containsKey(leader))          return Result.NOT_IN_PARTY;
        if (!memberToLeader.get(leader).equals(leader))   return Result.NOT_LEADER;
        RaidParty party = parties.get(leader);
        if (!party.isMember(target) || target.equals(leader)) return Result.TARGET_NOT_IN_PARTY;
        party.removeMember(target);
        memberToLeader.remove(target);
        notifyParty(party, target, "was kicked from the raid party.", server);
        return Result.KICK_OK;
    }

    // ── queries ───────────────────────────────────────────────────────────

    public Optional<RaidParty> getPartyOf(UUID member) {
        UUID leader = memberToLeader.get(member);
        if (leader == null) return Optional.empty();
        return Optional.ofNullable(parties.get(leader));
    }

    public Optional<RaidBossFight> getFight(UUID bossId) {
        return Optional.ofNullable(fightByBoss.get(bossId));
    }

    /**
     * Returns the pending invite's leader UUID, or empty if none / expired.
     */
    public Optional<UUID> getPendingInviteLeader(UUID invitee) {
        if (!isInviteValid(invitee)) {
            if (inviteLeader.containsKey(invitee)) clearInvite(invitee);
            return Optional.empty();
        }
        return Optional.ofNullable(inviteLeader.get(invitee));
    }

    // ── fight management ──────────────────────────────────────────────────

    /**
     * Called when a party member first hits a qualifying boss.
     * If the boss is already claimed by another party this returns empty.
     * If this party already has an active fight with a different boss this also returns empty.
     * Otherwise a new fight is created, stored and returned.
     */
    public Optional<RaidBossFight> tryClaimBoss(RaidParty party, LivingEntity boss) {
        UUID bossId   = boss.getUUID();
        UUID leaderId = party.getLeader();

        // Already claimed by someone else?
        if (fightByBoss.containsKey(bossId)) {
            RaidBossFight existing = fightByBoss.get(bossId);
            if (existing.getPartyId().equals(party.getPartyId())) {
                return Optional.of(existing); // same party — just return it
            }
            return Optional.empty(); // different party owns this boss
        }

        // This party is already fighting a different boss?
        if (leaderToBoss.containsKey(leaderId)) return Optional.empty();

        // Create the fight
        RaidBossFight fight = new RaidBossFight(
                party.getPartyId(),
                bossId,
                ForgeRegistries.ENTITY_TYPES.getKey(boss.getType()),
                boss.getName().getString()
        );
        fightByBoss.put(bossId, fight);
        leaderToBoss.put(leaderId, bossId);
        return Optional.of(fight);
    }

    /** Add damage to an ongoing fight (no-op if the fight no longer exists). */
    public void recordDamage(UUID bossId, UUID player, float amount) {
        RaidBossFight fight = fightByBoss.get(bossId);
        if (fight != null) fight.recordDamage(player, amount);
    }

    /**
     * Returns the active {@link RaidBossFight} for the given party, if one exists.
     * Used by /raid party status.
     */
    public Optional<RaidBossFight> getActiveFightForParty(RaidParty party) {
        UUID bossId = leaderToBoss.get(party.getLeader());
        if (bossId == null) return Optional.empty();
        return Optional.ofNullable(fightByBoss.get(bossId));
    }

    /** Remove the fight from both maps once the boss has died. */
    public void closeFight(UUID bossId) {
        RaidBossFight fight = fightByBoss.remove(bossId);
        if (fight == null) return;
        // find the leader who owns this fight and unlink
        leaderToBoss.entrySet().removeIf(e -> e.getValue().equals(bossId));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Result disbandParty(UUID leader, MinecraftServer server) {
        RaidParty party = parties.get(leader);
        if (party == null) return Result.NOT_IN_PARTY;
        // Close any active fight for this party
        UUID bossId = leaderToBoss.remove(leader);
        if (bossId != null) fightByBoss.remove(bossId);
        // Remove all members from the index
        for (UUID m : party.getMembers()) memberToLeader.remove(m);
        parties.remove(leader);
        // Notify (leader excluded from "disbanded" message — they know)
        for (UUID m : party.getMembers()) {
            if (m.equals(leader)) continue;
            sendMessage(m, "The raid party was disbanded by the leader.", server);
        }
        return Result.DISBAND_OK;
    }

    /** Send a notification to every current member except the source player. */
    private void notifyParty(RaidParty party, UUID source, String msg, MinecraftServer server) {
        String sourceName = getPlayerName(source, server);
        for (UUID m : party.getMembers()) {
            if (m.equals(source)) continue;
            sendMessage(m, sourceName + " " + msg, server);
        }
    }

    /** Send a plain-text server message to a player by UUID (if online). */
    public void sendMessage(UUID playerId, String text, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(text));
        }
    }

    private String getPlayerName(UUID playerId, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player != null ? player.getGameProfile().getName() : playerId.toString().substring(0, 8);
    }

    private boolean isInvitePending(UUID invitee) {
        return inviteLeader.containsKey(invitee) && isInviteValid(invitee);
    }

    private boolean isInviteValid(UUID invitee) {
        Long expiry = inviteExpiry.get(invitee);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    private void clearInvite(UUID invitee) {
        inviteLeader.remove(invitee);
        inviteExpiry.remove(invitee);
    }
}
