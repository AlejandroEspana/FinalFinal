package com.empresa.mantenimiento.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object inmutable que representa el identificador único de un sensor industrial.
 * Aplica validaciones Fail-Fast en su constructor compacto.
 */
public record SensorId(String value) implements Serializable {

    public SensorId {
        Objects.requireNonNull(value, "El identificador del sensor no puede ser nulo.");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("El identificador del sensor no puede estar vacío.");
        }
        value = trimmed;
    }

    public static SensorId of(String value) {
        return new SensorId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
