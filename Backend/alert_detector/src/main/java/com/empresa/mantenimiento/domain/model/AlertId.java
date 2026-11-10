package com.empresa.mantenimiento.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record AlertId(String value) implements Serializable {

    public AlertId {
        Objects.requireNonNull(value, "El identificador de alerta no puede ser nulo.");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("El identificador de alerta no puede estar vacío.");
        }
        value = trimmed;
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
