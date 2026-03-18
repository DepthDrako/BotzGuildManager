package com.botzguildz.command;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.*;
import com.botzguildz.gui.BountyItemPickerMenu;
import com.botzguildz.gui.GuildBountyBoardMenu;
import com.botzguildz.gui.GuildBountyPostMenu;
import com.botzguildz.market.GuildBountyEntry;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.UUID;

/**
 * /guild bounty subcommands.
 *
 * <pre>
 * /guild bounty                    — open the bounty board
 * /guild bounty post [qty] [reward] — hold item, open post menu (GUI) or quick-post via args
 * /guild bounty cancel <id>        — cancel own bounty (MANAGE_BOUNTIES required)
 * /guild bounty list               — text summary of active bounties
 * </pre>
 */
public class GuildBountyCommand {

    /** Registers all /guild bounty subcommands onto the parent 'guild' literal. */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild.then(Commands.literal("bounty")
                .requires(CommandSourceStack::isPlayer)

                // /guild bounty — open board
                .executes(ctx -> openBoard(ctx.getSource()))

                // /guild bounty post — hold item, open post GUI
                .then(Commands.literal("post")
                        .executes(ctx -> openPickerOrHeldItem(ctx.getSource()))
                        // /guild bounty post <qty> <reward> — quick-post with held item
                        .then(Commands.argument("qty", IntegerArgumentType.integer(1, 1000))
                                .then(Commands.argument("reward", StringArgumentType.word())
                                        .executes(ctx -> quickPost(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "qty"),
                                                StringArgumentType.getString(ctx, "reward"))))))

