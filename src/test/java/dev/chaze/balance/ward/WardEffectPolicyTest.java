package dev.chaze.balance.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardEffectPolicyTest {
    @Test
    void ordinaryZombieWithNoSlownessReceivesTheEffect() {
        assertTrue(WardEffectPolicy.shouldRefresh(true, -1, 0));
    }

    @Test
    void unsupportedTargetsNeverReceiveOrRefreshSlowness() {
        assertAll(
                () -> assertFalse(WardEffectPolicy.shouldRefresh(false, -1, 0)),
                () -> assertFalse(WardEffectPolicy.shouldRefresh(false, 0, 1)));
    }

    @Test
    void strongerUnrelatedSlownessIsPreservedEvenWhenNearlyExpired() {
        assertAll(
                () -> assertFalse(WardEffectPolicy.shouldRefresh(true, 1, 1)),
                () -> assertFalse(WardEffectPolicy.shouldRefresh(true, 3, 200)),
                () -> assertFalse(WardEffectPolicy.shouldRefresh(true, 1, -1)));
    }

    @Test
    void longerOrEqualDurationSlownessOneIsPreserved() {
        assertAll(
                () -> assertFalse(WardEffectPolicy.shouldRefresh(true, 0, 60)),
                () -> assertFalse(WardEffectPolicy.shouldRefresh(true, 0, 600)));
    }

    @Test
    void infiniteSlownessOneIsNeverReplacedByAFiniteWardEffect() {
        assertFalse(WardEffectPolicy.shouldRefresh(true, 0, -1));
    }

    @Test
    void shortSlownessOneIsRefreshedBeforeItExpires() {
        assertAll(
                () -> assertTrue(WardEffectPolicy.shouldRefresh(true, 0, 59)),
                () -> assertTrue(WardEffectPolicy.shouldRefresh(true, 0, 40)),
                () -> assertTrue(WardEffectPolicy.shouldRefresh(true, 0, 0)));
    }

    @Test
    void anOverlappingRefreshDoesNotApplyAnotherOrStrongerEffect() {
        WardIndex index = new WardIndex();
        index.upsert(new WardSource(0, 64, 0, 14.0));
        index.upsert(new WardSource(20, 64, 0, 14.0));
        assertTrue(index.isProtected(10.5, 64.5, 0.5));
        assertTrue(WardEffectPolicy.shouldRefresh(true, -1, 0));

        // After applying the one fixed effect, a second source cannot stack it.
        assertFalse(WardEffectPolicy.shouldRefresh(true, WardEffectPolicy.AMPLIFIER,
                WardEffectPolicy.EFFECT_DURATION_TICKS));
    }

    @Test
    void aRemovedSourceCannotAuthorizeAnotherEffectRefresh() {
        WardIndex index = new WardIndex();
        index.upsert(new WardSource(0, 64, 0, 14.0));
        assertTrue(index.isProtected(0.5, 64.5, 0.5)
                && WardEffectPolicy.shouldRefresh(true, 0, 40));

        index.remove(0, 64, 0);

        // Composition contract only: actual server scheduling is an adapter concern.
        assertFalse(index.isProtected(0.5, 64.5, 0.5)
                && WardEffectPolicy.shouldRefresh(true, 0, 40));
    }
}
