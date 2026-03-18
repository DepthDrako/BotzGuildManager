package com.botzguildz.command;

import com.botzguildz.data.Guild;
import com.botzguildz.gui.GuildVaultMenu;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * Handles {@code /guild vault}.
 * Registered by {@link GuildCommand}.
 */
public class GuildVaultCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild
            .then(Commands.literal("vault")
                    .executes(ctx -> openVault(ctx.getSource())));
    }

    private static int openVault(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) { src.sendFailure(MessageUtils.error("You are not in a guild.")); return 0; }

            UUID gId = guild.getGuildId();
            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal("Guild Vault"); }
                        @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new GuildVaultMenu(id, inv, gId);
                        }
                    },
                    buf -> { buf.writeBoolean(true); buf.writeUUID(gId); }
            );
            return 1;
        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("Error: " + e.getMessage())); return 0;
        }
    }
}
