package com.empresa.mantenimiento.domain.model;

import java.io.Serializable;
import java.util.Objects;

public record AlertId(String value) implements Serializable {

    public AlertId {
        Objects.requireNonNull(value, "El identificador de alerta no puede ser nulo.");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("El identificador de alerta no puede estar vacío.");
        }
        value = trimmed;
    }

    public static AlertId of(String value) {
        return new AlertId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