                // /guild bounty cancel <id>
                .then(Commands.literal("cancel")
                        .then(Commands.argument("entryId", StringArgumentType.word())
                                .executes(ctx -> cancelBounty(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "entryId")))))

                // /guild bounty list — chat text summary
                .then(Commands.literal("list")
                        .executes(ctx -> listBounties(ctx.getSource())))
        );
    }

    // ── /guild bounty ─────────────────────────────────────────────────────────

    private static int openBoard(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0;
            }
            UUID gId = guild.getGuildId();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal("Guild Bounty Board"); }
                        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new GuildBountyBoardMenu(id, inv, gId, 1);
                        }
                    },
                    buf -> { buf.writeBoolean(true); buf.writeUUID(gId); buf.writeInt(1); });
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); return 0;
        }
        return 1;
    }

    // ── /guild bounty post ────────────────────────────────────────────────────

    /**
     * No args: if player holds an item open the post GUI pre-populated; otherwise open picker.
     */
    private static int openPickerOrHeldItem(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0;
            }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_BOUNTIES)) {
                src.sendFailure(MessageUtils.error("You don't have permission to post bounties.")); return 0;
            }

            UUID gId = guild.getGuildId();
            ItemStack held = player.getMainHandItem();

            if (!held.isEmpty()) {
                // Held item shortcut → open post menu directly
                ItemStack item = held.copy();
                item.setCount(1);
                NetworkHooks.openScreen(player,
                        new MenuProvider() {
                            @Override public Component getDisplayName() { return Component.literal("Post Bounty"); }
                            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                                return new GuildBountyPostMenu(id, inv, gId, item);
                            }
                        },
                        buf -> { buf.writeBoolean(true); buf.writeUUID(gId); buf.writeItem(item); });
            } else {
                // No item held → open item picker
                NetworkHooks.openScreen(player,
                        new MenuProvider() {
                            @Override public Component getDisplayName() { return Component.literal("Select Item"); }
                            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                                return new BountyItemPickerMenu(id, inv, gId, 0, 0, 1, "");
                            }
                        },
                        buf -> {
                            buf.writeBoolean(true); buf.writeUUID(gId);
                            buf.writeInt(0); buf.writeInt(0); buf.writeInt(1); buf.writeUtf("");
                        });
            }
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); return 0;
        }
        return 1;
    }

    // ── /guild bounty post <qty> <reward> (quick-post with held item) ─────────

    private static int quickPost(CommandSourceStack src, int qty, String rewardStr) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0;
            }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_BOUNTIES)) {
                src.sendFailure(MessageUtils.error("You don't have permission to post bounties.")); return 0;
            }

            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                src.sendFailure(MessageUtils.error("Hold the target item in your main hand.")); return 0;
            }

            long reward;
            try {
                reward = Long.parseLong(rewardStr);
            } catch (NumberFormatException e) {
                src.sendFailure(MessageUtils.error("Invalid reward amount: " + rewardStr)); return 0;
            }
            if (reward <= 0) {
                src.sendFailure(MessageUtils.error("Reward must be greater than 0.")); return 0;
            }
            if (guild.getAvailableBalance() < reward) {
                src.sendFailure(MessageUtils.error("Insufficient guild bank funds. Available: "
                        + CurrencyManager.format(guild.getAvailableBalance()))); return 0;
            }

            boolean withdrew = guild.withdraw(reward);
            if (!withdrew) {
                src.sendFailure(MessageUtils.error("Failed to withdraw from guild bank.")); return 0;
            }

            ItemStack item = held.copy(); item.setCount(1);
            GuildBountyEntry entry = new GuildBountyEntry(
                    UUID.randomUUID(), guild.getGuildId(),
                    player.getUUID(), player.getName().getString(),
                    item, qty, reward);
            GuildBountyData.get(player.getServer()).addBounty(entry);
            GuildSavedData.get(player.getServer()).setDirty();
            guild.addLog(player.getName().getString() + " posted bounty: " + qty + "x "
                    + item.getHoverName().getString() + " → " + CurrencyManager.format(reward) + ".");

            src.sendSuccess(() -> MessageUtils.success("Bounty posted: " + qty + "x "
                    + item.getHoverName().getString() + " for "
                    + CurrencyManager.format(reward) + "."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); return 0;
        }
        return 1;
    }

    // ── /guild bounty cancel <id> ─────────────────────────────────────────────

    private static int cancelBounty(CommandSourceStack src, String idStr) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0;
            }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_BOUNTIES)) {
                src.sendFailure(MessageUtils.error("You don't have permission to cancel bounties.")); return 0;
            }

            UUID guildId = guild.getGuildId();
            GuildBountyData data = GuildBountyData.get(server);
            List<GuildBountyEntry> entries = data.getBountiesForGuild(guildId);

            // Support 8-char prefix or full UUID
            UUID entryId;
            if (idStr.length() == 8) {
                GuildBountyEntry match = entries.stream()
                        .filter(e -> e.getEntryId().toString().startsWith(idStr))
                        .findFirst().orElse(null);
                if (match == null) {
                    src.sendFailure(MessageUtils.error("Bounty '" + idStr + "' not found.")); return 0;
                }
                entryId = match.getEntryId();
            } else {
                try { entryId = UUID.fromString(idStr); }
                catch (IllegalArgumentException e) {
                    src.sendFailure(MessageUtils.error("Invalid bounty ID: " + idStr)); return 0;
                }
            }

            long refunded = data.cancelBounty(entryId, guildId, guild);
            if (refunded < 0) {
                src.sendFailure(MessageUtils.error("Bounty not found.")); return 0;
            }
            GuildSavedData.get(server).setDirty();
            final long r = refunded;
            src.sendSuccess(() -> MessageUtils.success("Bounty cancelled. "
                    + CurrencyManager.format(r) + " refunded to guild bank."), false);
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); return 0;
        }
        return 1;
    }

    // ── /guild bounty list ────────────────────────────────────────────────────

    private static int listBounties(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0;
            }

            List<GuildBountyEntry> entries = GuildBountyData.get(player.getServer())
                    .getBountiesForGuild(guild.getGuildId());

            src.sendSuccess(() -> MessageUtils.header("Guild Bounties — " + guild.getName()), false);
            if (entries.isEmpty()) {
                src.sendSuccess(() -> MessageUtils.info("No active bounties."), false);
            } else {
                for (GuildBountyEntry e : entries) {
                    final GuildBountyEntry entry = e;
                    src.sendSuccess(() -> Component.literal(
                            "  [" + entry.getEntryId().toString().substring(0, 8) + "] ")
                            .withStyle(MessageUtils.GRAY)
                            .append(Component.literal(entry.getQuantityRequired() + "x "
                                    + entry.getTargetItem().getHoverName().getString()).withStyle(MessageUtils.WHITE))
                            .append(Component.literal(" → " + CurrencyManager.format(entry.getRewardAmount()))
                                    .withStyle(MessageUtils.YELLOW)), false);
                }
            }
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage())); return 0;
        }
        return 1;
    }
}
