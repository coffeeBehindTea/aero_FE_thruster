package dev.dada2.aerofethrusters;

import dev.dada2.aerofethrusters.config.AftConfigs;
import dev.dada2.aerofethrusters.registry.AftBlockEntityTypes;
import dev.dada2.aerofethrusters.registry.AftBlocks;
import dev.dada2.aerofethrusters.registry.AftCapabilities;
import dev.dada2.aerofethrusters.registry.AftCreativeTabs;
import dev.dada2.aerofethrusters.registry.AftItems;
import dev.dada2.aerofethrusters.registry.AftMenus;
import dev.dada2.aerofethrusters.registry.AftPackets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;

/**
 * Main NeoForge mod entry point.
 *
 * <p>The constructor wires all deferred registers and MOD-bus listeners used by
 * the addon. Runtime logic lives in the registered block entity, menu, packets,
 * and optional compatibility classes.</p>
 */
@Mod(AeroFeThrusters.MOD_ID)
public class AeroFeThrusters {
    /** Stable mod id used by resources, registries, and packets. */
    public static final String MOD_ID = "aero_fe_thrusters";

    /**
     * Registers all mod content and MOD-bus event listeners.
     *
     * @param modEventBus NeoForge MOD event bus for this mod container
     */
    public AeroFeThrusters(final IEventBus modEventBus) {
        AftConfigs.register(ModLoadingContext.get().getActiveContainer());

        AftBlocks.register(modEventBus);
        AftItems.register(modEventBus);
        AftBlockEntityTypes.register(modEventBus);
        AftMenus.register(modEventBus);
        AftCreativeTabs.register(modEventBus);

        modEventBus.addListener(AftCapabilities::register);
        modEventBus.addListener(AftPackets::register);
    }
}
