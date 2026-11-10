package com.empresa.mantenimiento.infrastructure.persistence.entity;

import java.time.Instant;

public record MaintenanceTicketEntity(
        String workOrderId,
        String sensorId,
        boolean delayed,
        long delayMillis,
        String status,
        Instant completedAt
) {
}
