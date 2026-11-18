package com.empresa.mantenimiento.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AlertId(@JsonValue String value) implements Serializable {

    public AlertId {
        Objects.requireNonNull(value, "El identificador de alerta no puede ser nulo.");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("El identificador de alerta no puede estar vacío.");
        }
        value = trimmed;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AlertId fromJson(Object raw) {
        if (raw == null) {
            throw new NullPointerException("El identificador de alerta no puede ser nulo.");
        }
        if (raw instanceof String str) {
            return new AlertId(str);
        }
        if (raw instanceof Map<?, ?> map && map.get("value") instanceof String str) {
            return new AlertId(str);
        }
        throw new IllegalArgumentException("Formato inválido para AlertId: " + raw);
    }

    public static AlertId generate() {
        return new AlertId("ALT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    public static AlertId of(String value) {
        return new AlertId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
