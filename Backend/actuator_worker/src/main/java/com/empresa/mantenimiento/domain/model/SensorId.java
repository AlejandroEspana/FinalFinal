package com.empresa.mantenimiento.domain.model;

import com.empresa.mantenimiento.domain.exception.InvalidActuatorCommandException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public record SensorId(@JsonValue String value) implements Serializable {

    public SensorId {
        Objects.requireNonNull(value, "El identificador del sensor no puede ser nulo.");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidActuatorCommandException("El identificador del sensor no puede estar vacío.");
        }
        value = trimmed;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SensorId fromJson(Object raw) {
        if (raw == null) {
            throw new NullPointerException("El identificador del sensor no puede ser nulo.");
        }
        if (raw instanceof String str) {
            return new SensorId(str);
        }
        if (raw instanceof Map<?, ?> map && map.get("value") instanceof String str) {
            return new SensorId(str);
        }
        throw new InvalidActuatorCommandException("Formato inválido para SensorId: " + raw);
    }

    public static SensorId of(String value) {
        return new SensorId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
