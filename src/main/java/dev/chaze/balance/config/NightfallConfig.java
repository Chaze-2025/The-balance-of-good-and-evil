package dev.chaze.balance.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.Set;

/** Validated restart-only server settings. Existing files are never rewritten. */
public record NightfallConfig(boolean enabled,
                              int duskStart, int nightStart, int deepNightStart, int dawnStart,
                              double daylightIntensity, double duskIntensity, double nightIntensity,
                              double deepNightIntensity, double dawnIntensity, int updateIntervalTicks) {
    public static final int MIN_UPDATE_INTERVAL_TICKS = 5;
    public static final int MAX_UPDATE_INTERVAL_TICKS = 200;
    private static final Set<String> KEYS = Set.of("version", "enabled", "dusk_start", "night_start",
            "deep_night_start", "dawn_start", "daylight_intensity", "dusk_intensity", "night_intensity",
            "deep_night_intensity", "dawn_intensity", "update_interval_ticks");
    private static final String DEFAULT_FILE = """
            # Nightfall server settings, version 1. Restart the server after changes.
            # The Overworld is the only supported dimension in Phase 2.
            # Thresholds are inclusive phase starts in the 24000-tick daily cycle.
            version=1
            enabled=true
            dusk_start=11500
            night_start=13000
            deep_night_start=17000
            dawn_start=22500
            daylight_intensity=0.15
            dusk_intensity=0.40
            night_intensity=0.75
            deep_night_intensity=1.00
            dawn_intensity=0.25
            # Observation interval: 5..200 server ticks. Direct queries remain current.
            update_interval_ticks=20
            """;

    public NightfallConfig {
        requireThreshold("dusk_start", duskStart);
        requireThreshold("night_start", nightStart);
        requireThreshold("deep_night_start", deepNightStart);
        requireThreshold("dawn_start", dawnStart);
        if (!(duskStart < nightStart && nightStart < deepNightStart && deepNightStart < dawnStart)) {
            throw new IllegalArgumentException("Nightfall thresholds must be strictly ordered: "
                    + "0 < dusk_start < night_start < deep_night_start < dawn_start < 24000");
        }
        requireIntensity("daylight_intensity", daylightIntensity);
        requireIntensity("dusk_intensity", duskIntensity);
        requireIntensity("night_intensity", nightIntensity);
        requireIntensity("deep_night_intensity", deepNightIntensity);
        requireIntensity("dawn_intensity", dawnIntensity);
        if (updateIntervalTicks < MIN_UPDATE_INTERVAL_TICKS || updateIntervalTicks > MAX_UPDATE_INTERVAL_TICKS) {
            throw new IllegalArgumentException("Nightfall update_interval_ticks must be between 5 and 200");
        }
    }

    public static NightfallConfig defaults() {
        return new NightfallConfig(true, 11_500, 13_000, 17_000, 22_500,
                0.15, 0.40, 0.75, 1.00, 0.25, 20);
    }

    public static NightfallConfig load(Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            try {
                Files.writeString(path, DEFAULT_FILE, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException ignored) {
                // Another initializer created it after the existence check. Read, never replace.
            }
        }
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        String version = required(values, "version");
        if (!"1".equals(version)) {
            throw new IllegalArgumentException("Unsupported Nightfall configuration version '" + version
                    + "'; expected version=1");
        }
        for (String key : values.stringPropertyNames()) {
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown Nightfall configuration setting '" + key + "'");
            }
        }
        String enabled = required(values, "enabled");
        if (!enabled.equalsIgnoreCase("true") && !enabled.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Nightfall enabled must be true or false");
        }
        return new NightfallConfig(Boolean.parseBoolean(enabled), integer(values, "dusk_start"),
                integer(values, "night_start"), integer(values, "deep_night_start"), integer(values, "dawn_start"),
                decimal(values, "daylight_intensity"), decimal(values, "dusk_intensity"),
                decimal(values, "night_intensity"), decimal(values, "deep_night_intensity"),
                decimal(values, "dawn_intensity"), integer(values, "update_interval_ticks"));
    }

    private static void requireThreshold(String key, int value) {
        if (value <= 0 || value >= 24_000) {
            throw new IllegalArgumentException("Nightfall " + key + " must be greater than 0 and less than 24000");
        }
    }

    private static void requireIntensity(String key, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Nightfall " + key + " must be finite and between 0.0 and 1.0");
        }
    }

    private static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing or blank required Nightfall setting '" + key + "'");
        }
        return value.trim();
    }

    private static int integer(Properties values, String key) {
        String value = required(values, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Nightfall " + key + " must be an integer", exception);
        }
    }

    private static double decimal(Properties values, String key) {
        String value = required(values, key);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Nightfall " + key + " must be a decimal number", exception);
        }
    }
}
