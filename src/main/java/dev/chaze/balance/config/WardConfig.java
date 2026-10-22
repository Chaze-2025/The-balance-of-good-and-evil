package dev.chaze.balance.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Restart-only server configuration; never rewrites an existing configuration. */
public record WardConfig(double radius) {
    public static final double DEFAULT_RADIUS = 14.0;
    public static final double MAX_RADIUS = 64.0;

    public WardConfig {
        if (!Double.isFinite(radius) || radius <= 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Ward radius must be finite and greater than 0, at most 64 blocks");
        }
    }

    public static WardConfig load(Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, "# Server ward settings; restart the server after changes.\n"
                    + "# Inclusive sphere from torch block center to entity feet; range (0, 64].\n"
                    + "version=1\nradius=14.0\n", StandardCharsets.UTF_8);
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (!"1".equals(properties.getProperty("version"))) {
            throw new IllegalArgumentException("Unsupported or missing ward configuration version; expected version=1");
        }
        String radius = properties.getProperty("radius");
        if (radius == null) {
            throw new IllegalArgumentException("Missing ward configuration radius");
        }
        return new WardConfig(Double.parseDouble(radius));
    }
}
