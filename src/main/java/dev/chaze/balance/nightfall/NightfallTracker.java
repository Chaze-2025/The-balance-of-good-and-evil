package dev.chaze.balance.nightfall;

import java.util.Objects;
import java.util.Optional;

/**
 * One world's disposable transition baseline. Call only on the server thread;
 * no clock, world data, player state, or transition history is persisted here.
 */
public final class NightfallTracker {
    private NightfallState lastObservation;

    /**
     * Records the supplied state and returns only a direct observed phase change.
     * Initialization and activation establish a baseline without a false transition.
     */
    public Optional<Transition> observe(NightfallState current) {
        Objects.requireNonNull(current, "current");
        Optional<Transition> transition = hasPhaseChangedSinceLastObservation(current)
                ? Optional.of(new Transition(lastObservation.phase().orElseThrow(),
                        current.phase().orElseThrow()))
                : Optional.empty();
        lastObservation = current;
        return transition;
    }

    /** A fresh-time query does not consume a later scheduled transition notification. */
    public boolean hasPhaseChangedSinceLastObservation(NightfallState current) {
        Objects.requireNonNull(current, "current");
        return lastObservation != null && lastObservation.enabled() && current.enabled()
                && lastObservation.phase().orElseThrow() != current.phase().orElseThrow();
    }

    public void clear() {
        lastObservation = null;
    }

    public record Transition(NightfallPhase previous, NightfallPhase current) {
        public Transition {
            Objects.requireNonNull(previous, "previous");
            Objects.requireNonNull(current, "current");
            if (previous == current) {
                throw new IllegalArgumentException("A phase transition must change the phase");
            }
        }
    }
}
