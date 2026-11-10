package com.empresa.mantenimiento.application.dto;

import java.io.Serializable;
import java.time.Instant;

public record TelemetryEventDto(
        String sensorId,
        double vibrationValue,
        String machineSector,
        long totalReadingsEmitted,
        Instant timestamp
) implements Serializable {
}
