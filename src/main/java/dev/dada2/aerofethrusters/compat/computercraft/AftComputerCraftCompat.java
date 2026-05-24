package dev.dada2.aerofethrusters.compat.computercraft;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.dada2.aerofethrusters.registry.AftBlockEntityTypes;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Optional CC: Tweaked integration.
 *
 * <p>This class is only called when the {@code computercraft} mod is loaded, so
 * the main mod can still run without CC:T installed.</p>
 */
public class AftComputerCraftCompat {
    /**
     * Registers the electric thruster as a CC:T peripheral provider.
     *
     * @param event NeoForge capability registration event
     */
    public static void register(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                PeripheralCapability.get(),
                AftBlockEntityTypes.ELECTRIC_THRUSTER.get(),
                (blockEntity, side) -> new ElectricThrusterPeripheral(blockEntity));
    }
}
