package com.empresa.mantenimiento.application.dto;

import com.empresa.mantenimiento.domain.model.AlertSeverity;

import java.io.Serializable;
import java.time.Instant;

public record SensorPlantStatusDto(
        String sensorId,
        AlertSeverity severity,
        double lastVibration,
        String statusDescription,
        Instant lastUpdated
) implements Serializable {
}
