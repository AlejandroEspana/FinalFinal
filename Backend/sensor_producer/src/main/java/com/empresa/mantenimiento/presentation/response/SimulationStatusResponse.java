package com.empresa.mantenimiento.presentation.response;

import java.time.Instant;

public record SimulationStatusResponse(
        String sensorId,
        double vibrationValue,
        String machineSector,
        long totalReadingsEmitted,
        Instant emittedAt
) {
}
