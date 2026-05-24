package dev.dada2.aerofethrusters.compat.computercraft;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.dada2.aerofethrusters.config.AftConfigs;
import dev.dada2.aerofethrusters.content.ElectricThrusterBlockEntity;
import dev.dada2.aerofethrusters.content.RedstoneControlMode;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.chat.Component;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CC: Tweaked peripheral exposed by an adjacent electric thruster.
 *
 * <p>The peripheral has two responsibilities: direct thruster control and Sable
 * structure telemetry. Methods are marked {@code mainThread = true} whenever
 * they touch world or block entity state.</p>
 */
public class ElectricThrusterPeripheral implements IPeripheral {
    private final ElectricThrusterBlockEntity thruster;

    /**
     * Wraps a thruster block entity as a CC:T peripheral.
     *
     * @param thruster block entity exposed to the computer
     */
    public ElectricThrusterPeripheral(final ElectricThrusterBlockEntity thruster) {
        this.thruster = thruster;
    }

    /** @return peripheral type string used by {@code peripheral.find} */
    @Override
    public String getType() {
        return "aero_fe_thruster";
    }

    /** @return underlying block entity for CC:T identity/debugging */
    @Override
    public Object getTarget() {
        return this.thruster;
    }

    /**
     * Compares two peripherals by block entity identity.
     *
     * @param other other peripheral instance
     * @return true when both wrappers expose the same thruster
     */
    @Override
    public boolean equals(final IPeripheral other) {
        return other instanceof ElectricThrusterPeripheral peripheral
                && peripheral.thruster == this.thruster;
    }

    /**
     * Sets configured max thrust.
     *
     * @param thrust requested max thrust in pN, with up to 4 decimal places
     * @return applied max thrust
     * @throws LuaException when thrust is outside the configured range
     */
    @LuaFunction(mainThread = true)
    public final double setThrust(final double thrust) throws LuaException {
        return this.setMaxThrust(thrust);
    }

    /**
     * Sets configured max thrust while preserving the redstone mode.
     *
     * @param thrust requested max thrust in pN, with up to 4 decimal places
     * @return applied max thrust
     * @throws LuaException when thrust is outside the configured range
     */
    @LuaFunction(mainThread = true)
    public final double setMaxThrust(final double thrust) throws LuaException {
        this.validateThrust(thrust);
        this.thruster.applySettings(thrust, this.thruster.getRedstoneMode());
        return this.thruster.getConfiguredMaxThrust();
    }

    /** @return configured max thrust in pN */
    @LuaFunction(mainThread = true)
    public final double getThrust() {
        return this.thruster.getConfiguredMaxThrust();
    }

    /** @return configured max thrust in pN */
    @LuaFunction(mainThread = true)
    public final double getMaxThrust() {
        return this.thruster.getConfiguredMaxThrust();
    }

    /** @return actual thrust from the latest physics tick */
    @LuaFunction(mainThread = true)
    public final double getCurrentThrust() {
        return this.thruster.getCurrentThrustForDisplay();
    }

    /** @return redstone-adjusted target thrust before FE shortage is applied */
    @LuaFunction(mainThread = true)
    public final double getTargetThrust() {
        return this.thruster.getTargetThrustForDisplay();
    }

    /** @return strongest neighboring redstone signal */
    @LuaFunction(mainThread = true)
    public final int getRedstoneSignal() {
        return this.thruster.getRedstoneSignal();
    }

    /**
     * Sets the redstone control mode by stable Lua name.
     *
     * @param modeName one of the names returned by {@link #getRedstoneModes()}
     * @return applied mode name
     * @throws LuaException when the name is unknown
     */
    @LuaFunction(mainThread = true)
    public final String setRedstoneMode(final String modeName) throws LuaException {
        final RedstoneControlMode mode = RedstoneControlMode.byLuaName(modeName);
        if (mode == null) {
            throw new LuaException("unknown redstone mode '" + modeName + "'");
        }

        this.thruster.applySettings(this.thruster.getConfiguredMaxThrust(), mode);
        return this.thruster.getRedstoneMode().luaName();
    }

