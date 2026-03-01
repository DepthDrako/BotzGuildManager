package com.botzguildz.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Generates a massive Roman Colosseum-style arena centred at (cx, FLOOR_Y, cz).
 * Designed to comfortably hold 100+ concurrent players, with elevated spectator
 * galleries and dramatic multi-tier architecture.
 *
 * <pre>
 *  Layer              Y offset      Outer radius   Description
 *  ─────────────────────────────────────────────────────────────────────────────
 *  Combat floor        +0            r=0–60         Sand / gravel / andesite mix
 *  Transition ring     +0            r=61–62        Stone brick border
 *  Tier 1 wall         +1  … +18     r=63–90        Deepslate base (y+1..+4), stone brick above
 *  Cornice 1           +19           r=63–93        Polished andesite ledge
 *  Tier 2 wall         +20 … +28     r=63–79        Stone brick mid ring
 *  Cornice 2           +29           r=63–82        Polished andesite ledge
 *  Tier 3 wall         +30 … +37     r=63–70        Stone brick upper ring
 *  Spectator row 1     +38           r=63–70        Red wool
 *  Spectator row 2     +39           r=63–70        Orange wool (stepped)
 *  Merlons             +40           r=63–70        Alternating stone-brick battlements
 *  Barrier ceiling     +60           r=0–63         Invisible escape-prevention cap
 *
 *  12 arch windows on tier 1 outer face (at 22.5° intervals, skipping gateways)
 *   8 arch windows on tier 2 outer face (at 45° intervals)
 *   4 arched gateways at N/S/E/W (7 wide × 12 tall pointed arch)
 *  12 inner wall torch columns (skipping near gateways)
 *  Glowstone lighting rings embedded in floor (r=25 and r=48)
 *  Large 11×11 spawn platforms at North / South
 * </pre>
 */
public class ArenaGenerator {

    public static final int FLOOR_Y = 64;

    // ── Radii ─────────────────────────────────────────────────────────────────

    private static final int R_FLOOR    = 60;   // open combat floor
    private static final int R_WALL_IN  = 63;   // inner face of the ring wall
    private static final int R_T1       = 90;   // tier 1 outer face  (widest / lowest)
    private static final int R_T1_CORN  = 93;   // tier 1 cornice protruding ledge
    private static final int R_T2       = 79;   // tier 2 outer face
    private static final int R_T2_CORN  = 82;   // tier 2 cornice
    private static final int R_T3       = 70;   // tier 3 outer face  (narrowest / highest)
    private static final int R_CLEAR    = 98;   // air-clearing / stone-base cylinder

    // ── Y offsets from FLOOR_Y ────────────────────────────────────────────────

    private static final int Y_T1_TOP  = 18;   // tier 1:    +1 … +18
    private static final int Y_C1      = 19;   // cornice 1:  +19
    private static final int Y_T2_BOT  = 20;   // tier 2:    +20 … +28
    private static final int Y_T2_TOP  = 28;
    private static final int Y_C2      = 29;   // cornice 2:  +29
    private static final int Y_T3_BOT  = 30;   // tier 3:    +30 … +37
    private static final int Y_T3_TOP  = 37;
    private static final int Y_SEAT1   = 38;   // spectator row 1:  +38  (red wool)
    private static final int Y_SEAT2   = 39;   // spectator row 2:  +39  (orange wool)
    private static final int Y_MERLON  = 40;   // merlons:    +40
    private static final int Y_BARRIER = 60;   // barrier cap:+60

    // ── Block palette ─────────────────────────────────────────────────────────

    private static final BlockState SB      = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState SB_CR   = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState SB_MO   = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState SB_CH   = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    private static final BlockState P_AND   = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState AND     = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState RED_W   = Blocks.RED_WOOL.defaultBlockState();
    private static final BlockState ORG_W   = Blocks.ORANGE_WOOL.defaultBlockState();
    private static final BlockState AIR     = Blocks.AIR.defaultBlockState();
    private static final BlockState BAR     = Blocks.BARRIER.defaultBlockState();
    private static final BlockState TORCH   = Blocks.TORCH.defaultBlockState();
    private static final BlockState STONE   = Blocks.STONE.defaultBlockState();
    private static final BlockState DSLT    = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState DSLT_CR = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState DSLT_CH = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
    private static final BlockState SAND    = Blocks.SAND.defaultBlockState();
    private static final BlockState GRAVEL  = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState GLO     = Blocks.GLOWSTONE.defaultBlockState();

