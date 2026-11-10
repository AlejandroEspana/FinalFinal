package com.empresa.mantenimiento.presentation.response;

import java.time.Instant;

public record SensorStatusResponse(
        String sensorId,
        String severity,
        double lastVibration,
        String statusDescription,
        Instant lastUpdated
) {
}
