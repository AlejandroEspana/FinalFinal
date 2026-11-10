package com.empresa.mantenimiento.application.dto;

import java.time.Instant;

public record AuditRecordDto(
        Long id,
        String alertId,
        String sensorId,
        String chosenAction,
        String operatorId,
        String status,
        String routingTarget,
        Long delayMillis,
        String notes,
        Instant recordedAt
) {
}
