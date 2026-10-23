package dev.chaze.balance.nightfall;

/** The five parts of Minecraft's daily cycle; phase membership is independent of intensity. */
public enum NightfallPhase {
    DAYLIGHT,
    DUSK,
    NIGHT,
    DEEP_NIGHT,
    DAWN;

    /** Dawn is the recovery phase; the nighttime flag covers dusk through deep night. */
    public boolean isNighttime() {
        return this == DUSK || this == NIGHT || this == DEEP_NIGHT;
    }
}
