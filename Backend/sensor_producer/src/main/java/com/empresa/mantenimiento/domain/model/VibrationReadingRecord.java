package com.empresa.mantenimiento.domain.model;

import com.empresa.mantenimiento.domain.exception.InvalidTelemetryException;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public record VibrationReadingRecord(
        SensorId sensorId,
        double vibrationValue,
        Instant timestamp
) implements Serializable {

    public static final double MIN_VIBRATION = 0.0;
    public static final double MAX_VIBRATION = 500.0;

    public VibrationReadingRecord {
        Objects.requireNonNull(sensorId, "El sensorId no puede ser nulo.");
        Objects.requireNonNull(timestamp, "La marca temporal (timestamp) no puede ser nula.");

        if (Double.isNaN(vibrationValue) || Double.isInfinite(vibrationValue)) {
            throw new InvalidTelemetryException("El valor de vibración debe ser un número finito válido.");
        }
        if (vibrationValue < MIN_VIBRATION || vibrationValue > MAX_VIBRATION) {
            throw new InvalidTelemetryException(
                    String.format("Lectura de vibración [%.2f] fuera de los rangos físicos permitidos [%.1f - %.1f].",
                            vibrationValue, MIN_VIBRATION, MAX_VIBRATION));
        }
    }

    public static VibrationReadingRecord of(String sensorIdStr, double value) {
        return new VibrationReadingRecord(SensorId.of(sensorIdStr), value, Instant.now());
    }
}
