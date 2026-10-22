package dev.chaze.balance.ward;

/** A spherical ward centered on the center of its source block. */
public record WardSource(int x, int y, int z, double radius) {
    public WardSource {
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("Ward radius must be positive and finite");
        }
    }

    /** Includes the sphere's boundary and measures all three spatial axes. */
    public boolean contains(double targetX, double targetY, double targetZ) {
        double dx = targetX - (x + 0.5);
        double dy = targetY - (y + 0.5);
        double dz = targetZ - (z + 0.5);
        // hypot avoids overflowing intermediate squares for otherwise finite coordinates.
        return Math.hypot(Math.hypot(dx, dy), dz) <= radius;
    }
}
