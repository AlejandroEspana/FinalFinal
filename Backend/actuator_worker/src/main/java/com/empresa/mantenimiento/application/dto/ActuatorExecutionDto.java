package com.empresa.mantenimiento.application.dto;

import java.time.Instant;

public record ActuatorExecutionDto(
        String commandId,
        String sensorId,
        String previousState,
        String currentState,
        String operatorId,
        String reason,
        Instant executedAt
) {
}
