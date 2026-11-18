package com.empresa.mantenimiento.presentation.response;

import java.time.Instant;

public record DecisionDispatchedResponse(
        String status,
        String message,
        String alertId,
        String sensorId,
        String chosenAction,
        Long auditRecordId,
        String routingTarget,
        Instant processedAt
) {
}
