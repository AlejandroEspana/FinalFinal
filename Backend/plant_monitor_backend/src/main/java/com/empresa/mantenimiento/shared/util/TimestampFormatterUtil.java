package com.empresa.mantenimiento.shared.util;

import java.time.Instant;

public final class TimestampFormatterUtil {

    private TimestampFormatterUtil() {}

    public static String toIsoString(Instant instant) {
        return instant == null ? Instant.now().toString() : instant.toString();
    }
}
