package dev.chaze.balance.ward;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardIndexTest {
    @Test
    void insertingASourceActivatesOnlyItsSphere() {
        WardIndex index = new WardIndex();
        assertFalse(index.isProtected(0.5, 64.5, 0.5));

        index.upsert(new WardSource(0, 64, 0, 14.0));

        assertAll(
                () -> assertTrue(index.isProtected(0.5, 64.5, 0.5)),
                () -> assertFalse(index.isProtected(15.5, 64.5, 0.5)));
    }

    @Test
    void overlappingSourcesFormAUnionWithoutDuplicateEntries() {
        WardIndex index = new WardIndex();
        WardSource left = new WardSource(0, 64, 0, 14.0);
        WardSource right = new WardSource(20, 64, 0, 14.0);
        index.upsert(left);
        index.upsert(right);
        index.upsert(left);

        assertAll(
                () -> assertTrue(index.isProtected(-10.5, 64.5, 0.5)),
                () -> assertTrue(index.isProtected(10.5, 64.5, 0.5)),
                () -> assertTrue(index.isProtected(30.5, 64.5, 0.5)),
                () -> assertEquals(2, index.sources().size()));
    }

    @Test
    void removingOneOverlappingSourceKeepsOnlyTheOtherProtection() {
        WardIndex index = new WardIndex();
        index.upsert(new WardSource(0, 64, 0, 14.0));
        index.upsert(new WardSource(20, 64, 0, 14.0));

        assertTrue(index.remove(0, 64, 0));

        assertAll(
                () -> assertTrue(index.isProtected(10.5, 64.5, 0.5)),
                () -> assertFalse(index.isProtected(-10.5, 64.5, 0.5)),
                () -> assertEquals(1, index.sources().size()));
    }

    @Test
    void removingTheLastSourceLeavesNoStaleProtection() {
        WardIndex index = new WardIndex();
        index.upsert(new WardSource(0, 64, 0, 14.0));

        assertTrue(index.remove(0, 64, 0));

        assertAll(
                () -> assertFalse(index.isProtected(0.5, 64.5, 0.5)),
                () -> assertFalse(index.remove(0, 64, 0)),
                () -> assertTrue(index.sources().isEmpty()));
    }

    @Test
    void movingATargetOutsideAllSourcesEndsProtection() {
        WardIndex index = new WardIndex();
        index.upsert(new WardSource(0, 64, 0, 14.0));
        index.upsert(new WardSource(20, 64, 0, 14.0));

        assertAll(
                () -> assertTrue(index.isProtected(10.5, 64.5, 0.5)),
                () -> assertFalse(index.isProtected(35.5, 64.5, 0.5)),
                () -> assertFalse(index.isProtected(10.5, 79.5, 0.5)));
    }

    @Test
    void replacingASourceUpdatesItsRadiusAtTheSamePosition() {
        WardIndex index = new WardIndex();
        index.upsert(new WardSource(0, 64, 0, 14.0));
        assertTrue(index.isProtected(10.5, 64.5, 0.5));

        index.upsert(new WardSource(0, 64, 0, 4.0));

        assertAll(
                () -> assertFalse(index.isProtected(10.5, 64.5, 0.5)),
                () -> assertTrue(index.isProtected(4.5, 64.5, 0.5)),
                () -> assertEquals(1, index.sources().size()));
    }

    @Test
    void clearAndRebuildReconstructsProtectionFromSourceDescriptors() {
        // Tests the transient index contract, not Minecraft chunk/world persistence.
        List<WardSource> blockData = List.of(
                new WardSource(0, 64, 0, 14.0), new WardSource(0, -32, 0, 8.0));
        WardIndex index = new WardIndex();
        blockData.forEach(index::upsert);

        index.clear();
        assertFalse(index.isProtected(0.5, 64.5, 0.5));
        assertTrue(index.sources().isEmpty());
        blockData.forEach(index::upsert);

        assertAll(
                () -> assertTrue(index.isProtected(0.5, 64.5, 0.5)),
                () -> assertTrue(index.isProtected(0.5, -32.5, 0.5)),
                () -> assertEquals(blockData, index.sources()));
    }

    @Test
    void sourceSnapshotsCannotMutateOrTrackTheLiveIndex() {
        WardIndex index = new WardIndex();
        WardSource source = new WardSource(0, 64, 0, 14.0);
        index.upsert(source);
        List<WardSource> snapshot = index.sources();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(source));
        index.clear();

        assertAll(
                () -> assertEquals(List.of(source), snapshot),
                () -> assertTrue(index.sources().isEmpty()));
    }

    @Test
    void separateWorldIndexesDoNotShareProtection() {
        WardIndex overworld = new WardIndex();
        WardIndex nether = new WardIndex();
        overworld.upsert(new WardSource(0, 64, 0, 14.0));

        assertAll(
                () -> assertTrue(overworld.isProtected(0.5, 64.5, 0.5)),
                () -> assertFalse(nether.isProtected(0.5, 64.5, 0.5)));
    }
}
