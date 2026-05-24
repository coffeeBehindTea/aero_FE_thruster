package dev.dada2.aerofethrusters.content;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.dada2.aerofethrusters.compat.appliedenergistics.AftAppliedEnergisticsCompat;
import dev.dada2.aerofethrusters.registry.AftBlockEntityTypes;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Stores the electric thruster's FE buffer, configuration, and Sable physics output.
 *
 * <p>The block entity is both a normal Create smart block entity and a Sable
 * propeller actor. Sable calls it during physics ticks, where the configured
 * thrust is reduced by redstone mode and available FE before being applied as a
 * point force.</p>
 */
public class ElectricThrusterBlockEntity extends SmartBlockEntity
        implements BlockEntitySubLevelPropellerActor, BlockEntityPropeller, IHaveGoggleInformation, MenuProvider {
    /** Maximum FE stored by one thruster. */
    public static final int ENERGY_CAPACITY = 1_000_000;
    /** Maximum FE accepted by one insertion call. */
    public static final int MAX_RECEIVE = 64_000;
    /** Highest configurable thrust in pN. */
    public static final int MAX_THRUST = 4096;
    /** Default configured thrust for newly placed thrusters. */
    public static final int DEFAULT_THRUST = 256;
    /** FE consumed per pN of target thrust per game tick. */
    public static final int FE_PER_THRUST_PER_TICK = 2;
    /** Legacy scroll-wheel step used when loading old saves. */
    private static final int OLD_DEFAULT_THRUST_STEP = 64;

    private final ThrusterEnergyStorage energy = new ThrusterEnergyStorage(
            ENERGY_CAPACITY, MAX_RECEIVE, this::onEnergyChanged);
    private int maxThrust = DEFAULT_THRUST;
    private RedstoneControlMode redstoneMode = RedstoneControlMode.IGNORE;
    private double currentThrust;
    private double energyCostRemainder;
    private int syncTicks;
    private Object ae2EnergyAdapter;

    /**
     * Creates a thruster block entity at the given position.
     *
     * @param pos world position of the block entity
     * @param state current block state, including facing and lit properties
     */
    public ElectricThrusterBlockEntity(final BlockPos pos, final BlockState state) {
        super(AftBlockEntityTypes.ELECTRIC_THRUSTER.get(), pos, state);
        this.setLazyTickRate(20);
    }

    /**
     * Registers Create behaviours for the block entity.
     *
     * <p>No Create behaviours are needed currently; FE is exposed through a
     * NeoForge capability and configuration is handled by a menu.</p>
     *
     * @param behaviours mutable Create behaviour list
     */
    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
    }

    /**
     * Runs normal server-side maintenance outside Sable's physics step.
     *
     * <p>This clears stale thrust when the block is no longer part of a Sable
     * sub-level and periodically syncs state to clients for tooltips and UI.</p>
     */
    @Override
    public void tick() {
        super.tick();

        if (this.level == null || this.level.isClientSide) {
            return;
        }

        this.tickAe2EnergyAdapter();

        if (Sable.HELPER.getContaining(this.level, this.worldPosition) == null && this.currentThrust != 0) {
            this.currentThrust = 0;
            this.updateLitState();
            this.sendData();
        }

        if (++this.syncTicks >= 10) {
            this.syncTicks = 0;
            this.sendData();
        }
    }

    /**
     * Called by Sable while the containing physics structure is being simulated.
     *
     * @param subLevel physics structure containing the thruster
     * @param handle rigid body handle used to apply impulses
     * @param timeStep physics step length in seconds
     */
    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        this.updateThrustForPhysics(timeStep);
        if (this.currentThrust > 0) {
            BlockEntitySubLevelPropellerActor.super.sable$physicsTick(subLevel, handle, timeStep);
        }
    }

    /**
     * Converts target thrust into actual thrust according to FE that can be paid.
     *
     * <p>The method keeps a fractional FE remainder so variable physics time
     * steps do not lose energy cost precision.</p>
     *
     * @param timeStep physics step length in seconds
     */
    private void updateThrustForPhysics(final double timeStep) {
        final double targetThrust = this.getTargetThrustForDisplay();
        if (targetThrust <= 0) {
            this.currentThrust = 0;
            this.updateLitState();
            return;
        }

        final double tickScale = Math.max(0, timeStep * 20.0);
        final double exactCost = targetThrust * FE_PER_THRUST_PER_TICK * tickScale + this.energyCostRemainder;
        final int cost = (int) Math.floor(exactCost);
        this.energyCostRemainder = exactCost - cost;

        if (cost <= 0) {
            this.currentThrust = this.energy.getEnergyStored() > 0 ? targetThrust : 0;
            this.updateLitState();
            return;
        }

        final int paid = this.energy.consumeEnergy(cost);
        this.currentThrust = targetThrust * (paid / (double) cost);
        this.updateLitState();
    }

    /**
     * Computes target thrust after redstone control.
     *
     * @return thrust requested before FE shortage is applied
     */
    public double getTargetThrustForDisplay() {
        return this.redstoneMode.apply(this.maxThrust, this.getRedstoneSignal());
    }

    /**
     * Reads the strongest neighboring redstone signal.
     *
     * @return signal clamped to vanilla range {@code 0..15}
     */
    public int getRedstoneSignal() {
        if (this.level == null) {
            return 0;
        }
        return Mth.clamp(this.level.getBestNeighborSignal(this.worldPosition), 0, 15);
    }

    /** Marks the block entity dirty after energy changes. */
    private void onEnergyChanged() {
        this.setChanged();
        this.notifyAe2EnergyChanged();
    }

    /**
     * Exposes the receive-only FE storage for the NeoForge energy capability.
     *
     * @return internal energy storage
     */
    public IEnergyStorage getEnergyStorage() {
        return this.energy;
    }

    /**
     * Lazily creates the optional AE2 network adapter.
     *
     * <p>The return type is {@code Object} so the main block entity does not
     * expose AE2 API types from its public signature. The compat package casts
     * it back to the AE2 interfaces after confirming the {@code ae2} mod is
     * loaded.</p>
     *
     * @return AE2 adapter instance, or {@code null} when AE2 is not loaded
     */
    public Object getOrCreateAe2EnergyAdapter() {
        if (this.ae2EnergyAdapter == null && ModList.get().isLoaded("ae2")) {
            this.ae2EnergyAdapter = AftAppliedEnergisticsCompat.createEnergyAdapter(this);
        }
        return this.ae2EnergyAdapter;
    }

    /**
     * Accepts FE converted from AE power through an AE2 network.
     *
     * @param maxReceive requested FE insertion amount after unit conversion
     * @param simulate when true, calculates without mutating the buffer
     * @return FE accepted by the internal buffer
     */
    public int receiveEnergyFromAe2(final int maxReceive, final boolean simulate) {
        return this.energy.receiveEnergy(maxReceive, simulate);
    }

    /**
     * Extracts FE for an AE2 network while keeping normal NeoForge extraction disabled.
     *
     * @param maxExtract requested FE extraction amount before conversion to AE
     * @param simulate when true, calculates without mutating the buffer
     * @return FE removed from the internal buffer
     */
    public int extractEnergyForAe2(final int maxExtract, final boolean simulate) {
        return this.energy.extractEnergyForAe2(maxExtract, simulate);
    }

    /** @return configured maximum thrust in pN */
    public int getConfiguredMaxThrust() {
        return this.maxThrust;
    }

    /** @return current redstone control mode */
    public RedstoneControlMode getRedstoneMode() {
        return this.redstoneMode;
    }

    /** @return stored FE */
    public int getEnergyStored() {
        return this.energy.getEnergyStored();
    }

    /** @return maximum FE capacity */
    public int getEnergyCapacity() {
        return this.energy.getMaxEnergyStored();
    }

    /** @return actual thrust from the latest physics tick */
    public double getCurrentThrustForDisplay() {
        return this.currentThrust;
    }

    /** @return whether the internal buffer contains any FE */
    public boolean hasEnergy() {
        return this.energy.getEnergyStored() > 0;
    }

    /**
     * Applies settings from the UI or CC:T peripheral.
     *
     * @param maxThrust requested max thrust, clamped to {@code 0..MAX_THRUST}
     * @param redstoneMode requested redstone mode, or {@code null} for ignore mode
     */
    public void applySettings(final int maxThrust, final RedstoneControlMode redstoneMode) {
        this.maxThrust = Mth.clamp(maxThrust, 0, MAX_THRUST);
        this.redstoneMode = redstoneMode == null ? RedstoneControlMode.IGNORE : redstoneMode;
        this.setChanged();
        this.sendData();
    }

    /**
     * Opens the configuration menu for a server-side player.
     *
     * @param player player opening the menu
     */
    public void openMenu(final ServerPlayer player) {
        player.openMenu(this, this::writeMenuData);
    }

    /**
     * Writes initial menu values to the opening packet.
     *
     * @param buffer menu data buffer
     */
    public void writeMenuData(final RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.worldPosition);
        buffer.writeVarInt(this.maxThrust);
        buffer.writeVarInt(this.redstoneMode.id());
    }

    /** @return localized title displayed by the configuration menu */
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.aero_fe_thrusters.electric_thruster");
    }

    /**
     * Creates a server-side menu bound directly to this block entity.
     *
     * @param containerId vanilla container id
     * @param inventory player inventory
     * @param player player opening the menu
     * @return thruster configuration menu
     */
    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
        return new ElectricThrusterMenu(containerId, inventory, this);
    }

    /** @return this object as Sable's propeller interface */
    @Override
    public BlockEntityPropeller getPropeller() {
        return this;
    }

    /** @return block facing used as the thrust axis */
    @Override
    public Direction getBlockDirection() {
        return this.getBlockState().getValue(ElectricThrusterBlock.FACING);
    }

    /**
     * Reports passive airflow for Sable's propeller model.
     *
     * @return zero because this thruster produces only powered thrust
     */
    @Override
    public double getAirflow() {
        return 0;
    }

    /** @return current actual thrust used by Sable */
    @Override
    public double getThrust() {
        return this.currentThrust;
    }

    /**
     * Applies the sign convention expected by Sable's propeller actor.
     *
     * @return negated thrust so force points out of the nozzle
     */
    @Override
    public double getScaledThrust() {
        return -this.getThrust();
    }

    /** @return whether this thruster should apply force this physics tick */
    @Override
    public boolean isActive() {
        return this.currentThrust > 0;
    }

    /**
     * Mirrors actual thrust output to the block state's {@code lit} property.
     *
     * <p>The property selects the lit redstone lamp nozzle texture and enables
     * block light level 15. State changes are skipped when the value is already
     * correct to avoid extra block updates during physics ticks.</p>
     */
    private void updateLitState() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        final BlockState state = this.getBlockState();
        if (!state.hasProperty(ElectricThrusterBlock.LIT)) {
            return;
        }

        final boolean lit = this.currentThrust > 0;
        if (state.getValue(ElectricThrusterBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition, state.setValue(ElectricThrusterBlock.LIT, lit),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Saves persistent settings, FE, and the last visible thrust state.
     *
     * @param tag destination NBT tag
     * @param registries registry lookup supplied by Minecraft
     * @param clientPacket whether this write is for network sync instead of disk
     */
    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", this.energy.getEnergyStored());
        tag.putInt("MaxThrust", this.maxThrust);
        tag.putInt("RedstoneMode", this.redstoneMode.id());
        tag.putDouble("CurrentThrust", this.currentThrust);
        tag.putDouble("EnergyCostRemainder", this.energyCostRemainder);
        if (!clientPacket) {
            this.saveAe2EnergyAdapter(tag);
        }
    }

    /**
     * Loads persistent settings and FE from NBT.
     *
     * @param tag source NBT tag
     * @param registries registry lookup supplied by Minecraft
     * @param clientPacket whether this read came from network sync instead of disk
     */
    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.energy.setEnergy(tag.getInt("Energy"));
        this.maxThrust = readMaxThrust(tag);
        this.redstoneMode = RedstoneControlMode.byId(tag.getInt("RedstoneMode"));
        this.currentThrust = tag.getDouble("CurrentThrust");
        this.energyCostRemainder = tag.getDouble("EnergyCostRemainder");
        if (!clientPacket) {
            this.loadAe2EnergyAdapter(tag);
        }
    }

    /**
     * Releases the optional AE2 grid node when the containing chunk unloads.
     */
    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.destroyAe2EnergyAdapter();
    }

    /**
     * Releases external integrations when Create invalidates this block entity.
     */
    @Override
    public void invalidate() {
        super.invalidate();
        this.destroyAe2EnergyAdapter();
    }

    /**
     * Releases the optional AE2 grid node when the block is actually removed.
     */
    @Override
    public void remove() {
        super.remove();
        this.destroyAe2EnergyAdapter();
    }

    /**
     * Advances AE2 node creation after the block entity has a server level.
     */
    private void tickAe2EnergyAdapter() {
        if (!ModList.get().isLoaded("ae2")) {
            return;
        }

        final Object adapter = this.getOrCreateAe2EnergyAdapter();
        if (adapter != null) {
            AftAppliedEnergisticsCompat.tickEnergyAdapter(adapter);
        }
    }

    /**
     * Stores the AE2 managed node's persistent network data.
     *
     * @param tag block entity save tag
     */
    private void saveAe2EnergyAdapter(final CompoundTag tag) {
        if (this.ae2EnergyAdapter != null && ModList.get().isLoaded("ae2")) {
            AftAppliedEnergisticsCompat.saveEnergyAdapter(this.ae2EnergyAdapter, tag);
        }
    }

    /**
     * Restores the AE2 managed node's persistent network data.
     *
     * @param tag block entity save tag
     */
    private void loadAe2EnergyAdapter(final CompoundTag tag) {
        if (ModList.get().isLoaded("ae2")) {
            final Object adapter = this.getOrCreateAe2EnergyAdapter();
            if (adapter != null) {
                AftAppliedEnergisticsCompat.loadEnergyAdapter(adapter, tag);
            }
        }
    }

    /**
     * Notifies AE2 that stored energy changed so the ME energy service can rescan it.
     */
    private void notifyAe2EnergyChanged() {
        if (this.ae2EnergyAdapter != null && ModList.get().isLoaded("ae2")) {
            AftAppliedEnergisticsCompat.onEnergyChanged(this.ae2EnergyAdapter);
        }
    }

    /**
     * Destroys the AE2 managed node and clears the cached adapter reference.
     */
    private void destroyAe2EnergyAdapter() {
        if (this.ae2EnergyAdapter != null && ModList.get().isLoaded("ae2")) {
            AftAppliedEnergisticsCompat.destroyEnergyAdapter(this.ae2EnergyAdapter);
        }
        this.ae2EnergyAdapter = null;
    }

    /**
     * Reads max thrust from current or legacy save keys.
     *
     * @param tag block entity save data
     * @return max thrust clamped to the supported range
     */
    private static int readMaxThrust(final CompoundTag tag) {
        if (tag.contains("MaxThrust")) {
            return Mth.clamp(tag.getInt("MaxThrust"), 0, MAX_THRUST);
        }

        if (tag.contains("ScrollValue")) {
            final int oldStep = tag.contains("ThrustStep")
                    ? Mth.clamp(tag.getInt("ThrustStep"), 1, 128)
                    : OLD_DEFAULT_THRUST_STEP;
            return Mth.clamp(tag.getInt("ScrollValue") * oldStep, 0, MAX_THRUST);
        }

        return DEFAULT_THRUST;
    }

    /**
     * Adds Create goggle tooltip lines describing current thruster state.
     *
     * @param tooltip mutable tooltip list
     * @param isPlayerSneaking whether the player is sneaking
     * @return true because lines were added
     */
    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("block.aero_fe_thrusters.electric_thruster")
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("aero_fe_thrusters.tooltip.max_thrust",
                this.getConfiguredMaxThrust()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("aero_fe_thrusters.tooltip.redstone_mode",
                this.redstoneMode.translation()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("aero_fe_thrusters.tooltip.current_thrust",
                String.format("%.1f", this.getCurrentThrustForDisplay())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("aero_fe_thrusters.tooltip.energy",
                this.energy.getEnergyStored(), this.energy.getMaxEnergyStored()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("aero_fe_thrusters.tooltip.cost",
                FE_PER_THRUST_PER_TICK).withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }

    /**
     * Looks up a thruster block entity in the player's current level.
     *
     * @param player player whose level should be searched
     * @param pos block position to check
     * @return thruster at the position, or {@code null}
     */
    public static ElectricThrusterBlockEntity getAt(final Player player, final BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof ElectricThrusterBlockEntity thruster) {
            return thruster;
        }
        return null;
    }

    /**
     * Looks up a loaded server-side thruster block entity.
     *
     * @param player server player whose level should be searched
     * @param pos block position to check
     * @return loaded thruster at the position, or {@code null}
     */
    public static ElectricThrusterBlockEntity getAtServer(final ServerPlayer player, final BlockPos pos) {
        if (!player.serverLevel().isLoaded(pos)) {
            return null;
        }

        final BlockEntity blockEntity = player.serverLevel().getBlockEntity(pos);
        return blockEntity instanceof ElectricThrusterBlockEntity thruster ? thruster : null;
    }

    /**
     * Receive-only FE buffer used as the thruster's fuel tank.
     *
     * <p>External machines may insert FE, while only the block entity itself can
     * consume energy for thrust.</p>
     */
    private static class ThrusterEnergyStorage implements IEnergyStorage {
        private final int capacity;
        private final int maxReceive;
        private final Runnable changed;
        private int energy;

        /**
         * Creates a bounded FE storage.
         *
         * @param capacity maximum stored FE
         * @param maxReceive maximum accepted FE per insertion call
         * @param changed callback fired when stored energy changes
         */
        private ThrusterEnergyStorage(final int capacity, final int maxReceive, final Runnable changed) {
            this.capacity = capacity;
            this.maxReceive = maxReceive;
            this.changed = changed;
        }

        /**
         * Inserts FE into the internal buffer.
         *
         * @param maxReceive requested insertion amount
         * @param simulate when true, calculates without mutating state
         * @return FE accepted by the buffer
         */
        @Override
        public int receiveEnergy(final int maxReceive, final boolean simulate) {
            if (maxReceive <= 0) {
                return 0;
            }
            final int received = Math.min(this.capacity - this.energy, Math.min(this.maxReceive, maxReceive));
            if (!simulate && received > 0) {
                this.energy += received;
                this.changed.run();
            }
            return received;
        }

        /**
         * Rejects external extraction.
         *
         * @param maxExtract requested extraction amount
         * @param simulate ignored because extraction is disabled
         * @return always zero
         */
        @Override
        public int extractEnergy(final int maxExtract, final boolean simulate) {
            return 0;
        }

        /**
         * Consumes FE internally for propulsion.
         *
         * @param requested desired FE amount
         * @return amount actually consumed
         */
        private int consumeEnergy(final int requested) {
            if (requested <= 0) {
                return 0;
            }
            final int consumed = Math.min(this.energy, requested);
            if (consumed > 0) {
                this.energy -= consumed;
                this.changed.run();
            }
            return consumed;
        }

        /**
         * Extracts FE for AE2 public power storage integration.
         *
         * <p>The regular {@link #extractEnergy(int, boolean)} method still
         * returns zero so generic FE machines cannot drain the thruster. AE2 is
         * allowed to use this explicit bridge method because the user requested
         * bidirectional ME energy support.</p>
         *
         * @param requested desired FE amount
         * @param simulate when true, calculates without mutating state
         * @return amount that would be or was extracted
         */
        private int extractEnergyForAe2(final int requested, final boolean simulate) {
            if (requested <= 0) {
                return 0;
            }
            final int extracted = Math.min(this.energy, requested);
            if (!simulate && extracted > 0) {
                this.energy -= extracted;
                this.changed.run();
            }
            return extracted;
        }

        /**
         * Restores stored FE from NBT.
         *
         * @param energy saved FE amount
         */
        private void setEnergy(final int energy) {
            this.energy = Mth.clamp(energy, 0, this.capacity);
        }

        /** @return currently stored FE */
        @Override
        public int getEnergyStored() {
            return this.energy;
        }

        /** @return maximum FE capacity */
        @Override
        public int getMaxEnergyStored() {
            return this.capacity;
        }

        /** @return false because external extraction is disabled */
        @Override
        public boolean canExtract() {
            return false;
        }

        /** @return true because external insertion is supported */
        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
