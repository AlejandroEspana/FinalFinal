package com.empresa.mantenimiento.domain.model;

import com.empresa.mantenimiento.domain.exception.InvalidActuatorCommandException;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object inmutable (Java Record con validación Fail-Fast) para comando de apagado crítico.
 */
public record ShutdownCommand(
        String commandId,
        SensorId sensorId,
        String reason,
        String operatorId,
        Instant issuedAt
) {
    public ShutdownCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new InvalidActuatorCommandException("El commandId no puede ser vacío.");
        }
        Objects.requireNonNull(sensorId, "El sensorId no puede ser nulo.");
        if (reason == null || reason.isBlank()) {
            throw new InvalidActuatorCommandException("La razón de apagado es obligatoria.");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new InvalidActuatorCommandException("El operatorId es obligatorio.");
        }
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
    }
}
