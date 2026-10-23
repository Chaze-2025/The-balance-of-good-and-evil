package dev.chaze.balance.nightfall;

import dev.chaze.balance.config.NightfallConfig;
import java.util.Objects;
import java.util.Optional;

/** An immutable observation of authoritative time, not a separately advancing clock. */
public record NightfallState(int timeOfDay, Optional<NightfallPhase> phase,
                            double baseIntensity, boolean enabled) {
    public NightfallState {
        Objects.requireNonNull(phase, "phase");
        if (timeOfDay < 0 || timeOfDay >= 24_000) {
            throw new IllegalArgumentException("Time of day must be between 0 and 23999");
        }
        if (enabled != phase.isPresent()) {
            throw new IllegalArgumentException("An enabled Nightfall state must have a phase; an inactive state must not");
        }
        if (!Double.isFinite(baseIntensity) || baseIntensity < 0.0 || baseIntensity > 1.0) {
            throw new IllegalArgumentException("Base intensity must be finite and between 0 and 1");
        }
        if (!enabled && baseIntensity != 0.0) {
            throw new IllegalArgumentException("An inactive Nightfall state must have zero base intensity");
        }
    }

    /** Unsupported dimensions deliberately have no Overworld-style phase. */
    public static NightfallState from(long authoritativeTime, NightfallConfig config,
                                     boolean supportedDimension) {
        Objects.requireNonNull(config, "config");
        int timeOfDay = NightfallClock.timeOfDay(authoritativeTime);
        if (!config.enabled() || !supportedDimension) {
            return new NightfallState(timeOfDay, Optional.empty(), 0.0, false);
        }
        NightfallPhase phase = NightfallClock.phaseAt(authoritativeTime, config);
        return new NightfallState(timeOfDay, Optional.of(phase),
                NightfallClock.intensity(phase, config), true);
    }

    /** Phase classification only; configured intensity is a separate balancing input. */
    public boolean isElevatedNighttimeDanger() {
        return enabled && phase.orElseThrow().isNighttime();
    }
}