    /** @return current redstone mode Lua name */
    @LuaFunction(mainThread = true)
    public final String getRedstoneMode() {
        return this.thruster.getRedstoneMode().luaName();
    }

    /** @return 1-indexed table of available redstone mode Lua names */
    @LuaFunction
    public final Map<Integer, String> getRedstoneModes() {
        final Map<Integer, String> modes = new LinkedHashMap<>();
        int index = 1;
        for (final RedstoneControlMode mode : RedstoneControlMode.values()) {
            modes.put(index++, mode.luaName());
        }
        return modes;
    }

    /** @return stored FE */
    @LuaFunction(mainThread = true)
    public final int getEnergy() {
        return this.thruster.getEnergyStored();
    }

    /** @return maximum FE capacity */
    @LuaFunction(mainThread = true)
    public final int getEnergyCapacity() {
        return this.thruster.getEnergyCapacity();
    }

    /** @return whether the thruster has any FE stored */
    @LuaFunction(mainThread = true)
    public final boolean hasEnergy() {
        return this.thruster.hasEnergy();
    }

    /** @return whether this thruster currently belongs to a Sable physics structure */
    @LuaFunction(mainThread = true)
    public final boolean hasStructure() {
        return this.findSubLevel() instanceof ServerSubLevel;
    }

    /**
     * Reads combined Sable structure telemetry.
     *
     * @return table with id, mass, center of mass, velocities, and force count
     * @throws LuaException when the thruster is not in a physics structure
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getStructureInfo() throws LuaException {
        final ServerSubLevel subLevel = this.requireSubLevel();
        final MassData massData = subLevel.getMassTracker();
        final Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", subLevel.getUniqueId().toString());
        info.put("runtimeId", subLevel.getRuntimeId());
        info.put("name", subLevel.getName());
        info.put("mass", massData.getMass());
        info.put("inverseMass", massData.getInverseMass());
        info.put("centerOfMass", vectorToLua(massData.getCenterOfMass()));
        info.put("linearVelocity", vectorToLua(subLevel.latestLinearVelocity));
        info.put("angularVelocity", vectorToLua(subLevel.latestAngularVelocity));
        info.put("forceTelemetryEnabled", subLevel.isTrackingIndividualQueuedForces());
        info.put("forceCount", countRecordedForces(subLevel));
        return info;
    }

    /**
     * Reads total mass of the containing Sable structure.
     *
     * @return structure mass
     * @throws LuaException when the thruster is not in a physics structure
     */
    @LuaFunction(mainThread = true)
    public final double getStructureMass() throws LuaException {
        return this.requireSubLevel().getMassTracker().getMass();
    }

    /**
     * Reads center of mass of the containing Sable structure.
     *
     * @return vector table with x/y/z
     * @throws LuaException when the thruster is not in a physics structure
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Double> getStructureCenterOfMass() throws LuaException {
        return vectorToLua(this.requireSubLevel().getMassTracker().getCenterOfMass());
    }

    /**
     * Reads latest linear velocity of the containing Sable structure.
     *
     * @return vector table with x/y/z
     * @throws LuaException when the thruster is not in a physics structure
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Double> getStructureLinearVelocity() throws LuaException {
        return vectorToLua(this.requireSubLevel().latestLinearVelocity);
    }

    /**
     * Reads latest angular velocity of the containing Sable structure.
     *
     * @return vector table with x/y/z
     * @throws LuaException when the thruster is not in a physics structure
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Double> getStructureAngularVelocity() throws LuaException {
        return vectorToLua(this.requireSubLevel().latestAngularVelocity);
    }

    /**
     * Enables Sable's individual point-force recording for this structure.
     *
     * @return true when tracking is enabled
     * @throws LuaException when the thruster is not in a physics structure
     */
    @LuaFunction(mainThread = true)
    public final boolean enableForceTelemetry() throws LuaException {
        final ServerSubLevel subLevel = this.requireSubLevel();
        subLevel.enableIndividualQueuedForcesTracking(true);
        return subLevel.isTrackingIndividualQueuedForces();
    }

