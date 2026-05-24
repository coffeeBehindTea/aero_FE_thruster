package dev.dada2.aerofethrusters.registry;

import dev.dada2.aerofethrusters.AeroFeThrusters;
import dev.dada2.aerofethrusters.content.ElectricThrusterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Deferred registration for menu types.
 */
public class AftMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AeroFeThrusters.MOD_ID);

    /** Menu type backing the electric thruster configuration screen. */
    public static final DeferredHolder<MenuType<?>, MenuType<ElectricThrusterMenu>> ELECTRIC_THRUSTER =
            MENUS.register("electric_thruster", () ->
                    IMenuTypeExtension.create(ElectricThrusterMenu::new));

    /**
     * Attaches the deferred register to NeoForge's MOD event bus.
     *
     * @param eventBus MOD event bus
     */
    public static void register(final IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
