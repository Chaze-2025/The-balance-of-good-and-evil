package dev.chaze.balance.ward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardSourceTest {
    private final WardSource source = new WardSource(10, 64, -5, 14.0);

    @Test
    void containsTheBlockCenterAndInteriorPoints() {
        assertAll(
                () -> assertTrue(source.contains(10.5, 64.5, -4.5)),
                () -> assertTrue(source.contains(13.5, 68.5, -0.5)));
    }

    @Test
    void includesTheBoundaryInEveryAxisDirection() {
        assertAll(
                () -> assertTrue(source.contains(24.5, 64.5, -4.5)),
                () -> assertTrue(source.contains(-3.5, 64.5, -4.5)),
                () -> assertTrue(source.contains(10.5, 78.5, -4.5)),
                () -> assertTrue(source.contains(10.5, 50.5, -4.5)),
                () -> assertTrue(source.contains(10.5, 64.5, 9.5)),
                () -> assertTrue(source.contains(10.5, 64.5, -18.5)));
    }

    @Test
    void excludesPointsImmediatelyBeyondTheBoundary() {
        assertAll(
                () -> assertFalse(source.contains(Math.nextUp(24.5), 64.5, -4.5)),
                () -> assertFalse(source.contains(10.5, Math.nextUp(78.5), -4.5)),
                () -> assertFalse(source.contains(10.5, 64.5, Math.nextUp(9.5))));
    }

    @Test
    void usesEuclideanDistanceRatherThanABoundingBox() {
        WardSource smaller = new WardSource(0, 0, 0, 10.0);
        assertAll(
                () -> assertTrue(smaller.contains(6.5, 8.5, 0.5)),
                () -> assertFalse(smaller.contains(6.5, 8.5, 0.6)),
                () -> assertFalse(source.contains(20.5, 74.5, -4.5)));
    }

    @Test
    void measuresFromTheCenterOfNegativeCoordinateBlocks() {
        WardSource negative = new WardSource(-2, -2, -2, 1.0);
        assertAll(
                () -> assertTrue(negative.contains(-0.5, -1.5, -1.5)),
                () -> assertFalse(negative.contains(-2.6, -1.5, -1.5)));
    }

    @Test
    void rejectsInvalidRadii() {
        for (double radius : new double[]{0.0, -0.0, -1.0, Double.NaN,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new WardSource(0, 0, 0, radius));
        }
    }

    @Test
    void handlesNonFiniteTargetsAndLargeFiniteDistancesWithoutOverflow() {
        WardSource enormous = new WardSource(0, 0, 0, Double.MAX_VALUE / 2.0);
        assertAll(
                () -> assertFalse(source.contains(Double.NaN, 64.5, -4.5)),
                () -> assertFalse(source.contains(Double.POSITIVE_INFINITY, 64.5, -4.5)),
                () -> assertTrue(enormous.contains(Double.MAX_VALUE / 4.0, 0.5, 0.5)),
                () -> assertFalse(enormous.contains(Double.MAX_VALUE, 0.5, 0.5)));
    }
}
