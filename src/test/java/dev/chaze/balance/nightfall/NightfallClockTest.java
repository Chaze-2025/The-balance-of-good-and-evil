package dev.chaze.balance.nightfall;

import dev.chaze.balance.config.NightfallConfig;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure clock tests; these do not run a Minecraft world or advance its time. */
class NightfallClockTest {
    private static final NightfallConfig DEFAULTS = NightfallConfig.defaults();
    private static final NightfallPhase[] PHASES = NightfallPhase.values();

    @Test
    void includesTheExactBeginningAndEndOfEveryDefaultPhase() {
        int[][] intervals = {{0, 11_499}, {11_500, 12_999}, {13_000, 16_999},
                {17_000, 22_499}, {22_500, 23_999}};
        for (int i = 0; i < intervals.length; i++) {
            assertEquals(PHASES[i], NightfallClock.phaseAt(intervals[i][0], DEFAULTS),
                    "phase beginning " + intervals[i][0]);
            assertEquals(PHASES[i], NightfallClock.phaseAt(intervals[i][1], DEFAULTS),
                    "phase end " + intervals[i][1]);
        }
    }

    @Test
    void classifiesBothSidesOfEveryTransitionIncludingDailyWrap() {
        int[] thresholds = {11_500, 13_000, 17_000, 22_500, 24_000};
        for (int i = 0; i < thresholds.length; i++) {
            long time = thresholds[i];
            NightfallPhase next = PHASES[(i + 1) % PHASES.length];
            assertEquals(PHASES[i], NightfallClock.phaseAt(time - 1, DEFAULTS));
            assertEquals(next, NightfallClock.phaseAt(time, DEFAULTS));
            assertEquals(next, NightfallClock.phaseAt(time + 1, DEFAULTS));
        }
    }

    @Test
    void wrapsWholeDaysAndNegativeTimesWithoutNegativeRemainders() {
        long[] absolute = {-48_001, -48_000, -24_001, -24_000, -1, 0,
                23_999, 24_000, 24_001, 48_000, 48_001};
        int[] expected = {23_999, 0, 23_999, 0, 23_999, 0, 23_999, 0, 1, 0, 1};
        for (int i = 0; i < absolute.length; i++) {
            assertEquals(expected[i], NightfallClock.timeOfDay(absolute[i]), "absolute time " + absolute[i]);
            assertEquals(expected[i] == 23_999 ? NightfallPhase.DAWN : NightfallPhase.DAYLIGHT,
                    NightfallClock.phaseAt(absolute[i], DEFAULTS));
        }
    }

    @Test
    void handlesLongExtremesUsingAnIndependentBigIntegerModuloOracle() {
        long[] times = {Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE - 1,
                Long.MAX_VALUE, 24_000L * 100_000_000_000L + 17_000,
                -24_000L * 100_000_000_000L - 1};
        for (long time : times) {
            int normalized = BigInteger.valueOf(time).mod(BigInteger.valueOf(24_000)).intValueExact();
            assertEquals(normalized, NightfallClock.timeOfDay(time), "absolute time " + time);
            assertEquals(NightfallClock.phaseAt(normalized, DEFAULTS),
                    NightfallClock.phaseAt(time, DEFAULTS), "phase at " + time);
        }
    }

    @Test
    void customThresholdsDefineEveryPhaseWithoutUsingDefaultBoundaries() {
        NightfallConfig custom = customConfig(true);
        int[][] intervals = {{0, 999}, {1_000, 4_999}, {5_000, 9_999},
                {10_000, 19_999}, {20_000, 23_999}};
        for (int i = 0; i < intervals.length; i++) {
            assertEquals(PHASES[i], NightfallClock.phaseAt(intervals[i][0], custom));
            assertEquals(PHASES[i], NightfallClock.phaseAt(intervals[i][1], custom));
        }
    }

    @Test
    void exposesDefaultAndCustomIntensityForEachPhase() {
        double[] defaults = {0.15, 0.40, 0.75, 1.00, 0.25};
        double[] custom = {1.0, 0.0, 0.2, 0.9, 0.0};
        NightfallConfig config = customConfig(true);
        for (int i = 0; i < PHASES.length; i++) {
            assertEquals(defaults[i], NightfallClock.intensity(PHASES[i], DEFAULTS));
            assertEquals(custom[i], NightfallClock.intensity(PHASES[i], config));
        }
    }

    @Test
    void repeatedQueriesHaveNoAdvancingTimerOrMutablePhaseState() {
        NightfallConfig custom = customConfig(true);
        for (int repetition = 0; repetition < 100; repetition++) {
            assertAll(
                    () -> assertEquals(NightfallPhase.DEEP_NIGHT, NightfallClock.phaseAt(12_345, custom)),
                    () -> assertEquals(NightfallPhase.DUSK, NightfallClock.phaseAt(2_345, custom)),
                    () -> assertEquals(0.9, NightfallClock.intensity(NightfallPhase.DEEP_NIGHT, custom)));
        }
    }

    @Test
    void classifiesElevatedNighttimePhasesSeparatelyFromTheirIntensity() {
        assertAll(
                () -> assertFalse(NightfallPhase.DAYLIGHT.isNighttime()),
                () -> assertTrue(NightfallPhase.DUSK.isNighttime()),
                () -> assertTrue(NightfallPhase.NIGHT.isNighttime()),
                () -> assertTrue(NightfallPhase.DEEP_NIGHT.isNighttime()),
                () -> assertFalse(NightfallPhase.DAWN.isNighttime()));
    }

    @Test
    void disabledConfigurationDoesNotChangeTheUnderlyingClockMathematics() {
        // The service decides inactivity; the clock remains a deterministic time classifier.
        assertEquals(NightfallPhase.DEEP_NIGHT, NightfallClock.phaseAt(12_345, customConfig(false)));
        assertEquals(0.9, NightfallClock.intensity(NightfallPhase.DEEP_NIGHT, customConfig(false)));
    }

    private static NightfallConfig customConfig(boolean enabled) {
        return new NightfallConfig(enabled, 1_000, 5_000, 10_000, 20_000,
                1.0, 0.0, 0.2, 0.9, 0.0, 20);
    }
}
