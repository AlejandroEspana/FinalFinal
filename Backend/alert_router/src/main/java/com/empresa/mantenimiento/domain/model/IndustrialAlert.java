package com.empresa.mantenimiento.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public record IndustrialAlert(
        AlertId alertId,
        SensorId sensorId,
        AlertSeverity severity,
        double triggerValue,
        String description,
        Instant timestamp
) implements Serializable {

    public IndustrialAlert {
        Objects.requireNonNull(alertId, "El alertId no puede ser nulo.");
        Objects.requireNonNull(sensorId, "El sensorId no puede ser nulo.");
        Objects.requireNonNull(severity, "La severidad no puede ser nula.");
        Objects.requireNonNull(timestamp, "La marca temporal no puede ser nula.");
    }
}
