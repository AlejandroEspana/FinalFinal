package com.empresa.mantenimiento.presentation.response;

import java.time.Instant;

/**
 * DTO de Salida que confirma la persistencia en PostgreSQL y el enrutamiento en RabbitMQ.
 */
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
