package com.empresa.mantenimiento.infrastructure.persistence.entity;

import java.time.Instant;

public record OperatorSessionEntity(
        String alertId,
        String sensorId,
        boolean resolved,
        Instant createdAt
) {
}
