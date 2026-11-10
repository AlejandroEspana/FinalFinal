package com.empresa.mantenimiento.infrastructure.persistence.entity;

import java.time.Instant;

public record WindowSnapshotEntity(
        String sensorId,
        String currentState,
        int readingCount,
        double averageVibration,
        Instant evaluatedAt
) {
}
