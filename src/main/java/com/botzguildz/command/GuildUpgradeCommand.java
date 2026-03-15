package com.botzguildz.command;

import com.botzguildz.currency.CurrencyManager;
import com.botzguildz.data.Guild;
import com.botzguildz.data.GuildSavedData;
import com.botzguildz.data.RankPermission;
import com.botzguildz.ftb.FTBBridge;
import com.botzguildz.gui.UpgradesMenu;
import com.botzguildz.upgrade.GuildUpgradeDef;
import com.botzguildz.upgrade.UpgradeCategoryDef;
import com.botzguildz.upgrade.UpgradeRegistry;
import com.botzguildz.util.GuildUtils;
import com.botzguildz.util.MessageUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;

public class GuildUpgradeCommand {

    // ── Suggestion providers ───────────────────────────────────────────────────

    /** Suggests all loaded category IDs. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CATEGORIES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    UpgradeRegistry.INSTANCE.getCategories().stream()
                            .map(UpgradeCategoryDef::getId),
                    builder);

    /** Suggests all loaded upgrade IDs. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_UPGRADES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    UpgradeRegistry.INSTANCE.getAllUpgrades().stream()
                            .map(GuildUpgradeDef::getId),
                    builder);

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(LiteralArgumentBuilder<CommandSourceStack> guild) {
        guild.then(Commands.literal("upgrade")
                .requires(src -> src.isPlayer())

                // /guild upgrade                  — opens the GUI on the default tab
                .executes(ctx -> openGui(ctx.getSource(), null))

                // /guild upgrade <category>       — opens the GUI on a specific tab
                .then(Commands.argument("category", StringArgumentType.word())
                        .suggests(SUGGEST_CATEGORIES)
                        .executes(ctx -> openGui(ctx.getSource(),
                                StringArgumentType.getString(ctx, "category"))))

                // /guild upgrade info <upgrade>   — text info card
                .then(Commands.literal("info")
                        .then(Commands.argument("upgrade", StringArgumentType.word())
                                .suggests(SUGGEST_UPGRADES)
                                .executes(ctx -> info(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "upgrade")))))

                // /guild upgrade buy <upgrade>    — purchase via command
                .then(Commands.literal("buy")
                        .then(Commands.argument("upgrade", StringArgumentType.word())
                                .suggests(SUGGEST_UPGRADES)
                                .executes(ctx -> buy(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "upgrade")))))
        );
    }

    // ── /guild upgrade [category] — opens the chest GUI ──────────────────────

    private static int openGui(CommandSourceStack src, String categoryFilter) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild."));
                return 0;
            }

            UpgradeRegistry reg = UpgradeRegistry.INSTANCE;

            // Resolve start category
            UpgradeCategoryDef startCat;
            if (categoryFilter == null || categoryFilter.isBlank()) {
                startCat = reg.getCategories().isEmpty() ? null : reg.getCategories().get(0);
            } else {
                startCat = reg.getCategoryById(categoryFilter.toUpperCase());
                if (startCat == null) {
                    src.sendFailure(MessageUtils.error(
                            "Unknown category '" + categoryFilter + "'. Valid: "
                            + String.join(", ", reg.getCategories().stream()
                                    .map(UpgradeCategoryDef::getId).toList())));
                    return 0;
                }
            }

            if (startCat == null) {
                src.sendFailure(MessageUtils.error("No upgrade categories are loaded."));
                return 0;
            }

            final String catId = startCat.getId();

            NetworkHooks.openScreen(player,
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.literal("Guild Upgrades");
                        }

                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new UpgradesMenu(id, inv, catId, 1);
                        }
                    },
                    buf -> { buf.writeUtf(catId); buf.writeInt(1); }
            );

        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred: " + e.getMessage()));
        }
        return 1;
    }

    // ── /guild upgrade info <upgrade> ─────────────────────────────────────────

    private static int info(CommandSourceStack src, String upgradeId) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild."));
                return 0;
            }

            GuildUpgradeDef upgrade = UpgradeRegistry.INSTANCE.getUpgradeById(upgradeId.toUpperCase());
            if (upgrade == null) {
                src.sendFailure(MessageUtils.error("Unknown upgrade '" + upgradeId + "'."));
                return 0;
            }

            final GuildUpgradeDef u = upgrade;
            UpgradeCategoryDef cat = UpgradeRegistry.INSTANCE.getCategoryById(u.getCategoryId());
            String catDisplayName  = cat != null ? cat.getName() : u.getCategoryId();

            src.sendSuccess(() -> MessageUtils.header(u.getDisplayName()), false);
            src.sendSuccess(() -> Component.literal("  Category: ").withStyle(MessageUtils.GOLD)
                    .append(Component.literal(catDisplayName).withStyle(MessageUtils.WHITE)), false);
            src.sendSuccess(() -> Component.literal("  Cost: ").withStyle(MessageUtils.GOLD)
                    .append(Component.literal(CurrencyManager.format(u.getCost()))
                            .withStyle(MessageUtils.YELLOW)), false);
            src.sendSuccess(() -> Component.literal("  Required Level: ").withStyle(MessageUtils.GOLD)
                    .append(Component.literal(String.valueOf(u.getRequiredLevel()))
                            .withStyle(MessageUtils.WHITE)), false);
            if (u.getPrerequisiteId() != null)
                src.sendSuccess(() -> Component.literal("  Requires: ").withStyle(MessageUtils.GOLD)
                        .append(Component.literal(u.getPrerequisiteId())
                                .withStyle(MessageUtils.WHITE)), false);
            src.sendSuccess(() -> Component.literal("  " + u.getDescription())
                    .withStyle(MessageUtils.GRAY), false);

            boolean owned     = guild.hasUpgrade(u.getId());
            boolean available = u.isAvailableTo(guild);
            src.sendSuccess(() -> Component.literal("  Status: ").withStyle(MessageUtils.GOLD)
                    .append(Component.literal(owned ? "Owned" : available ? "Available" : "Locked")
                            .withStyle(owned ? MessageUtils.GREEN
                                    : available ? MessageUtils.YELLOW
                                    : MessageUtils.GRAY)), false);

        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred."));
        }
        return 1;
    }

    // ── /guild upgrade buy <upgrade> ──────────────────────────────────────────

    private static int buy(CommandSourceStack src, String upgradeId) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            Guild guild = GuildUtils.getGuildOf(player);
            if (guild == null) {
                src.sendFailure(MessageUtils.error("You are not in a guild."));
                return 0;
            }
            if (!guild.hasPermission(player.getUUID(), RankPermission.MANAGE_UPGRADES)) {
                src.sendFailure(MessageUtils.error("You don't have permission to purchase upgrades."));
                return 0;
            }

            GuildUpgradeDef upgrade = UpgradeRegistry.INSTANCE.getUpgradeById(upgradeId.toUpperCase());
            if (upgrade == null) {
                src.sendFailure(MessageUtils.error("Unknown upgrade '" + upgradeId + "'."));
                return 0;
            }
            if (guild.hasUpgrade(upgrade.getId())) {
                src.sendFailure(MessageUtils.error(
                        "Your guild already has " + upgrade.getDisplayName() + "."));
                return 0;
            }
            if (!upgrade.isAvailableTo(guild)) {
                String reason = guild.getLevel() < upgrade.getRequiredLevel()
                        ? "Your guild must be level " + upgrade.getRequiredLevel()
                          + " (currently " + guild.getLevel() + ")."
                        : "You need the '" + upgrade.getPrerequisiteId() + "' upgrade first.";
                src.sendFailure(MessageUtils.error(reason));
                return 0;
            }
            if (!guild.canAfford(upgrade.getCost())) {
                src.sendFailure(MessageUtils.error("Insufficient guild bank funds. Need "
                        + CurrencyManager.format(upgrade.getCost()) + ", available: "
                        + CurrencyManager.format(guild.getAvailableBalance()) + "."));
                return 0;
            }

            // Complete the purchase
            guild.withdraw(upgrade.getCost());
            guild.addUpgrade(upgrade.getId());
            GuildSavedData data = GuildSavedData.get(player.getServer());
            data.setDirty();

            // FTB Chunks integration
            String uid = upgrade.getId();
            if (uid.equals("CHUNK_CLAIM_I") || uid.equals("CHUNK_CLAIM_II")
                    || uid.equals("CHUNK_FORCE_LOAD")) {
                FTBBridge.applyChunkClaimBonus(guild, player.getServer());
            }

            // Apply one-time effects (GIVE_ITEM, COMMAND)
            UpgradesMenu.applyPurchaseEffects(upgrade, player, guild);

            guild.addLog(player.getName().getString()
                    + " purchased upgrade: " + upgrade.getDisplayName() + ".");
            MessageUtils.broadcastToGuild(guild,
                    MessageUtils.success("Guild upgrade purchased: " + upgrade.getDisplayName() + "!"),
                    player.getServer());

        } catch (Exception e) {
            src.sendFailure(MessageUtils.error("An error occurred."));
        }
        return 1;
    }
}
