package dev.chaze.balance.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardConfigTest {
    @TempDir
    Path directory;

    @Test
    void absentConfigurationCreatesAReadableVersionedDefault() throws IOException {
        Path path = directory.resolve("nested/config/balance-wards.properties");

        WardConfig config = WardConfig.load(path);

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertAll(
                () -> assertTrue(Files.isRegularFile(path)),
                () -> assertEquals(14.0, config.radius()),
                () -> assertEquals("1", properties.getProperty("version")),
                () -> assertEquals("14.0", properties.getProperty("radius")),
                () -> assertEquals(config, WardConfig.load(path)));
    }

    @Test
    void validCustomConfigurationIsPreservedVerbatim() throws IOException {
        Path path = directory.resolve("balance-wards.properties");
        String original = "# Custom radius and comments stay intact.\nversion = 1\nradius = 7.5\n\n";
        Files.writeString(path, original, StandardCharsets.UTF_8);

        WardConfig config = WardConfig.load(path);

        assertAll(
                () -> assertEquals(7.5, config.radius()),
                () -> assertEquals(original, Files.readString(path, StandardCharsets.UTF_8)));
    }

    @Test
    void nonFiniteNonPositiveAndOversizedRadiiAreRejectedWithoutRewriting() throws IOException {
        for (String radius : new String[]{"NaN", "Infinity", "-Infinity", "0", "-0.0", "-1", "64.0001"}) {
            assertRejectedWithoutRewriting("version=1\nradius=" + radius + "\n");
        }
    }

    @Test
    void maximumAllowedRadiusIsAccepted() throws IOException {
        Path path = directory.resolve("balance-wards.properties");
        String original = "version=1\nradius=64\n";
        Files.writeString(path, original, StandardCharsets.UTF_8);

        WardConfig config = WardConfig.load(path);

        assertAll(
                () -> assertEquals(64.0, config.radius()),
                () -> assertEquals(original, Files.readString(path, StandardCharsets.UTF_8)));
    }

    @Test
    void unsupportedOrMissingRequiredPropertiesFailWithoutReplacingTheFile() throws IOException {
        for (String content : new String[]{"version=2\nradius=14\n", "radius=14\n", "version=1\n"}) {
            assertRejectedWithoutRewriting(content);
        }
    }

    @Test
    void malformedRadiusFailsWithoutReplacingTheFile() throws IOException {
        assertRejectedWithoutRewriting("# Fix this value rather than discarding this file.\n"
                + "version=1\nradius=fourteen\n");
    }

    private void assertRejectedWithoutRewriting(String content) throws IOException {
        Path path = directory.resolve("balance-wards.properties");
        Files.writeString(path, content, StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> WardConfig.load(path));
        assertEquals(content, Files.readString(path, StandardCharsets.UTF_8));
    }
}
