package com.empresa.mantenimiento.shared.util;

import java.time.Instant;

public final class WorkOrderClockUtil {
    private WorkOrderClockUtil() {}
    public static Instant nowUtc() {
        return Instant.now();
    }
}
