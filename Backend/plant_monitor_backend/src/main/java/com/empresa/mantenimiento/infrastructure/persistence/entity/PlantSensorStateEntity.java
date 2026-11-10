package com.empresa.mantenimiento.infrastructure.persistence.entity;

import java.time.Instant;

public record PlantSensorStateEntity(
        String sensorId,
        String severity,
        double lastVibration,
        String description,
        Instant updatedAt
) {
}
