package dev.chaze.balance.nightfall;

import static org.junit.jupiter.api.Assertions.*;

import dev.chaze.balance.config.NightfallConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests state contracts only; no Minecraft world or dimension is created. */
class NightfallStateTest {
    private static final NightfallConfig DEFAULTS = NightfallConfig.defaults();

    @Test
    void activeStateUsesSuppliedTimePhaseAndConfiguredIntensity() {
        NightfallState state = NightfallState.from(24_000L + 17_000L, DEFAULTS, true);
        assertTrue(state.enabled());
        assertEquals(17_000, state.timeOfDay());
        assertEquals(Optional.of(NightfallPhase.DEEP_NIGHT), state.phase());
        assertEquals(1.0, state.baseIntensity());
        assertTrue(state.isElevatedNighttimeDanger());
    }

    @Test
    void disabledSystemHasNoPhaseOrDangerEvenAtDeepNight() {
        NightfallConfig disabled = new NightfallConfig(false, 11_500, 13_000, 17_000, 22_500,
                0.15, 0.40, 0.75, 1.0, 0.25, 20);
        NightfallState state = NightfallState.from(17_000, disabled, true);
        assertFalse(state.enabled());
        assertEquals(17_000, state.timeOfDay());
        assertTrue(state.phase().isEmpty());
        assertEquals(0.0, state.baseIntensity());
        assertFalse(state.isElevatedNighttimeDanger());
    }

    @Test
    void unsupportedDimensionRetainsNormalizedTimeWithoutPretendingToHaveNightfall() {
        NightfallState state = NightfallState.from(-1, DEFAULTS, false);
        assertFalse(state.enabled());
        assertEquals(23_999, state.timeOfDay());
        assertTrue(state.phase().isEmpty());
        assertEquals(0.0, state.baseIntensity());
        assertFalse(state.isElevatedNighttimeDanger());
    }

    @Test
    void elevatedFlagClassifiesDuskNightAndDeepNightOnly() {
        long[] times = {0, 11_500, 13_000, 17_000, 22_500};
        boolean[] expected = {false, true, true, true, false};
        for (int i = 0; i < times.length; i++) {
            assertEquals(expected[i], NightfallState.from(times[i], DEFAULTS, true)
                    .isElevatedNighttimeDanger(), "time=" + times[i]);
        }
    }

    @Test
    void elevationClassificationDoesNotOverrideCustomIntensity() {
        NightfallConfig custom = new NightfallConfig(true, 11_500, 13_000, 17_000, 22_500,
                1.0, 0.0, 0.0, 0.0, 1.0, 20);
        NightfallState dusk = NightfallState.from(11_500, custom, true);
        assertTrue(dusk.isElevatedNighttimeDanger());
        assertEquals(0.0, dusk.baseIntensity());
        NightfallState dawn = NightfallState.from(22_500, custom, true);
        assertFalse(dawn.isElevatedNighttimeDanger());
        assertEquals(1.0, dawn.baseIntensity());
    }

    @Test
    void constructorRejectsInconsistentOrOutOfRangeState() {
        assertThrows(IllegalArgumentException.class,
                () -> new NightfallState(-1, Optional.empty(), 0.0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new NightfallState(24_000, Optional.empty(), 0.0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new NightfallState(0, Optional.empty(), 0.0, true));
        assertThrows(IllegalArgumentException.class,
                () -> new NightfallState(0, Optional.of(NightfallPhase.DAYLIGHT), 0.0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new NightfallState(0, Optional.empty(), 0.1, false));
        for (double intensity : new double[]{-0.01, 1.01, Double.NaN,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new NightfallState(0, Optional.of(NightfallPhase.DAYLIGHT), intensity, true));
        }
        assertThrows(NullPointerException.class,
                () -> new NightfallState(0, null, 0.0, false));
    }
}
