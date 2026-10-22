package dev.chaze.balance.ward;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A world's transient ward sources, maintained by the server-thread world adapter.
 * Block data remains authoritative; this index contains no world/entity references
 * and must be rebuilt from loaded blocks when a world or chunk is loaded.
 */
public final class WardIndex {
    private final Map<Position, WardSource> sources = new LinkedHashMap<>();

    /** Adds or replaces the source at exactly this block position. */
    public void upsert(WardSource source) {
        Objects.requireNonNull(source, "source");
        sources.put(new Position(source.x(), source.y(), source.z()), source);
    }

    /** Returns whether a source was removed. Removing a missing source is harmless. */
    public boolean remove(int x, int y, int z) {
        return sources.remove(new Position(x, y, z)) != null;
    }

    public void clear() {
        sources.clear();
    }

    /** Returns an immutable snapshot, never a mutable view of the live index. */
    public List<WardSource> sources() {
        return List.copyOf(sources.values());
    }

    /** Protection is a union of spheres, so overlapping wards do not stack. */
    public boolean isProtected(double x, double y, double z) {
        for (WardSource source : sources.values()) {
            if (source.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private record Position(int x, int y, int z) {
    }
}
