package dev.chaze.balance.nightfall;

import static org.junit.jupiter.api.Assertions.*;

import dev.chaze.balance.config.NightfallConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Supplied snapshots model observed clock changes; these are not Minecraft sleep/tick tests. */
class NightfallTrackerTest {
    private static final NightfallConfig DEFAULTS = NightfallConfig.defaults();

    private static NightfallState at(long time) {
        return NightfallState.from(time, DEFAULTS, true);
    }

    private static void assertTransition(NightfallTracker tracker, long time,
                                         NightfallPhase previous, NightfallPhase current) {
        assertEquals(Optional.of(new NightfallTracker.Transition(previous, current)),
                tracker.observe(at(time)));
    }

    @Test
    void initialObservationAtAnyPhaseEstablishesBaselineWithoutTransition() {
        for (long time : new long[]{0, 11_500, 13_000, 17_000, 22_500}) {
            NightfallTracker tracker = new NightfallTracker();
            assertFalse(tracker.hasPhaseChangedSinceLastObservation(at(time)));
            assertTrue(tracker.observe(at(time)).isEmpty());
            assertFalse(tracker.hasPhaseChangedSinceLastObservation(at(time)));
        }
    }

    @Test
    void normalChronologicalObservationsEmitOneTransitionAtEachBoundary() {
        NightfallTracker tracker = new NightfallTracker();
        assertTrue(tracker.observe(at(11_499)).isEmpty());
        assertTransition(tracker, 11_500, NightfallPhase.DAYLIGHT, NightfallPhase.DUSK);
        assertTrue(tracker.observe(at(12_999)).isEmpty());
        assertTransition(tracker, 13_000, NightfallPhase.DUSK, NightfallPhase.NIGHT);
        assertTrue(tracker.observe(at(16_999)).isEmpty());
        assertTransition(tracker, 17_000, NightfallPhase.NIGHT, NightfallPhase.DEEP_NIGHT);
        assertTrue(tracker.observe(at(22_499)).isEmpty());
        assertTransition(tracker, 22_500, NightfallPhase.DEEP_NIGHT, NightfallPhase.DAWN);
        assertTrue(tracker.observe(at(23_999)).isEmpty());
        assertTransition(tracker, 24_000, NightfallPhase.DAWN, NightfallPhase.DAYLIGHT);
    }

    @Test
    void repeatedObservationsAndProgressInsideOnePhaseDoNotDuplicateEvents() {
        NightfallTracker tracker = new NightfallTracker();
        tracker.observe(at(13_000));
        for (long time : new long[]{13_000, 13_000, 13_020, 14_000, 16_999}) {
            assertFalse(tracker.hasPhaseChangedSinceLastObservation(at(time)));
            assertTrue(tracker.observe(at(time)).isEmpty());
        }
    }

    @Test
    void directForwardAndBackwardTimeJumpsDoNotFabricateIntermediateTransitions() {
        NightfallTracker tracker = new NightfallTracker();
        tracker.observe(at(0));
        assertTransition(tracker, 18_000, NightfallPhase.DAYLIGHT, NightfallPhase.DEEP_NIGHT);
        assertTransition(tracker, 12_000, NightfallPhase.DEEP_NIGHT, NightfallPhase.DUSK);
        assertTrue(tracker.observe(at(12_000)).isEmpty());
    }

    @Test
    void suppliedSleepJumpGoesDirectlyFromNightToDaylight() {
        NightfallTracker tracker = new NightfallTracker();
        tracker.observe(at(15_000));
        assertTransition(tracker, 24_000, NightfallPhase.NIGHT, NightfallPhase.DAYLIGHT);
        assertTrue(tracker.observe(at(24_000)).isEmpty());
    }

    @Test
    void frozenClockDoesNotEscalateAndResumedClockUsesNewTime() {
        NightfallTracker tracker = new NightfallTracker();
        tracker.observe(at(16_999));
        for (int poll = 0; poll < 100; poll++) {
            assertTrue(tracker.observe(at(16_999)).isEmpty());
            assertEquals(NightfallPhase.NIGHT, at(16_999).phase().orElseThrow());
            assertEquals(0.75, at(16_999).baseIntensity());
        }
        assertTransition(tracker, 17_000, NightfallPhase.NIGHT, NightfallPhase.DEEP_NIGHT);
    }

