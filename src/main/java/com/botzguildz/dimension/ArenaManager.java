package com.botzguildz.dimension;

import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.GuildWar;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages arena instances for guild wars.
 *
 * Each war gets its own arena slot in the guild_arena dimension.
 * Arenas are spaced 200 blocks apart on the X axis to avoid overlap.
 */
public class ArenaManager {

    public static final String ARENA_DIMENSION_ID = "botzguildz:guild_arena";
    public static final ResourceKey<Level> ARENA_DIMENSION =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    new ResourceLocation("botzguildz", "guild_arena"));

    // Outer radius of the Colosseum is ~98 blocks; 250-block spacing gives a 54-block
    // buffer between adjacent arenas.  Increase here if the generator grows further.
    private static final int ARENA_SPACING = 250; // blocks between arena centres
    private static final AtomicInteger nextSlot = new AtomicInteger(0);

    // Slot index reserved for the permanent, editable "production" arena.
    // Wars use this slot (and skip generation) when customArena mode is enabled.
    public static final int CUSTOM_ARENA_CX = 0;
    public static final int CUSTOM_ARENA_CZ = 0;

    // ── Setup an arena war ────────────────────────────────────────────────────

    /**
     * Generate an arena, teleport all online members of both guilds in, and save return positions.
     */
    public static void setupArenaWar(GuildWar war, Guild declaring, Guild challenged,
                                     MinecraftServer server) {
        ServerLevel arenaLevel = server.getLevel(ARENA_DIMENSION);
        if (arenaLevel == null) {
            MessageUtils.broadcastToGuild(declaring, MessageUtils.error("Arena dimension not found! Falling back to open world war."), server);
            MessageUtils.broadcastToGuild(challenged, MessageUtils.error("Arena dimension not found! Falling back to open world war."), server);
            return;
        }

        // Determine arena position: use the fixed custom arena if that mode is on,
        // otherwise allocate the next fresh slot and generate a new Colosseum.
        GuildSavedData data = GuildSavedData.get(server);
        int cx, cz;
        if (data.isCustomArena()) {
            cx = CUSTOM_ARENA_CX;
            cz = CUSTOM_ARENA_CZ;
            // Do NOT regenerate — preserve the admin's custom build
        } else {
            int slot = nextSlot.getAndIncrement();
            cx = slot * ARENA_SPACING;
            cz = 0;
            ArenaGenerator.generate(arenaLevel, cx, cz);
        }

        war.setArenaCenter(new BlockPos(cx, ArenaGenerator.FLOOR_Y, cz));

        BlockPos spawnA = ArenaGenerator.getTeamASpawn(cx, cz);
        BlockPos spawnB = ArenaGenerator.getTeamBSpawn(cx, cz);

        // Teleport declaring guild members to spawn A
        List<ServerPlayer> declaringOnline = GuildUtils.getOnlineMembers(declaring, server);
        for (ServerPlayer player : declaringOnline) {
            String returnDim = player.level().dimension().location().toString();
            BlockPos returnPos = player.blockPosition();
            war.saveReturnPosition(player.getUUID(), returnPos, returnDim);
            GuildUtils.teleportPlayer(player, spawnA, ARENA_DIMENSION_ID);
            MessageUtils.broadcastWarEvent(declaring, "Teleporting to the arena!", server);
        }

        // Teleport challenged guild members to spawn B
        List<ServerPlayer> challengedOnline = GuildUtils.getOnlineMembers(challenged, server);
        for (ServerPlayer player : challengedOnline) {
            String returnDim = player.level().dimension().location().toString();
            BlockPos returnPos = player.blockPosition();
            war.saveReturnPosition(player.getUUID(), returnPos, returnDim);
            GuildUtils.teleportPlayer(player, spawnB, ARENA_DIMENSION_ID);
        }

        GuildSavedData.get(server).setDirty();
    }

    // ── Return all players ────────────────────────────────────────────────────

    /**
     * Teleport all war participants back to their pre-war positions.
     */
    public static void returnAllPlayers(GuildWar war, MinecraftServer server) {
        for (java.util.UUID uuid : war.getParticipantGuildMap().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            returnPlayer(war, player, server);
        }
    }

    /**
     * Return a single player to their saved pre-war position.
     */
    public static void returnPlayer(GuildWar war, ServerPlayer player, MinecraftServer server) {
        BlockPos returnPos = war.getReturnPosition(player.getUUID());
        String  returnDim  = war.getReturnDimension(player.getUUID());

        if (returnPos != null && returnDim != null) {
            GuildUtils.teleportPlayer(player, returnPos, returnDim);
        } else {
            // Fallback: send to overworld spawn
            ServerLevel overworld = server.overworld();
            BlockPos spawnPos = overworld.getSharedSpawnPos();
            GuildUtils.teleportPlayer(player, spawnPos, "minecraft:overworld");
        }
        player.sendSystemMessage(MessageUtils.info("Returned to your pre-war location."));
    }
}
