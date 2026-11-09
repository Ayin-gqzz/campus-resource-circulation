package edu.campus.circulation.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class UtcTimestamps {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private UtcTimestamps() {
    }

    public static String format(Instant instant) {
        return FORMAT.format(instant);
    }

    public static Instant parse(String text) {
        return Instant.parse(text);
    }
}
