package dev.chaze.balance.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NightfallConfigTest {
    private static final String DEFAULT_FILE = """
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
            update_interval_ticks=20
            """;
    private static final String[] INTENSITY_KEYS = {"daylight_intensity", "dusk_intensity",
            "night_intensity", "deep_night_intensity", "dawn_intensity"};

    @TempDir
    Path directory;

    @Test
    void defaultsMatchTheDocumentedTimingAndIntensityBaseline() {
        NightfallConfig config = NightfallConfig.defaults();
        assertAll(
                () -> assertTrue(config.enabled()),
                () -> assertEquals(11_500, config.duskStart()),
                () -> assertEquals(13_000, config.nightStart()),
                () -> assertEquals(17_000, config.deepNightStart()),
                () -> assertEquals(22_500, config.dawnStart()),
                () -> assertEquals(0.15, config.daylightIntensity()),
                () -> assertEquals(0.40, config.duskIntensity()),
                () -> assertEquals(0.75, config.nightIntensity()),
                () -> assertEquals(1.00, config.deepNightIntensity()),
                () -> assertEquals(0.25, config.dawnIntensity()),
                () -> assertEquals(20, config.updateIntervalTicks()));
    }

    @Test
    void missingFileCreatesANestedVersionedDefaultThatCanBeLoadedAgain() throws IOException {
        Path path = directory.resolve("nested/config/balance-nightfall.properties");
        NightfallConfig config = NightfallConfig.load(path);
        Properties exported = new Properties();
        exported.load(new StringReader(Files.readString(path, StandardCharsets.UTF_8)));
        assertAll(
                () -> assertEquals(NightfallConfig.defaults(), config),
                () -> assertEquals("1", exported.getProperty("version")),
                () -> assertEquals(12, exported.size()),
                () -> assertEquals(config, NightfallConfig.load(path)));
    }

    @Test
    void customSettingsAndCommentsArePreservedVerbatim() throws IOException {
        String custom = "# Server tuning stays intact.\n" + DEFAULT_FILE
                .replace("enabled=true", "enabled = FALSE")
                .replace("dusk_start=11500", "dusk_start=1000")
                .replace("night_start=13000", "night_start=5000")
                .replace("deep_night_start=17000", "deep_night_start=10000")
                .replace("dawn_start=22500", "dawn_start=20000")
                .replace("daylight_intensity=0.15", "daylight_intensity=1")
                .replace("dusk_intensity=0.40", "dusk_intensity=0")
                .replace("update_interval_ticks=20", "update_interval_ticks=5");
        Path path = write(custom);
        NightfallConfig config = NightfallConfig.load(path);
        assertAll(
                () -> assertFalse(config.enabled()),
                () -> assertEquals(1_000, config.duskStart()),
                () -> assertEquals(5_000, config.nightStart()),
                () -> assertEquals(10_000, config.deepNightStart()),
                () -> assertEquals(20_000, config.dawnStart()),
                () -> assertEquals(1.0, config.daylightIntensity()),
                () -> assertEquals(0.0, config.duskIntensity()),
                () -> assertEquals(5, config.updateIntervalTicks()),
                () -> assertEquals(custom, Files.readString(path, StandardCharsets.UTF_8)));
    }

    @Test
    void everyRequiredPropertyReportsItsMissingKeyWithoutReplacingTheFile() throws IOException {
        for (String line : DEFAULT_FILE.lines().toList()) {
            String key = line.substring(0, line.indexOf('='));
            assertRejected(DEFAULT_FILE.replace(line + "\n", ""), key);
        }
    }

    @Test
    void unsupportedVersionsAndUnknownPropertiesFailWithUsefulContext() throws IOException {
        assertRejected(change("version", "2"), "version");
        assertRejected(change("version", "future"), "version");
        assertRejected(DEFAULT_FILE + "dusk_starts=12000\n", "dusk_starts");
    }

    @Test
    void rejectsOverlappingOrReversedPhaseThresholds() throws IOException {
        String[][] invalid = {{"dusk_start", "13000"}, {"night_start", "11500"},
                {"night_start", "17000"}, {"deep_night_start", "13000"},
                {"deep_night_start", "22500"}, {"dawn_start", "17000"}};
        for (String[] setting : invalid) {
            assertRejected(change(setting[0], setting[1]), setting[0]);
        }
    }

    @Test
    void rejectsThresholdsOutsideTheDailyCycle() throws IOException {
        for (String key : new String[]{"dusk_start", "night_start", "deep_night_start", "dawn_start"}) {
            for (String value : new String[]{"-1", "0", "24000", "24001"}) {
                assertRejected(change(key, value), key);
            }
        }
    }

    @Test
    void rejectsEveryInvalidIntensityAndIdentifiesTheAffectedProperty() throws IOException {
        for (String key : INTENSITY_KEYS) {
            for (String value : new String[]{"-0.01", "1.01", "NaN", "Infinity", "-Infinity"}) {
                assertRejected(change(key, value), key);
            }
        }
    }

    @Test
    void acceptsInclusiveIntensityEndpointsWithoutRequiringMonotonicIntensity() throws IOException {
        String content = DEFAULT_FILE;
        for (int i = 0; i < INTENSITY_KEYS.length; i++) {
            String key = INTENSITY_KEYS[i];
            content = content.replaceAll("(?m)^" + key + "=.*$", key + "=" + (i % 2));
        }
        NightfallConfig config = NightfallConfig.load(write(content));
        assertAll(
                () -> assertEquals(0.0, config.daylightIntensity()),
                () -> assertEquals(1.0, config.duskIntensity()),
                () -> assertEquals(0.0, config.nightIntensity()),
                () -> assertEquals(1.0, config.deepNightIntensity()),
                () -> assertEquals(0.0, config.dawnIntensity()));
    }

    @Test
    void boundsTheObservationIntervalAndAcceptsBothEndpoints() throws IOException {
        assertEquals(5, NightfallConfig.load(write(change("update_interval_ticks", "5"))).updateIntervalTicks());
        assertEquals(200, NightfallConfig.load(write(change("update_interval_ticks", "200"))).updateIntervalTicks());
        for (String invalid : new String[]{"-1", "0", "1", "4", "201", "2147483647"}) {
            assertRejected(change("update_interval_ticks", invalid), "update_interval_ticks");
        }
    }

    @Test
    void malformedNumbersAndBooleanValuesFailWithoutSilentCoercion() throws IOException {
        for (String key : new String[]{"dusk_start", "night_start", "deep_night_start", "dawn_start",
                "update_interval_ticks", "daylight_intensity", "dusk_intensity", "night_intensity",
                "deep_night_intensity", "dawn_intensity"}) {
            assertRejected(change(key, "not-a-number"), key);
        }
        assertRejected(change("night_start", "13000.5"), "night_start");
        assertRejected(change("night_start", "2147483648"), "night_start");
        for (String value : new String[]{"yes", "1", "false-ish", ""}) {
            assertRejected(change("enabled", value), "enabled");
        }
    }

    @Test
    void directConstructionAlsoValidatesSettingsBeforeTheClockUsesThem() {
        assertThrows(IllegalArgumentException.class, () -> new NightfallConfig(true,
                13_000, 13_000, 17_000, 22_500, 0.15, 0.4, 0.75, 1.0, 0.25, 20));
        assertThrows(IllegalArgumentException.class, () -> new NightfallConfig(true,
                11_500, 13_000, 17_000, 22_500, 0.15, 0.4, Double.NaN, 1.0, 0.25, 20));
        assertThrows(IllegalArgumentException.class, () -> new NightfallConfig(true,
                11_500, 13_000, 17_000, 22_500, 0.15, 0.4, 0.75, 1.0, 0.25, 201));
    }

    private static String change(String key, String value) {
        return DEFAULT_FILE.replaceAll("(?m)^" + key + "=.*$", key + "=" + value);
    }

    private Path write(String content) throws IOException {
        Path path = directory.resolve("balance-nightfall.properties");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private void assertRejected(String content, String key) throws IOException {
        Path path = write(content);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> NightfallConfig.load(path), key);
        assertAll(
                () -> assertTrue(failure.getMessage().contains(key),
                        "Expected useful context for " + key + ": " + failure.getMessage()),
                () -> assertEquals(content, Files.readString(path, StandardCharsets.UTF_8)));
    }
}
