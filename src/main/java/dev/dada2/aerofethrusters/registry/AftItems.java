package dev.dada2.aerofethrusters.registry;

import dev.dada2.aerofethrusters.AeroFeThrusters;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Deferred registration for item instances.
 */
public class AftItems {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, AeroFeThrusters.MOD_ID);

    /** Block item used to place the electric thruster. */
    public static final DeferredHolder<Item, BlockItem> ELECTRIC_THRUSTER =
            ITEMS.register("electric_thruster",
                    () -> new BlockItem(AftBlocks.ELECTRIC_THRUSTER.get(), new Item.Properties()));

    /**
     * Attaches the deferred register to NeoForge's MOD event bus.
     *
     * @param eventBus MOD event bus
     */
    public static void register(final IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
