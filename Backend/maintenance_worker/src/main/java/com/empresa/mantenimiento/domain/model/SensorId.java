package com.empresa.mantenimiento.domain.model;

import com.empresa.mantenimiento.domain.exception.InvalidWorkOrderException;

public record SensorId(String value) {
    public SensorId {
        if (value == null || value.isBlank()) {
            throw new InvalidWorkOrderException("El sensorId en la orden de mantenimiento no puede ser vacío.");
        }
    }
}
