package com.empresa.mantenimiento.infrastructure.persistence.entity;

import java.time.Instant;

public record SimulationLogEntity(
        String sensorId,
        double vibrationValue,
        Instant emittedAt
) {
}
