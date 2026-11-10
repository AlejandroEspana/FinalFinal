package com.empresa.mantenimiento.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record HumanActionRequest(
        AlertId alertId,
        SensorId sensorId,
        AlertSeverity severity,
        double triggerValue,
        String description,
        List<String> availableOptions,
        Instant timestamp
) implements Serializable {

    public HumanActionRequest {
        Objects.requireNonNull(alertId, "El alertId no puede ser nulo.");
        Objects.requireNonNull(sensorId, "El sensorId no puede ser nulo.");
        Objects.requireNonNull(severity, "La severidad no puede ser nula.");
        Objects.requireNonNull(availableOptions, "Las opciones disponibles no pueden ser nulas.");
        Objects.requireNonNull(timestamp, "La marca temporal no puede ser nula.");
        if (availableOptions.isEmpty()) {
            throw new IllegalArgumentException("Las opciones disponibles no pueden estar vacías.");
        }
        availableOptions = Collections.unmodifiableList(List.copyOf(availableOptions));
    }
}
