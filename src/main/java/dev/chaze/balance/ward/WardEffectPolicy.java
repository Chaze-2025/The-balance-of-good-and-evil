package dev.chaze.balance.ward;

/** The prototype's target and Slowness refresh rules, independent of Minecraft APIs. */
public final class WardEffectPolicy {
    public static final int REFRESH_INTERVAL_TICKS = 20;
    public static final int EFFECT_DURATION_TICKS = 60;
    public static final int AMPLIFIER = 0;

    private WardEffectPolicy() {
    }

    /**
     * Determines whether to apply Slowness I to an already protected target.
     * The adapter must identify an ordinary vanilla zombie and supply amplifier
     * {@code -1} for a missing effect. A negative duration on an existing effect
     * represents Minecraft's infinite duration and must be preserved.
     * Stronger or longer unrelated effects are never shortened or downgraded.
     */
    public static boolean shouldRefresh(boolean ordinaryZombie, int existingAmplifier, int remainingTicks) {
        if (!ordinaryZombie) {
            return false;
        }
        if (existingAmplifier == -1) {
            return true;
        }
        return existingAmplifier == AMPLIFIER
                && remainingTicks >= 0
                && remainingTicks < EFFECT_DURATION_TICKS;
    }
}
