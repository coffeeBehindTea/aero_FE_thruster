package dev.dada2.aerofethrusters.compat.appliedenergistics;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import dev.dada2.aerofethrusters.content.ElectricThrusterBlockEntity;
import dev.dada2.aerofethrusters.registry.AftBlockEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Optional Applied Energistics 2 integration entry point.
 *
 * <p>This class is only referenced after NeoForge confirms that the
 * {@code ae2} mod is loaded, keeping the base thruster usable in packs without
 * AE2 installed.</p>
 */
public final class AftAppliedEnergisticsCompat {
    private AftAppliedEnergisticsCompat() {
    }

    /**
     * Registers the thruster as an in-world AE2 grid node host.
     *
     * @param event NeoForge capability registration event
     */
    public static void register(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                AftBlockEntityTypes.ELECTRIC_THRUSTER.get(),
                (blockEntity, ignored) -> (IInWorldGridNodeHost) blockEntity.getOrCreateAe2EnergyAdapter());
    }

    /**
     * Creates the adapter that exposes one thruster to AE2 cables and networks.
     *
     * @param thruster block entity being wrapped
     * @return adapter stored by the block entity as an opaque object
     */
    public static Object createEnergyAdapter(final ElectricThrusterBlockEntity thruster) {
        return new ElectricThrusterAe2EnergyAdapter(thruster);
    }

    /**
     * Gives the adapter a server tick so it can create its managed grid node.
     *
     * @param adapter opaque adapter object owned by the block entity
     */
    public static void tickEnergyAdapter(final Object adapter) {
        ((ElectricThrusterAe2EnergyAdapter) adapter).tick();
    }

    /**
     * Saves AE2 node data into the block entity NBT.
     *
     * @param adapter opaque adapter object owned by the block entity
     * @param tag block entity save tag
     */
    public static void saveEnergyAdapter(final Object adapter, final CompoundTag tag) {
        ((ElectricThrusterAe2EnergyAdapter) adapter).save(tag);
    }

    /**
     * Loads AE2 node data from the block entity NBT.
     *
     * @param adapter opaque adapter object owned by the block entity
     * @param tag block entity save tag
     */
    public static void loadEnergyAdapter(final Object adapter, final CompoundTag tag) {
        ((ElectricThrusterAe2EnergyAdapter) adapter).load(tag);
    }

    /**
     * Notifies AE2 that the thruster's FE buffer changed.
     *
     * @param adapter opaque adapter object owned by the block entity
     */
    public static void onEnergyChanged(final Object adapter) {
        ((ElectricThrusterAe2EnergyAdapter) adapter).onInternalEnergyChanged();
    }

    /**
     * Destroys the AE2 managed node when the thruster unloads or is removed.
     *
     * @param adapter opaque adapter object owned by the block entity
     */
    public static void destroyEnergyAdapter(final Object adapter) {
        ((ElectricThrusterAe2EnergyAdapter) adapter).destroy();
    }
}
