package com.botzguildz.util;

import com.botzguildz.market.MarketListing;
import com.botzguildz.market.PlayerShop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Utility for keeping wall signs adjacent to a shop block in sync with
 * the shop's current state (listings, open/closed).
 *
 * <p>Call {@link #updateAdjacentSigns} whenever shop data changes:
 * listing added, listing removed, shop opened/closed.
 *
 * <p>Sign front-face text (4 lines):
 * <pre>
 *   [SHOP]
 *   OwnerName
 *   First item (or "Empty")
 *   OPEN  /  CLOSED
 * </pre>
 */
public final class ShopSignHelper {

    private ShopSignHelper() {}

    /**
     * Scan all 6 faces adjacent to {@code shopBlockPos} in {@code level}.
     * For every {@link WallSignBlock} found that is attached to
     * {@code shopBlockPos}, update its front-face text with the shop info.
     */
    public static void updateAdjacentSigns(ServerLevel level,
                                           BlockPos shopBlockPos,
                                           PlayerShop shop) {
        if (shopBlockPos == null) return;

        for (Direction dir : Direction.values()) {
            BlockPos signPos = shopBlockPos.relative(dir);
            BlockState signState = level.getBlockState(signPos);

            // Must be a wall sign
            if (!(signState.getBlock() instanceof WallSignBlock)) continue;

            // Sign must be attached to shopBlockPos.
            // WallSignBlock.FACING points AWAY from the wall (outward normal),
            // which is the same direction as `dir` (shop → sign).
            Direction signFacing = signState.getValue(WallSignBlock.FACING);
            if (signFacing != dir) continue;

            if (!(level.getBlockEntity(signPos) instanceof SignBlockEntity sign)) continue;

            // Build 4-line text
            String ownerLine   = truncate(shop.getOwnerName(), 15);
            String itemLine    = buildItemLine(shop);
            String statusLine  = shop.isOpen() ? ">> OPEN <<" : "-- CLOSED --";

            SignText updated = sign.getFrontText()
                    .setMessage(0, Component.literal("[SHOP]"))
                    .setMessage(1, Component.literal(ownerLine))
                    .setMessage(2, Component.literal(itemLine))
                    .setMessage(3, Component.literal(statusLine));

            sign.setText(updated, true);
            sign.setChanged();
            level.sendBlockUpdated(signPos, signState, signState, 3);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String buildItemLine(PlayerShop shop) {
        if (shop.getListings().isEmpty()) return "Empty";
        MarketListing first = shop.getListings().get(0);
        String name  = truncate(first.getItem().getHoverName().getString(), 13);
        int    extra = shop.getListings().size() - 1;
        return extra > 0 ? name + "+" + extra : name;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
