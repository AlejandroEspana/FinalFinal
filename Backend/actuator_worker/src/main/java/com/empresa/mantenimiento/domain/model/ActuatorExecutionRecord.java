package com.empresa.mantenimiento.domain.model;

import java.time.Instant;

public record ActuatorExecutionRecord(
        String commandId,
        String sensorId,
        String previousState,
        String currentState,
        String operatorId,
        String reason,
        Instant executedAt
) {
}