    // ── Public API (called by ArenaManager) ───────────────────────────────────

    public static void generate(ServerLevel level, int cx, int cz) {
        // Foundation + air clear
        clearAndBase(level, cx, cz);

        // Combat floor
        buildFloor(level, cx, cz);

        // Stone-brick base layer sealing the 1-block gap at the foot of the outer wall
        buildWallBase(level, cx, cz);

        // Three tiers with polished-andesite cornices between them
        buildRingLayer(level, cx, cz,        1, Y_T1_TOP, R_WALL_IN, R_T1);
        buildFlatRing (level, cx, cz, Y_C1,     R_WALL_IN, R_T1_CORN, P_AND);
        buildRingLayer(level, cx, cz, Y_T2_BOT, Y_T2_TOP, R_WALL_IN, R_T2);
        buildFlatRing (level, cx, cz, Y_C2,     R_WALL_IN, R_T2_CORN, P_AND);
        buildRingLayer(level, cx, cz, Y_T3_BOT, Y_T3_TOP, R_WALL_IN, R_T3);

        // Top dressing: two rows of stepped spectator seating + merlons
        buildFlatRing(level, cx, cz, Y_SEAT1, R_WALL_IN, R_T3, RED_W);
        buildFlatRing(level, cx, cz, Y_SEAT2, R_WALL_IN, R_T3, ORG_W);
        buildMerlons (level, cx, cz);

        // Decorative details — gateways must come last to re-punch the arch holes,
        // then end-caps seal the open outer end of each tunnel
        buildTier1Windows  (level, cx, cz);
        buildTier2Windows  (level, cx, cz);
        buildGateways      (level, cx, cz);
        buildGatewayEndCaps(level, cx, cz);
        buildInnerColumns  (level, cx, cz);
        buildFloorLighting (level, cx, cz);

        // Spawn platforms + invisible safety ceiling
        buildSpawnPlatform(level, cx, cz - (R_FLOOR - 10));  // Team A  (north)
        buildSpawnPlatform(level, cx, cz + (R_FLOOR - 10));  // Team B  (south)
        buildBarrierCap   (level, cx, cz);
    }

    public static BlockPos getTeamASpawn(int cx, int cz) {
        return new BlockPos(cx, FLOOR_Y + 2, cz - (R_FLOOR - 10));
    }

    public static BlockPos getTeamBSpawn(int cx, int cz) {
        return new BlockPos(cx, FLOOR_Y + 2, cz + (R_FLOOR - 10));
    }

    // ── Step implementations ──────────────────────────────────────────────────

    /**
     * Solid stone foundation below FLOOR_Y, then clear air from FLOOR_Y up to
     * two blocks above the barrier cap.
     */
    private static void clearAndBase(ServerLevel level, int cx, int cz) {
        for (int dx = -R_CLEAR; dx <= R_CLEAR; dx++) {
            for (int dz = -R_CLEAR; dz <= R_CLEAR; dz++) {
                if (!inCircle(dx, dz, R_CLEAR)) continue;
                for (int y = FLOOR_Y - 6; y < FLOOR_Y; y++)
                    set(level, cx + dx, y, cz + dz, STONE);
                for (int y = FLOOR_Y; y <= FLOOR_Y + Y_BARRIER + 2; y++)
                    set(level, cx + dx, y, cz + dz, AIR);
            }
        }
    }

