package com.empresa.mantenimiento.presentation.response;

import java.time.Instant;

public record ActuatorStatusResponse(
        String commandId,
        String sensorId,
        String previousState,
        String currentState,
        String operatorId,
        String reason,
        Instant executedAt
) {
}