    @Test
    void freshQueriesDoNotConsumeTheScheduledObservationTransition() {
        NightfallTracker tracker = new NightfallTracker();
        tracker.observe(at(0));
        NightfallState current = at(17_000);
        for (int query = 0; query < 3; query++) {
            assertEquals(NightfallPhase.DEEP_NIGHT, current.phase().orElseThrow());
            assertTrue(tracker.hasPhaseChangedSinceLastObservation(current));
        }
        assertEquals(Optional.of(new NightfallTracker.Transition(
                NightfallPhase.DAYLIGHT, NightfallPhase.DEEP_NIGHT)), tracker.observe(current));
        assertFalse(tracker.hasPhaseChangedSinceLastObservation(current));
        assertTrue(tracker.observe(current).isEmpty());
    }

    @Test
    void inactiveObservationsResetTheBaselineWithoutActivationEvents() {
        NightfallTracker tracker = new NightfallTracker();
        tracker.observe(at(0));
        NightfallState inactive = NightfallState.from(17_000, DEFAULTS, false);
        assertFalse(tracker.hasPhaseChangedSinceLastObservation(inactive));
        assertTrue(tracker.observe(inactive).isEmpty());
        assertFalse(tracker.hasPhaseChangedSinceLastObservation(at(17_000)));
        assertTrue(tracker.observe(at(17_000)).isEmpty());
        assertTransition(tracker, 22_500, NightfallPhase.DEEP_NIGHT, NightfallPhase.DAWN);
    }

    @Test
    void completeSkippedCyclesWithTheSameObservedPhaseDoNotInventEvents() {
        NightfallTracker tracker = new NightfallTracker();
        tracker.observe(at(11_500));
        assertTrue(tracker.observe(at(24_000L * 1_000_000L + 11_500)).isEmpty());
        assertTransition(tracker, -1, NightfallPhase.DUSK, NightfallPhase.DAWN);
        assertTransition(tracker, 0, NightfallPhase.DAWN, NightfallPhase.DAYLIGHT);
    }

    @Test
    void worldTrackersAreIndependentAndRecreationDoesNotReuseStaleBaseline() {
        NightfallTracker firstWorld = new NightfallTracker();
        NightfallTracker secondWorld = new NightfallTracker();
        firstWorld.observe(at(0));
        secondWorld.observe(at(17_000));
        assertTransition(firstWorld, 11_500, NightfallPhase.DAYLIGHT, NightfallPhase.DUSK);
        assertTrue(secondWorld.observe(at(17_000)).isEmpty());
        assertTransition(secondWorld, 22_500, NightfallPhase.DEEP_NIGHT, NightfallPhase.DAWN);
        assertTrue(firstWorld.observe(at(11_500)).isEmpty());
        NightfallTracker reloadedWorld = new NightfallTracker();
        assertTrue(reloadedWorld.observe(at(22_500)).isEmpty());
    }

    @Test
    void clearingAWorldBaselinePreventsFalseReloadTransitions() {
        NightfallTracker tracker = new NightfallTracker();
        tracker.observe(at(0));
        tracker.clear();
        assertFalse(tracker.hasPhaseChangedSinceLastObservation(at(17_000)));
        assertTrue(tracker.observe(at(17_000)).isEmpty());
    }

    @Test
    void transitionValueRejectsNonChangesAndMissingPhases() {
        assertThrows(IllegalArgumentException.class,
                () -> new NightfallTracker.Transition(NightfallPhase.DAYLIGHT, NightfallPhase.DAYLIGHT));
        assertThrows(NullPointerException.class,
                () -> new NightfallTracker.Transition(null, NightfallPhase.NIGHT));
        assertThrows(NullPointerException.class,
                () -> new NightfallTracker.Transition(NightfallPhase.NIGHT, null));
    }
}
