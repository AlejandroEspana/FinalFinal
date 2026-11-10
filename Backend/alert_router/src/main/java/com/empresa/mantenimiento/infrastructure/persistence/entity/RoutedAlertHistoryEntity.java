package com.empresa.mantenimiento.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.List;

public record RoutedAlertHistoryEntity(
        String alertId,
        String sensorId,
        String severity,
        List<String> options,
        Instant routedAt
) {
}
