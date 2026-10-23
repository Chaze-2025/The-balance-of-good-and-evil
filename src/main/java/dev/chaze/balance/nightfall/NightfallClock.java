package dev.chaze.balance.nightfall;

import dev.chaze.balance.config.NightfallConfig;
import java.util.Objects;

/** Pure calculations over authoritative world time; never advances a separate timer. */
public final class NightfallClock {
    public static final int TICKS_PER_DAY = 24_000;

    private NightfallClock() {}

    /** Handles negative values and the full long range without overflow. */
    public static int timeOfDay(long absoluteTime) {
        return (int) Math.floorMod(absoluteTime, (long) TICKS_PER_DAY);
    }

    public static NightfallPhase phaseAt(long time, NightfallConfig config) {
        Objects.requireNonNull(config, "config");
        int dailyTime = timeOfDay(time);
        if (dailyTime < config.duskStart()) {
            return NightfallPhase.DAYLIGHT;
        }
        if (dailyTime < config.nightStart()) {
            return NightfallPhase.DUSK;
        }
        if (dailyTime < config.deepNightStart()) {
            return NightfallPhase.NIGHT;
        }
        if (dailyTime < config.dawnStart()) {
            return NightfallPhase.DEEP_NIGHT;
        }
        return NightfallPhase.DAWN;
    }

    /** This is a base time input, not a combined danger score or permission to bypass wards. */
    public static double intensity(NightfallPhase phase, NightfallConfig config) {
        Objects.requireNonNull(config, "config");
        return switch (Objects.requireNonNull(phase, "phase")) {
            case DAYLIGHT -> config.daylightIntensity();
            case DUSK -> config.duskIntensity();
            case NIGHT -> config.nightIntensity();
            case DEEP_NIGHT -> config.deepNightIntensity();
            case DAWN -> config.dawnIntensity();
        };
    }
}
