package dev.dada2.aerofethrusters.registry;

import dev.dada2.aerofethrusters.AeroFeThrusters;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Deferred registration for creative mode tabs.
 */
public class AftCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeroFeThrusters.MOD_ID);

    /** Main creative tab containing the electric thruster item. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aero_fe_thrusters.main"))
                    .icon(() -> new ItemStack(AftItems.ELECTRIC_THRUSTER.get()))
                    .displayItems((parameters, output) -> output.accept(AftItems.ELECTRIC_THRUSTER.get()))
                    .build());

    /**
     * Attaches the deferred register to NeoForge's MOD event bus.
     *
     * @param eventBus MOD event bus
     */
    public static void register(final IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
