package com.empresa.mantenimiento.presentation.response;

import java.time.Instant;

public record MaintenanceTicketResponse(
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
