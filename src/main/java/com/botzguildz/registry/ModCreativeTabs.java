package com.botzguildz.registry;

import com.botzguildz.BotzGuildz;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Creative-mode tabs added by BotzGuildz.
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BotzGuildz.MODID);

    /**
     * "Boss Bank" tab — contains all six guild currency denominations
     * in ascending order (Bit → Chip → Token → Coin → Mark → Seal).
     */
    public static final RegistryObject<CreativeModeTab> BOSS_BANK_TAB =
            CREATIVE_MODE_TABS.register("boss_bank", () ->
                    CreativeModeTab.builder()
                            .title(Component.literal("Boss Bank"))
                            .icon(() -> new ItemStack(ModItems.GUILD_SEAL.get()))
                            .displayItems((params, output) -> {
                                output.accept(ModItems.GUILD_BIT.get());
                                output.accept(ModItems.GUILD_CHIP.get());
                                output.accept(ModItems.GUILD_TOKEN.get());
                                output.accept(ModItems.GUILD_COIN.get());
                                output.accept(ModItems.GUILD_MARK.get());
                                output.accept(ModItems.GUILD_SEAL.get());
                            })
                            .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