    /**
     * Sand/gravel/andesite combat floor inside r=R_FLOOR with a stone-brick
     * transition ring between the floor edge and the inner wall.
     */
    private static void buildFloor(ServerLevel level, int cx, int cz) {
        for (int dx = -(R_WALL_IN - 1); dx <= R_WALL_IN - 1; dx++) {
            for (int dz = -(R_WALL_IN - 1); dz <= R_WALL_IN - 1; dz++) {
                if (inCircle(dx, dz, R_FLOOR)) {
                    set(level, cx + dx, FLOOR_Y, cz + dz, floorTile(dx, dz));
                } else if (inRing(dx, dz, R_FLOOR + 1, R_WALL_IN - 1)) {
                    set(level, cx + dx, FLOOR_Y, cz + dz, SB);
                }
            }
        }
    }

    /** Solid ring wall spanning Y offsets yBotOff through yTopOff (inclusive). */
    private static void buildRingLayer(ServerLevel level, int cx, int cz,
                                       int yBotOff, int yTopOff, int rIn, int rOut) {
        for (int y = yBotOff; y <= yTopOff; y++) {
            for (int dx = -rOut; dx <= rOut; dx++) {
                for (int dz = -rOut; dz <= rOut; dz++) {
                    if (inRing(dx, dz, rIn, rOut)) {
                        set(level, cx + dx, FLOOR_Y + y, cz + dz, wallTile(dx, dz, y));
                    }
                }
            }
        }
    }

    /** Single-height ring filled with one block type (cornice ledge, seats, …). */
    private static void buildFlatRing(ServerLevel level, int cx, int cz,
                                      int yOff, int rIn, int rOut, BlockState block) {
        for (int dx = -rOut; dx <= rOut; dx++) {
            for (int dz = -rOut; dz <= rOut; dz++) {
                if (inRing(dx, dz, rIn, rOut)) {
                    set(level, cx + dx, FLOOR_Y + yOff, cz + dz, block);
                }
            }
        }
    }

    /**
     * Alternating stone-brick merlons on the very top of tier 3.
     * One merlon block per ~8-degree arc segment on even-numbered segments.
     */
    private static void buildMerlons(ServerLevel level, int cx, int cz) {
        for (int dx = -R_T3; dx <= R_T3; dx++) {
            for (int dz = -R_T3; dz <= R_T3; dz++) {
                if (!inRing(dx, dz, R_WALL_IN, R_T3)) continue;
                int seg = (int) ((Math.toDegrees(Math.atan2(dz, dx)) + 180.0) / 8.0);
                if (seg % 2 == 0) {
                    set(level, cx + dx, FLOOR_Y + Y_MERLON, cz + dz, SB);
                }
            }
        }
    }

    /**
     * 12 pointed arch windows on the tier 1 outer face, at 22.5° intervals
     * (skipping positions within 18° of a gateway).  Each window is 3 wide,
     * 13 blocks tall to a pointed apex, with a chiseled sill and keystone.
     */
    private static void buildTier1Windows(ServerLevel level, int cx, int cz) {
        for (int i = 0; i < 16; i++) {
            double aDeg = i * 22.5;
            if (nearGateway(aDeg)) continue;

            double rad = Math.toRadians(aDeg);
            double ux  =  Math.cos(rad);   // outward radial direction
            double uz  =  Math.sin(rad);
            double tx  = -Math.sin(rad);   // tangential (perpendicular) direction
            double tz  =  Math.cos(rad);

            // Centre column tallest; side columns 2 shorter
            for (int p = -1; p <= 1; p++) {
                int topY = (p == 0) ? 14 : 12;
                for (int h = 3; h <= topY; h++) {
                    int bx  = (int) Math.round(cx + (R_T1 - 1) * ux + p * tx);
                    int bz  = (int) Math.round(cz + (R_T1 - 1) * uz + p * tz);
                    int ddx = bx - cx;
                    int ddz = bz - cz;
                    if (inRing(ddx, ddz, R_WALL_IN, R_T1 + 1)) {
                        set(level, bx, FLOOR_Y + h, bz, AIR);
                    }
                }
            }

            // Chiseled sill at window base
            int sx = (int) Math.round(cx + (R_T1 - 1) * ux);
            int sz = (int) Math.round(cz + (R_T1 - 1) * uz);
            if (inRing(sx - cx, sz - cz, R_WALL_IN, R_T1)) {
                set(level, sx, FLOOR_Y + 3, sz, SB_CH);
            }

            // Chiseled keystone just above arch apex
            int kx = (int) Math.round(cx + (R_T1 - 1) * ux);
            int kz = (int) Math.round(cz + (R_T1 - 1) * uz);
            if (inRing(kx - cx, kz - cz, R_WALL_IN, R_T1)) {
                set(level, kx, FLOOR_Y + 15, kz, SB_CH);
            }
        }
    }

