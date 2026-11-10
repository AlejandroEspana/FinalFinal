package com.empresa.mantenimiento.infrastructure.persistence.entity;

import java.time.Instant;

public record ActuatorExecutionLogEntity(
        String commandId,
        String sensorId,
        String previousState,
        String currentState,
        String operatorId,
        String reason,
        Instant executedAt
) {
}
