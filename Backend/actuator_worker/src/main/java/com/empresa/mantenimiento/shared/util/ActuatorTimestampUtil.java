package com.empresa.mantenimiento.shared.util;

import java.time.Instant;

public final class ActuatorTimestampUtil {
    private ActuatorTimestampUtil() {}
    public static Instant nowUtc() {
        return Instant.now();
    }
}