    /**
     * 8 pointed arch windows on the tier 2 outer face at 45° intervals (diagonal
     * positions — between the tier 1 windows and gateways).
     */
    private static void buildTier2Windows(ServerLevel level, int cx, int cz) {
        double[] angles = {22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5};
        for (double aDeg : angles) {
            double rad = Math.toRadians(aDeg);
            double ux  =  Math.cos(rad);
            double uz  =  Math.sin(rad);
            double tx  = -Math.sin(rad);
            double tz  =  Math.cos(rad);

            for (int p = -1; p <= 1; p++) {
                int topY = Y_T2_BOT + (p == 0 ? 6 : 4);
                for (int h = Y_T2_BOT + 1; h <= topY; h++) {
                    int bx  = (int) Math.round(cx + (R_T2 - 1) * ux + p * tx);
                    int bz  = (int) Math.round(cz + (R_T2 - 1) * uz + p * tz);
                    int ddx = bx - cx;
                    int ddz = bz - cz;
                    if (inRing(ddx, ddz, R_WALL_IN, R_T2 + 1)) {
                        set(level, bx, FLOOR_Y + h, bz, AIR);
                    }
                }
            }
        }
    }

    /**
     * Four arched gateways at N/S/E/W.  Each tunnel is 7 blocks wide and 12 tall
     * with a pointed arch (full-width to h=9, narrowing to 5 wide at h=10, 3 wide at
     * h=11, 1 block keystone at h=12).  Flanked by chiseled-accent columns.
     */
    private static void buildGateways(ServerLevel level, int cx, int cz) {
        // (ax, az) = outward radial unit vector;  (px, pz) = perpendicular
        int[][] dirs = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        for (int[] d : dirs) {
            int ax = d[0], az = d[1];
            int px = -az, pz = ax;  // 90° CW perpendicular

            // Tunnel: sweep full thickness of tier 1 + cornice
            for (int r = R_WALL_IN - 1; r <= R_T1_CORN + 1; r++) {
                for (int p = -3; p <= 3; p++) {
                    int bx = cx + r * ax + p * px;
                    int bz = cz + r * az + p * pz;
                    for (int h = 1; h <= 12; h++) {
                        if (h == 10 && Math.abs(p) > 2) continue;  // arch taper
                        if (h == 11 && Math.abs(p) > 1) continue;
                        if (h == 12 && p != 0)           continue;  // apex keystone
                        set(level, bx, FLOOR_Y + h, bz, AIR);
                    }
                }
            }

            // Flanking columns on the outer face at ±4
            for (int side : new int[]{-4, 4}) {
                for (int h = 1; h <= Y_T1_TOP; h++) {
                    int fx = cx + R_T1 * ax + side * px;
                    int fz = cz + R_T1 * az + side * pz;
                    if (inCircle(fx - cx, fz - cz, R_T1 + 2)) {
                        BlockState col = (h == 1 || h == 7 || h == Y_T1_TOP) ? SB_CH : SB;
                        set(level, fx, FLOOR_Y + h, fz, col);
                    }
                }
            }

            // Chiseled lintel above arch on the outer face
            for (int p = -2; p <= 2; p++) {
                int fx = cx + R_T1 * ax + p * px;
                int fz = cz + R_T1 * az + p * pz;
                set(level, fx, FLOOR_Y + 13, fz, SB_CH);
            }

            // Chiseled keystone on the inner face
            set(level, cx + R_WALL_IN * ax, FLOOR_Y + 13, cz + R_WALL_IN * az, SB_CH);
        }
    }

