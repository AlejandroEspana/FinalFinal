package com.empresa.mantenimiento.presentation.response;

import java.time.Instant;

public record WindowStatusResponse(
        String sensorId,
        String severity,
        int readingsInWindow,
        double averageVibration,
        Instant lastEvaluatedAt
) {
}
