package dev.dada2.aerofethrusters.registry;

import dev.dada2.aerofethrusters.compat.appliedenergistics.AftAppliedEnergisticsCompat;
import dev.dada2.aerofethrusters.compat.computercraft.AftComputerCraftCompat;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capability registration for block entities.
 */
public class AftCapabilities {
    /**
     * Registers FE energy capability plus optional CC:T and AE2 capabilities.
     *
     * @param event NeoForge capability registration event
     */
    public static void register(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                AftBlockEntityTypes.ELECTRIC_THRUSTER.get(),
                (blockEntity, side) -> blockEntity.getEnergyStorage());

        if (ModList.get().isLoaded("computercraft")) {
            AftComputerCraftCompat.register(event);
        }

        if (ModList.get().isLoaded("ae2")) {
            AftAppliedEnergisticsCompat.register(event);
        }
    }
}