    /**
     * 12 stone-brick torch columns on the inner wall (r = R_WALL_IN + 1), evenly
     * spaced, skipping those near gateway openings.  Columns have chiseled accents
     * at rows 3, 9, and the top, with a torch placed one block above the column.
     */
    private static void buildInnerColumns(ServerLevel level, int cx, int cz) {
        int count = 16;
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / count;
            if (nearGateway(Math.toDegrees(angle))) continue;

            int dx = (int) Math.round((R_WALL_IN + 1) * Math.cos(angle));
            int dz = (int) Math.round((R_WALL_IN + 1) * Math.sin(angle));

            for (int y = 1; y <= Y_T1_TOP; y++) {
                BlockState b = (y == 3 || y == 9 || y == Y_T1_TOP) ? SB_CH : SB;
                set(level, cx + dx, FLOOR_Y + y, cz + dz, b);
            }
            // Torch on top of each column
            set(level, cx + dx, FLOOR_Y + Y_T1_TOP + 1, cz + dz, TORCH);
        }
    }

    /**
     * Two concentric rings of glowstone embedded in the floor at r=25 and r=48,
     * providing natural arena lighting without obstructing combat.
     */
    private static void buildFloorLighting(ServerLevel level, int cx, int cz) {
        int[] radii = {25, 48};
        int[] counts = {8, 16};
        for (int ring = 0; ring < 2; ring++) {
            int r = radii[ring];
            int count = counts[ring];
            for (int i = 0; i < count; i++) {
                double angle = 2.0 * Math.PI * i / count;
                int dx = (int) Math.round(r * Math.cos(angle));
                int dz = (int) Math.round(r * Math.sin(angle));
                if (inCircle(dx, dz, R_FLOOR)) {
                    set(level, cx + dx, FLOOR_Y, cz + dz, GLO);
                }
            }
        }
    }

    /**
     * Large 11×11 mossy stone-brick spawn platform raised one block above the floor.
     * Chiseled corners, stone-brick edge, mossy stone-brick interior.
     *
     * @param cx  arena centre X
     * @param cz  platform centre Z  (already offset from arena centre)
     */
    private static void buildSpawnPlatform(ServerLevel level, int cx, int cz) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                boolean corner = Math.abs(dx) == 5 && Math.abs(dz) == 5;
                boolean edge   = Math.abs(dx) == 5 || Math.abs(dz) == 5;
                BlockState b   = corner ? SB_CH : edge ? SB : SB_MO;
                set(level, cx + dx, FLOOR_Y + 1, cz + dz, b);
            }
        }
    }

    /** Invisible barrier ring sealing the top of the inner combat cylinder. */
    private static void buildBarrierCap(ServerLevel level, int cx, int cz) {
        for (int dx = -R_WALL_IN; dx <= R_WALL_IN; dx++) {
            for (int dz = -R_WALL_IN; dz <= R_WALL_IN; dz++) {
                if (inCircle(dx, dz, R_WALL_IN)) {
                    set(level, cx + dx, FLOOR_Y + Y_BARRIER, cz + dz, BAR);
                }
            }
        }
    }

    /**
     * Fills the 1-block-high gap at FLOOR_Y under the outer wall and cornice.
     *
     * clearAndBase() clears everything at FLOOR_Y to AIR, and buildRingLayer()
     * starts tier 1 at y+1.  That leaves FLOOR_Y bare (exposing the raw stone
     * foundation below) in the entire wall/cornice ring.  This method places a
     * stone-brick course at FLOOR_Y across that ring, giving the wall a solid base.
     */
    private static void buildWallBase(ServerLevel level, int cx, int cz) {
        int rOuter = R_T1_CORN + 2;   // just past the cornice
        for (int dx = -rOuter; dx <= rOuter; dx++) {
            for (int dz = -rOuter; dz <= rOuter; dz++) {
                if (inRing(dx, dz, R_WALL_IN, rOuter)) {
                    set(level, cx + dx, FLOOR_Y, cz + dz, SB);
                }
            }
        }
    }

    /**
     * Seals the open outer end of each of the four gateway tunnels with a
     * stone-brick wall that matches the gateway's pointed-arch profile.
     *
     * buildGateways() punches the tunnel from r = R_WALL_IN-1 to r = R_T1_CORN+1,
     * leaving the far end (r = R_T1_CORN+2) open to sky.  This method places a
     * closing wall there: solid SB everywhere OUTSIDE the arch opening, and a
     * matching arch-shaped gap in the centre so the tunnel looks finished rather
     * than bricked off.  The arch proportions match the gateway exactly:
     *   h 1–9  : full 7-wide (|p| ≤ 3)
     *   h 10   : 5-wide     (|p| ≤ 2)
     *   h 11   : 3-wide     (|p| ≤ 1)
     *   h 12   : keystone   (p = 0)
     */
    private static void buildGatewayEndCaps(ServerLevel level, int cx, int cz) {
        int[][] dirs = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        int capR = R_T1_CORN + 2;  // one block past the outer end of the tunnel
        for (int[] d : dirs) {
            int ax = d[0], az = d[1];
            int px = -az, pz = ax;

            for (int p = -3; p <= 3; p++) {
                for (int h = 1; h <= 13; h++) {
                    // Arch opening — same shape as the gateway itself
                    boolean open =
                            (h <=  9)                         ||
                            (h == 10 && Math.abs(p) <= 2)     ||
                            (h == 11 && Math.abs(p) <= 1)     ||
                            (h == 12 && p == 0);

                    if (!open) {
                        int bx = cx + capR * ax + p * px;
                        int bz = cz + capR * az + p * pz;
                        BlockState b = (h == 13) ? SB_CH : SB;   // chiseled lintel row
                        set(level, bx, FLOOR_Y + h, bz, b);
                    }
                }
            }
        }
    }

    // ── Block selectors ───────────────────────────────────────────────────────

    /** Sand / gravel / andesite mix — classic Roman arena-sand aesthetic. */
    private static BlockState floorTile(int dx, int dz) {
        int v = Math.abs(dx * 3 + dz * 7) % 16;
        if (v < 8)  return SAND;
        if (v < 12) return GRAVEL;
        return AND;
    }

    /**
     * Deepslate bricks for the lower 4 rows of tier 1 (dark, menacing base);
     * a chiseled-accent transition row at y=5; stone bricks with cracked/mossy
     * variation above.
     */
    private static BlockState wallTile(int dx, int dz, int y) {
        if (y >= 1 && y <= 4) {
            // Deepslate base — darkest at the very foot of the walls
            int v = Math.abs(dx + dz * 13 + y * 5) % 10;
            return (v == 0) ? DSLT_CR : DSLT;
        }
        if (y == 5) {
            // Chiseled deepslate / chiseled stone accent band between deepslate and brick
            int v = Math.abs(dx + dz * 13) % 6;
            return (v == 0) ? DSLT_CH : SB_CH;
        }
        // Stone bricks with ~5 % cracked and ~5 % mossy variation
        int v = Math.abs(dx + dz * 13 + y * 3) % 20;
        if (v == 0) return SB_CR;
        if (v == 1) return SB_MO;
        return SB;
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private static boolean inCircle(int dx, int dz, int r) {
        return (long) dx * dx + (long) dz * dz <= (long) r * r;
    }

    private static boolean inRing(int dx, int dz, int rIn, int rOut) {
        long d2 = (long) dx * dx + (long) dz * dz;
        return d2 >= (long) rIn * rIn && d2 <= (long) rOut * rOut;
    }

    /** Returns true when {@code deg} is within 18° of a cardinal gateway angle. */
    private static boolean nearGateway(double deg) {
        double n = ((deg % 360.0) + 360.0) % 360.0;
        for (double gw : new double[]{0, 90, 180, 270}) {
            double diff = Math.abs(n - gw);
            if (diff > 180) diff = 360 - diff;
            if (diff < 18) return true;
        }
        return false;
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }
}
