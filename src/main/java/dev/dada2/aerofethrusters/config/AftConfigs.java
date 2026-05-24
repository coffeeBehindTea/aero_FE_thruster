package dev.dada2.aerofethrusters.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Server-side configuration values exposed through NeoForge and Create's config UI.
 */
public final class AftConfigs {
    /** Lowest allowed configured cap for a thruster's max thrust. */
    public static final double MIN_CONFIGURABLE_THRUST_CAP = 1.0;
    /** Highest allowed configured cap for a thruster's max thrust. */
    public static final double MAX_CONFIGURABLE_THRUST_CAP = 1.0E7;
    /** Default cap used to preserve the original 4096 pN behaviour. */
    public static final double DEFAULT_CONFIGURABLE_THRUST_CAP = 4096.0;
    private static final int THRUST_DECIMALS = 4;
    private static final double THRUST_SCALE = 10_000.0;

    private static final ModConfigSpec SERVER_SPEC;
    private static final ModConfigSpec.DoubleValue MAX_CONFIGURABLE_THRUST;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("thruster");
        MAX_CONFIGURABLE_THRUST = builder
                .comment(
                        "Maximum thrust that can be entered for one Electric Thruster, in pN.",
                        "Create's in-game config screen can edit this value.",
                        "Range: 1.0 to 1.0E7. Per-thruster UI values are rounded to 4 decimal places.")
                .defineInRange("maxConfigurableThrust",
                        DEFAULT_CONFIGURABLE_THRUST_CAP,
                        MIN_CONFIGURABLE_THRUST_CAP,
                        MAX_CONFIGURABLE_THRUST_CAP);
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private AftConfigs() {
    }

    /**
     * Registers this mod's server config so Create's config UI can discover it.
     *
     * @param modContainer active NeoForge mod container
     */
    public static void register(final ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }

    /**
     * Reads the configured per-thruster maximum thrust cap.
     *
     * @return configured cap clamped to the supported config range
     */
    public static double maxConfigurableThrust() {
        return clamp(MAX_CONFIGURABLE_THRUST.get(),
                MIN_CONFIGURABLE_THRUST_CAP,
                MAX_CONFIGURABLE_THRUST_CAP);
    }

    /**
     * Clamps and rounds a per-thruster setting to the allowed range.
     *
     * @param thrust requested thrust in pN
     * @return value clamped to {@code 0..maxConfigurableThrust()} and rounded to 4 decimals
     */
    public static double clampConfiguredThrust(final double thrust) {
        return roundThrust(clampFinite(thrust, 0, maxConfigurableThrust()));
    }

    /**
     * Rounds display and saved thrust values to four decimal places.
     *
     * @param thrust thrust in pN
     * @return rounded thrust in pN
     */
    public static double roundThrust(final double thrust) {
        if (!Double.isFinite(thrust)) {
            return 0;
        }
        return Math.round(thrust * THRUST_SCALE) / THRUST_SCALE;
    }

    /**
     * Formats thrust without trailing zeros while preserving up to 4 decimal places.
     *
     * @param thrust thrust in pN
     * @return human-readable decimal string
     */
    public static String formatThrust(final double thrust) {
        return BigDecimal.valueOf(roundThrust(thrust))
                .setScale(THRUST_DECIMALS, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    /**
     * Checks whether a parsed UI/CC:T value is within the current server cap.
     *
     * @param thrust requested thrust in pN
     * @return true when the value is finite and allowed
     */
    public static boolean isAllowedConfiguredThrust(final double thrust) {
        return Double.isFinite(thrust) && thrust >= 0 && thrust <= maxConfigurableThrust();
    }

    /**
     * Clamps a finite value.
     *
     * @param value value to clamp
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return clamped value
     */
    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Replaces non-finite values with the minimum before clamping.
     *
     * @param value value to clamp
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return finite clamped value
     */
    private static double clampFinite(final double value, final double min, final double max) {
        return Double.isFinite(value) ? clamp(value, min, max) : min;
    }
}
