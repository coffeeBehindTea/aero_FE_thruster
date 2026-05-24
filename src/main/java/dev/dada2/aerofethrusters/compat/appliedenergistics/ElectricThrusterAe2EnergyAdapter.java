package dev.dada2.aerofethrusters.compat.appliedenergistics;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.events.GridPowerStorageStateChanged;
import appeng.api.networking.events.GridPowerStorageStateChanged.PowerEventType;
import appeng.api.util.AECableType;
import dev.dada2.aerofethrusters.content.ElectricThrusterBlockEntity;
import dev.dada2.aerofethrusters.registry.AftItems;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * Bridges one FE-powered thruster to an Applied Energistics 2 ME network.
 *
 * <p>AE2 cables see this object as an in-world grid node host. The node also
 * registers an {@link IAEPowerStorage} service, allowing the ME network to push
 * AE into the thruster's FE buffer or pull stored FE back out as AE.</p>
 */
final class ElectricThrusterAe2EnergyAdapter
        implements IInWorldGridNodeHost, IAEPowerStorage, IGridNodeListener<ElectricThrusterAe2EnergyAdapter> {
    private static final String NODE_TAG_NAME = "ae2_grid_node";

    private final ElectricThrusterBlockEntity thruster;
    private final IManagedGridNode node;
    private boolean created;
    private boolean chargingFromNetwork;
    private int lastKnownEnergy;

    /**
     * Creates an AE2 managed grid node bound to the thruster.
     *
     * @param thruster block entity whose FE buffer backs the AE power storage
     */
    ElectricThrusterAe2EnergyAdapter(final ElectricThrusterBlockEntity thruster) {
        this.thruster = thruster;
        this.lastKnownEnergy = thruster.getEnergyStored();
        this.node = GridHelper.createManagedNode(this, this)
                .setInWorldNode(true)
                .setTagName(NODE_TAG_NAME)
                .setIdlePowerUsage(0)
                .setVisualRepresentation(AftItems.ELECTRIC_THRUSTER.get())
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .addService(IAEPowerStorage.class, this);
    }

    /**
     * Creates the AE2 node once the block entity has a server level and position.
     */
    void tick() {
        final Level level = this.thruster.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }

        if (!this.created) {
            this.node.create(level, this.thruster.getBlockPos());
            this.created = true;
            this.lastKnownEnergy = this.thruster.getEnergyStored();
        }

        this.chargeFromNetwork();
    }

    /**
     * Saves the managed node's persistent data into the block entity tag.
     *
     * @param tag block entity save tag
     */
    void save(final CompoundTag tag) {
        this.node.saveToNBT(tag);
    }

    /**
     * Loads the managed node's persistent data from the block entity tag.
     *
     * @param tag block entity save tag
     */
    void load(final CompoundTag tag) {
        this.node.loadFromNBT(tag);
        this.lastKnownEnergy = this.thruster.getEnergyStored();
    }

    /**
     * Destroys the managed node so AE2 drops network connections cleanly.
     */
    void destroy() {
        this.node.destroy();
        this.created = false;
    }

    /**
     * Emits an AE2 power-storage event after the FE buffer changes.
     */
    void onInternalEnergyChanged() {
        final int currentEnergy = this.thruster.getEnergyStored();
        if (currentEnergy > this.lastKnownEnergy) {
            this.emitPowerStorageChange(PowerEventType.RECEIVE_POWER);
        } else if (currentEnergy < this.lastKnownEnergy) {
            this.emitPowerStorageChange(PowerEventType.PROVIDE_POWER);
        }
        this.lastKnownEnergy = currentEnergy;
    }

    /**
     * Returns the grid node exposed on the requested side.
     *
     * @param side side queried by an AE2 cable
     * @return managed grid node, or {@code null} before creation
     */
    @Override
    public IGridNode getGridNode(final Direction side) {
        return this.node.getNode();
    }

    /**
     * Uses normal covered-cable connectivity on every side.
     *
     * @param side side queried by AE2
     * @return covered cable connection type
     */
    @Override
    public AECableType getCableConnectionType(final Direction side) {
        return AECableType.COVERED;
    }

    /**
     * Inserts AE power into the thruster after converting it to FE.
     *
     * @param amount requested AE insertion amount
     * @param mode whether AE2 is simulating or mutating
     * @return leftover AE that did not fit into the FE buffer
     */
    @Override
    public double injectAEPower(final double amount, final Actionable mode) {
        if (amount <= 0 || Double.isNaN(amount)) {
            return 0;
        }

        final int requestedFe = aeToWholeFe(amount);
        if (requestedFe <= 0) {
            return amount;
        }

        final int acceptedFe = this.thruster.receiveEnergyFromAe2(requestedFe, mode.isSimulate());
        final double acceptedAe = wholeFeToAe(acceptedFe);
        return Math.max(0, amount - acceptedAe);
    }

    /**
     * Extracts stored FE for the AE2 network after converting the request to FE.
     *
     * @param amount requested AE extraction amount
     * @param mode whether AE2 is simulating or mutating
     * @param multiplier AE2 multiplier selected by the caller
     * @return AE actually extracted for the network
     */
    @Override
    public double extractAEPower(final double amount, final Actionable mode, final PowerMultiplier multiplier) {
        if (this.chargingFromNetwork || amount <= 0 || Double.isNaN(amount)) {
            return 0;
        }

        final double multipliedAe = multiplier.multiply(amount);
        final int requestedFe = aeToWholeFe(multipliedAe);
        if (requestedFe <= 0) {
            return 0;
        }

        final int extractedFe = this.thruster.extractEnergyForAe2(requestedFe, mode.isSimulate());
        return multiplier.divide(wholeFeToAe(extractedFe));
    }

    /**
     * Reports the FE buffer capacity in AE units.
     *
     * @return max storage converted from FE to AE
     */
    @Override
    public double getAEMaxPower() {
        return wholeFeToAe(this.thruster.getEnergyCapacity());
    }

    /**
     * Reports the current FE buffer amount in AE units.
     *
     * @return current storage converted from FE to AE
     */
    @Override
    public double getAECurrentPower() {
        return wholeFeToAe(this.thruster.getEnergyStored());
    }

    /**
     * Makes the storage visible to the shared ME energy service.
     *
     * @return true so the network may use this storage
     */
    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    /**
     * Allows both inserting AE into the thruster and extracting AE from it.
     *
     * @return read-write access for AE2 power flow
     */
    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ_WRITE;
    }

    /**
     * Marks the owning block entity dirty when AE2 changes node data.
     *
     * @param owner adapter instance that owns the node
     * @param node grid node requesting a save
     */
    @Override
    public void onSaveChanges(final ElectricThrusterAe2EnergyAdapter owner, final IGridNode node) {
        owner.thruster.setChanged();
    }

    /**
     * Posts a storage state change event to the connected ME grid.
     *
     * @param type whether the storage received or provided power
     */
    private void emitPowerStorageChange(final PowerEventType type) {
        this.node.ifPresent(grid -> grid.postEvent(new GridPowerStorageStateChanged(this, type)));
    }

    /**
     * Pulls AE from the connected ME network into the thruster's FE buffer.
     *
     * <p>The adapter is also an AE storage, so {@link #chargingFromNetwork}
     * temporarily disables this same storage as an extraction source. That
     * avoids spending time moving energy from the thruster back into itself
     * when AE2 resolves the network-wide extraction request.</p>
     */
    private void chargeFromNetwork() {
        final int missingFe = this.thruster.getEnergyCapacity() - this.thruster.getEnergyStored();
        if (missingFe <= 0) {
            return;
        }

        final int requestedFe = Math.min(ElectricThrusterBlockEntity.MAX_RECEIVE, missingFe);
        final double requestedAe = wholeFeToAe(requestedFe);
        if (requestedAe <= 0) {
            return;
        }

        this.node.ifPresent(grid -> {
            final IEnergyService energyService = grid.getEnergyService();
            double extractedAe = 0;
            this.chargingFromNetwork = true;
            try {
                extractedAe = energyService.extractAEPower(requestedAe, Actionable.MODULATE, PowerMultiplier.ONE);
            } finally {
                this.chargingFromNetwork = false;
            }

            final int extractedFe = aeToWholeFe(extractedAe);
            if (extractedFe > 0) {
                this.thruster.receiveEnergyFromAe2(extractedFe, false);
            }
        });
    }

    /**
     * Converts AE to a whole FE amount because the thruster buffer stores ints.
     *
     * @param ae AE amount requested by the network
     * @return whole FE amount clamped to int range
     */
    private static int aeToWholeFe(final double ae) {
        final double fe = PowerUnit.AE.convertTo(PowerUnit.FE, ae);
        if (fe <= 0 || Double.isNaN(fe)) {
            return 0;
        }
        return (int) Mth.clamp(Math.floor(fe), 0, Integer.MAX_VALUE);
    }

    /**
     * Converts whole FE from the thruster buffer into AE.
     *
     * @param fe stored FE amount
     * @return equivalent AE amount
     */
    private static double wholeFeToAe(final int fe) {
        if (fe <= 0) {
            return 0;
        }
        return PowerUnit.FE.convertTo(PowerUnit.AE, fe);
    }
}
