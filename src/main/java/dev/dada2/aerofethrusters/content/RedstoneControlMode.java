package dev.dada2.aerofethrusters.content;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Redstone rules used to convert configured max thrust into target thrust.
 *
 * <p>Each mode has a stable numeric id for menu sync, a stable Lua name for
 * CC:T scripts, and a translation key for screen/tooltips.</p>
 */
public enum RedstoneControlMode {
    /** Ignores redstone and always requests the configured max thrust. */
    IGNORE(0, "ignore", "aero_fe_thrusters.redstone_mode.ignore") {
        @Override
        public double apply(final double maxThrust, final int signal) {
            return maxThrust;
        }
    },
    /** Requests thrust only while redstone signal is present. */
    WORK_WHEN_POWERED(1, "work_when_powered", "aero_fe_thrusters.redstone_mode.work_when_powered") {
        @Override
        public double apply(final double maxThrust, final int signal) {
            return signal > 0 ? maxThrust : 0;
        }
    },
    /** Requests thrust unless redstone signal is present. */
    STOP_WHEN_POWERED(2, "stop_when_powered", "aero_fe_thrusters.redstone_mode.stop_when_powered") {
        @Override
        public double apply(final double maxThrust, final int signal) {
            return signal > 0 ? 0 : maxThrust;
        }
    },
    /** Scales thrust linearly from 0/15 to 15/15 of max thrust. */
    PROPORTIONAL(3, "proportional", "aero_fe_thrusters.redstone_mode.proportional") {
        @Override
        public double apply(final double maxThrust, final int signal) {
            return maxThrust * (Mth.clamp(signal, 0, 15) / 15.0);
        }
    },
    /** Scales thrust inversely from 15/15 to 0/15 of max thrust. */
    INVERSE(4, "inverse", "aero_fe_thrusters.redstone_mode.inverse") {
        @Override
        public double apply(final double maxThrust, final int signal) {
            return maxThrust * ((15 - Mth.clamp(signal, 0, 15)) / 15.0);
        }
    };

    public static final int COUNT = values().length;
    private final int id;
    private final String luaName;
    private final String translationKey;

    RedstoneControlMode(final int id, final String luaName, final String translationKey) {
        this.id = id;
        this.luaName = luaName;
        this.translationKey = translationKey;
    }

    /**
     * Converts configured max thrust and redstone signal into target thrust.
     *
     * @param maxThrust configured max thrust in pN, with up to 4 decimal places
     * @param signal neighboring redstone signal strength
     * @return redstone-adjusted target thrust
     */
    public abstract double apply(double maxThrust, int signal);

    /** @return stable id used by menu packets and save data */
    public int id() {
        return this.id;
    }

    /** @return localized component for UI display */
    public Component translation() {
        return Component.translatable(this.translationKey);
    }

    /** @return stable CC:T name for this mode */
    public String luaName() {
        return this.luaName;
    }

    /** @return translation key for language files */
    public String translationKey() {
        return this.translationKey;
    }

    /**
     * Looks up a mode by saved/menu id.
     *
     * @param id stored mode id
     * @return matching mode, or {@link #IGNORE} when unknown
     */
    public static RedstoneControlMode byId(final int id) {
        for (final RedstoneControlMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return IGNORE;
    }

    /**
     * Looks up a mode by the CC:T-facing Lua name.
     *
     * @param name mode name from Lua
     * @return matching mode, or {@code null} when unknown
     */
    public static RedstoneControlMode byLuaName(final String name) {
        for (final RedstoneControlMode mode : values()) {
            if (mode.luaName.equals(name)) {
                return mode;
            }
        }
        return null;
    }
}
