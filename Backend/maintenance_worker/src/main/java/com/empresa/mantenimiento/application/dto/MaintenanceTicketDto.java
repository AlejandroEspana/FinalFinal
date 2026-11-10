package com.empresa.mantenimiento.application.dto;

import java.time.Instant;

public record MaintenanceTicketDto(
        String workOrderId,
        String sensorId,
        String priority,
        boolean delayed,
        long delayMillis,
        String operatorId,
        String ticketStatus,
        String executionSummary,
        Instant scheduledAt,
        Instant completedAt
) {
}
