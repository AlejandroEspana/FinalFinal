package com.empresa.mantenimiento.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public record ShutdownCommand(
        String commandId,
        SensorId sensorId,
        String reason,
        String initiatedBy,
        Instant issuedAt
) implements Serializable {

    public ShutdownCommand {
        Objects.requireNonNull(commandId, "El commandId no puede ser nulo.");
        Objects.requireNonNull(sensorId, "El sensorId no puede ser nulo.");
        Objects.requireNonNull(reason, "La razón no puede ser nula.");
        Objects.requireNonNull(initiatedBy, "El iniciador no puede ser nulo.");
        Objects.requireNonNull(issuedAt, "El issuedAt no puede ser nulo.");
    }
}