    /**
     * Checks whether Sable point-force recording is enabled.
     *
     * @return tracking flag from the containing sub-level
     * @throws LuaException when the thruster is not in a physics structure
     */
    @LuaFunction(mainThread = true)
    public final boolean isForceTelemetryEnabled() throws LuaException {
        return this.requireSubLevel().isTrackingIndividualQueuedForces();
    }

    /**
     * Reads point forces grouped by Sable force group.
     *
     * <p>The method enables tracking automatically. The first call may return an
     * empty table until the next Sable physics tick records forces.</p>
     *
     * @return 1-indexed table of force group summaries
     * @throws LuaException when the thruster is not in a physics structure
     */
    @LuaFunction(mainThread = true)
    public final Map<Integer, Map<String, Object>> getForceGroups() throws LuaException {
        final ServerSubLevel subLevel = this.requireSubLevel();
        subLevel.enableIndividualQueuedForcesTracking(true);

        final Map<Integer, Map<String, Object>> groups = new LinkedHashMap<>();
        final Map<ForceGroup, QueuedForceGroup> queuedForceGroups = subLevel.getQueuedForceGroups();
        if (queuedForceGroups == null) {
            return groups;
        }

        int index = 1;
        for (final Map.Entry<ForceGroup, QueuedForceGroup> entry : queuedForceGroups.entrySet()) {
            final Map<String, Object> group = forceGroupToLua(entry.getKey(), entry.getValue(), subLevel);
            if (((Number) group.get("forceCount")).intValue() > 0) {
                groups.put(index++, group);
            }
        }
        return groups;
    }

    /**
     * Reads every recorded point force for the containing structure.
     *
     * @return 1-indexed table of force entries with group, point, vector, and magnitude
     * @throws LuaException when the thruster is not in a physics structure
     */
    @LuaFunction(mainThread = true)
    public final Map<Integer, Map<String, Object>> getForces() throws LuaException {
        final ServerSubLevel subLevel = this.requireSubLevel();
        subLevel.enableIndividualQueuedForcesTracking(true);

        final Map<Integer, Map<String, Object>> forces = new LinkedHashMap<>();
        final Map<ForceGroup, QueuedForceGroup> queuedForceGroups = subLevel.getQueuedForceGroups();
        if (queuedForceGroups == null) {
            return forces;
        }

        int index = 1;
        for (final Map.Entry<ForceGroup, QueuedForceGroup> entry : queuedForceGroups.entrySet()) {
            final ForceGroup group = entry.getKey();
            final QueuedForceGroup queuedGroup = entry.getValue();
            for (final QueuedForceGroup.PointForce pointForce : queuedGroup.getRecordedPointForces()) {
                final Map<String, Object> force = new LinkedHashMap<>();
                force.put("groupId", forceGroupId(group));
                force.put("groupName", componentText(group.name()));
                force.put("point", vectorToLua(pointForce.point()));
                force.put("force", vectorToLua(pointForce.force()));
                force.put("magnitude", pointForce.force().length());
                forces.put(index++, force);
            }
        }
        return forces;
    }

    /**
     * Reads a compact status table for the thruster itself.
     *
     * @return table containing thrust, redstone, and FE fields
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getInfo() {
        final Map<String, Object> info = new LinkedHashMap<>();
        info.put("maxThrust", this.thruster.getConfiguredMaxThrust());
        info.put("currentThrust", this.thruster.getCurrentThrustForDisplay());
        info.put("targetThrust", this.thruster.getTargetThrustForDisplay());
        info.put("redstoneMode", this.thruster.getRedstoneMode().luaName());
        info.put("redstoneSignal", this.thruster.getRedstoneSignal());
        info.put("energy", this.thruster.getEnergyStored());
        info.put("energyCapacity", this.thruster.getEnergyCapacity());
        return info;
    }

    /**
     * Validates CC:T thrust input.
     *
     * @param thrust requested max thrust in pN
     * @throws LuaException when thrust is outside the supported range
     */
    private void validateThrust(final double thrust) throws LuaException {
        if (!AftConfigs.isAllowedConfiguredThrust(thrust)) {
            throw new LuaException("thrust must be between 0 and "
                    + AftConfigs.formatThrust(AftConfigs.maxConfigurableThrust()));
        }
    }

