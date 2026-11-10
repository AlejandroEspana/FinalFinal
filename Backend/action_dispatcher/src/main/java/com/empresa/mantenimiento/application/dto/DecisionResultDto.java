package com.empresa.mantenimiento.application.dto;

import java.time.Instant;

public record DecisionResultDto(
        String status,
        String message,
        String alertId,
        String sensorId,
        String actionDispatched,
        Long auditId,
        String routingTarget,
        Instant processedAt
) {
}
