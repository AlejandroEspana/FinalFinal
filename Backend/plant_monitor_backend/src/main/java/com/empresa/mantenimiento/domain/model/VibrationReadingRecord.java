package com.empresa.mantenimiento.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public record VibrationReadingRecord(
        SensorId sensorId,
        double vibrationValue,
        Instant timestamp
) implements Serializable {

    public VibrationReadingRecord {
        Objects.requireNonNull(sensorId, "El sensorId no puede ser nulo.");
        Objects.requireNonNull(timestamp, "El timestamp no puede ser nulo.");
        if (vibrationValue < 0.0 || vibrationValue > 500.0) {
            throw new IllegalArgumentException("Vibración fuera de rango físico.");
        }
    }
}
