package dev.dada2.aerofethrusters.content;

import dev.dada2.aerofethrusters.registry.AftBlocks;
import dev.dada2.aerofethrusters.registry.AftMenus;
import dev.dada2.aerofethrusters.config.AftConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Menu backing the electric thruster configuration screen.
 *
 * <p>The client-side constructor receives a serialized position and values. The
 * server-side constructor stores a direct block entity reference so packets can
 * apply settings without trusting only a world position.</p>
 */
public class ElectricThrusterMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final BlockPos blockPos;
    private final ElectricThrusterBlockEntity thruster;
    private double maxThrust;
    private double maxAllowedThrust;
    private RedstoneControlMode redstoneMode;

    /**
     * Creates the client-side menu from network payload data.
     *
     * @param containerId vanilla container id
     * @param inventory player inventory
     * @param buffer menu opening payload sent by the block entity
     */
    public ElectricThrusterMenu(final int containerId, final Inventory inventory,
                                final RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), buffer.readDouble(),
                RedstoneControlMode.byId(buffer.readVarInt()), buffer.readDouble());
    }

    /**
     * Creates a menu snapshot without a direct server block entity reference.
     *
     * @param containerId vanilla container id
     * @param inventory player inventory
     * @param blockPos thruster position
     * @param maxThrust initial max thrust in pN
     * @param redstoneMode initial redstone mode
     * @param maxAllowedThrust server-configured upper limit in pN
     */
    public ElectricThrusterMenu(final int containerId, final Inventory inventory,
                                final BlockPos blockPos, final double maxThrust,
                                final RedstoneControlMode redstoneMode,
                                final double maxAllowedThrust) {
        super(AftMenus.ELECTRIC_THRUSTER.get(), containerId);
        this.access = ContainerLevelAccess.create(inventory.player.level(), blockPos);
        this.blockPos = blockPos;
        this.thruster = null;
        this.maxThrust = maxThrust;
        this.maxAllowedThrust = maxAllowedThrust;
        this.redstoneMode = redstoneMode;
    }

    /**
     * Creates the authoritative server-side menu.
     *
     * @param containerId vanilla container id
     * @param inventory player inventory
     * @param thruster block entity controlled by this menu
     */
    public ElectricThrusterMenu(final int containerId, final Inventory inventory,
                                final ElectricThrusterBlockEntity thruster) {
        super(AftMenus.ELECTRIC_THRUSTER.get(), containerId);
        this.access = ContainerLevelAccess.create(inventory.player.level(), thruster.getBlockPos());
        this.blockPos = thruster.getBlockPos();
        this.thruster = thruster;
        this.maxThrust = thruster.getConfiguredMaxThrust();
        this.maxAllowedThrust = AftConfigs.maxConfigurableThrust();
        this.redstoneMode = thruster.getRedstoneMode();
    }

    /**
     * Disables shift-click transfer because this menu has no item slots.
     *
     * @param player player shift-clicking
     * @param index clicked slot index
     * @return empty stack because no transfer is possible
     */
    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return ItemStack.EMPTY;
    }

    /**
     * Keeps the menu open only while the player can still interact with the block.
     *
     * @param player player viewing the menu
     * @return true when the player remains close enough to the thruster
     */
    @Override
    public boolean stillValid(final Player player) {
        return stillValid(this.access, player, AftBlocks.ELECTRIC_THRUSTER.get());
    }

    /** @return position of the configured thruster */
    public BlockPos blockPos() {
        return this.blockPos;
    }

    /** @return current menu copy of max thrust */
    public double maxThrust() {
        return this.maxThrust;
    }

    /** @return server-configured maximum value accepted by the UI */
    public double maxAllowedThrust() {
        return this.maxAllowedThrust;
    }

    /** @return current menu copy of redstone mode */
    public RedstoneControlMode redstoneMode() {
        return this.redstoneMode;
    }

    /**
     * Updates the client-side menu snapshot immediately after UI input.
     *
     * @param maxThrust new max thrust shown by the screen
     * @param redstoneMode new redstone mode shown by the screen
     */
    public void applyClientSettings(final double maxThrust, final RedstoneControlMode redstoneMode) {
        this.maxThrust = maxThrust;
        this.redstoneMode = redstoneMode;
    }

    /**
     * Applies a client packet to the server-side block entity if it matches this menu.
     *
     * @param pos position echoed by the client packet
     * @param maxThrust requested max thrust in pN
     * @param redstoneMode requested redstone mode
     * @return true when the settings were applied
     */
    public boolean applyServerSettings(final BlockPos pos, final double maxThrust,
                                       final RedstoneControlMode redstoneMode) {
        if (!this.blockPos.equals(pos) || this.thruster == null || this.thruster.isRemoved()) {
            return false;
        }

        this.thruster.applySettings(maxThrust, redstoneMode);
        this.maxThrust = this.thruster.getConfiguredMaxThrust();
        this.maxAllowedThrust = AftConfigs.maxConfigurableThrust();
        this.redstoneMode = this.thruster.getRedstoneMode();
        return true;
    }
}
