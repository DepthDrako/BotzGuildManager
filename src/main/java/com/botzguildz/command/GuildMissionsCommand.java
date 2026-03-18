package com.botzguildz.command;

import com.botzguildz.data.*;
import com.botzguildz.gui.GuildMissionsMenu;
import com.botzguildz.registry.ModMenuTypes;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
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
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.UUID;

/**
 * Handles {@code /guild missions} and sub-commands.
 * Registered by {@link GuildCommand}.
 */
public class GuildMissionsCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild
            // /guild missions — open mission board
            .then(Commands.literal("missions")
                    .executes(ctx -> openMissions(ctx.getSource()))
                    // /guild missions list — text summary
                    .then(Commands.literal("list")
                            .executes(ctx -> listMissions(ctx.getSource())))
            );
    }

    // ── /guild missions ───────────────────────────────────────────────────────

    private static int openMissions(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            UUID gId = guild.getGuildId();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal("Guild Missions"); }
                        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new GuildMissionsMenu(id, inv, gId);
                        }
                    },
                    buf -> { buf.writeBoolean(true); buf.writeUUID(gId); }
            );
            return 1;
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("Error: " + e.getMessage())); return 0;
        }
    }

    // ── /guild missions list ──────────────────────────────────────────────────

    private static int listMissions(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            MinecraftServer server = player.getServer();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            GuildMissionsData md = GuildMissionsData.get(server);
            List<GuildMissionEntry> missions = md.getOrGenerateMissions(guild);

            src.sendSuccess(() -> MessageUtils.header("Guild Missions"), false);
            if (missions.isEmpty()) {
                src.sendSuccess(() -> MessageUtils.info("No active missions right now."), false);
            } else {
                for (int i = 0; i < missions.size(); i++) {
                    GuildMissionEntry m = missions.get(i);
                    int fi = i + 1;
                    src.sendSuccess(() -> MessageUtils.info(
                            fi + ". " + m.getType().label + " " + m.getDisplayName()
                            + "  [" + m.progressBar() + "]"
                            + "  Expires: " + m.timeRemaining()), false);
                }
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("Error: " + e.getMessage())); return 0;
        }
    }
}