    /** @return Sable sub-level containing the thruster, or {@code null} */
    private SubLevel findSubLevel() {
        return Sable.HELPER.getContaining(this.thruster);
    }

    /**
     * Finds the containing server-side Sable sub-level or throws a Lua error.
     *
     * @return containing server sub-level
     * @throws LuaException when the thruster is not in a physics structure
     */
    private ServerSubLevel requireSubLevel() throws LuaException {
        final SubLevel subLevel = this.findSubLevel();
        if (subLevel instanceof ServerSubLevel serverSubLevel) {
            return serverSubLevel;
        }
        throw new LuaException("thruster is not part of a physics structure");
    }

    /**
     * Counts individual point forces currently recorded by Sable.
     *
     * @param subLevel containing physics structure
     * @return total recorded point force count
     */
    private static int countRecordedForces(final ServerSubLevel subLevel) {
        final Map<ForceGroup, QueuedForceGroup> queuedForceGroups = subLevel.getQueuedForceGroups();
        if (queuedForceGroups == null) {
            return 0;
        }

        int count = 0;
        for (final QueuedForceGroup group : queuedForceGroups.values()) {
            count += group.getRecordedPointForces().size();
        }
        return count;
    }

    /**
     * Converts a Sable force group and its recorded point forces into a Lua table.
     *
     * @param forceGroup Sable force group metadata
     * @param queuedGroup recorded forces for that group
     * @param subLevel structure used for center-of-mass torque calculation
     * @return Lua-friendly group summary table
     */
    private static Map<String, Object> forceGroupToLua(
            final ForceGroup forceGroup,
            final QueuedForceGroup queuedGroup,
            final ServerSubLevel subLevel) {
        final Vector3d totalForce = new Vector3d();
        final Vector3d totalTorque = new Vector3d();
        final Vector3dc centerOfMass = subLevel.getMassTracker().getCenterOfMass();

        for (final QueuedForceGroup.PointForce pointForce : queuedGroup.getRecordedPointForces()) {
            totalForce.add(pointForce.force());
            totalTorque.add(new Vector3d(pointForce.point()).sub(centerOfMass).cross(pointForce.force()));
        }

        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", forceGroupId(forceGroup));
        group.put("name", componentText(forceGroup.name()));
        group.put("description", componentText(forceGroup.description()));
        group.put("color", forceGroup.color());
        group.put("defaultDisplayed", forceGroup.defaultDisplayed());
        group.put("forceCount", queuedGroup.getRecordedPointForces().size());
        group.put("totalForce", vectorToLua(totalForce));
        group.put("totalForceMagnitude", totalForce.length());
        group.put("totalTorque", vectorToLua(totalTorque));
        group.put("totalTorqueMagnitude", totalTorque.length());
        return group;
    }

    /**
     * Resolves the registry id of a Sable force group.
     *
     * @param forceGroup force group instance
     * @return registry id, or {@code unknown}
     */
    private static String forceGroupId(final ForceGroup forceGroup) {
        final var key = ForceGroups.REGISTRY.getKey(forceGroup);
        return key == null ? "unknown" : key.toString();
    }

    /**
     * Converts nullable Minecraft components into plain strings for Lua.
     *
     * @param component source component, possibly {@code null}
     * @return component string or empty string
     */
    private static String componentText(final Component component) {
        return component == null ? "" : component.getString();
    }

    /**
     * Converts a JOML vector into a Lua-friendly table.
     *
     * @param vector source vector
     * @return map containing x, y, and z
     */
    private static Map<String, Double> vectorToLua(final Vector3dc vector) {
        final Map<String, Double> result = new LinkedHashMap<>();
        result.put("x", vector.x());
        result.put("y", vector.y());
        result.put("z", vector.z());
        return result;
    }
}
